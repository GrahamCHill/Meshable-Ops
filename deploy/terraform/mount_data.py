#!/usr/bin/env python3
"""Mount the services data disk; format only an empty, unattached data disk."""
import argparse
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import time

TARGET = Path("/srv/meshable-data")


def inspect_disk(device: dict, signature: dict[str, str]) -> bool:
    """Return whether a fresh filesystem is needed; reject ambiguous disks."""
    if device.get("type") != "disk" or device.get("children"):
        raise ValueError("Expected a whole data disk without partitions")
    if any(device.get("mountpoints") or []):
        raise ValueError("Refusing to modify an already mounted disk")
    filesystem = signature.get("TYPE")
    if filesystem == "ext4":
        return False
    if signature:
        raise ValueError("Disk already has an unsupported filesystem or partition signature; no formatting performed")
    return True


def output(*args: str, allowed=(0,)) -> str:
    result = subprocess.run(args, capture_output=True, text=True)
    if result.returncode not in allowed:
        raise RuntimeError("Data-disk command failed: " + args[0])
    return result.stdout.strip()


def resolve_device(reference: str) -> Path:
    if reference.startswith("ebs:"):
        volume = reference.removeprefix("ebs:")
        if not re.fullmatch(r"vol-[a-f0-9]+", volume):
            raise ValueError("Invalid EBS data volume ID")
        disks = json.loads(output("lsblk", "--json", "--nodeps", "--paths", "--output", "NAME,SERIAL,TYPE"))["blockdevices"]
        matches = [Path(disk["name"]) for disk in disks if disk.get("type") == "disk" and (disk.get("serial") or "").replace("-", "") == volume.replace("-", "")]
        if len(matches) != 1:
            raise FileNotFoundError("Waiting for the specified EBS data volume")
        return matches[0].resolve(strict=True)
    if not (re.fullmatch(r"/dev/disk/azure/scsi1/lun10", reference) or re.fullmatch(r"/dev/disk/by-id/google-[a-z0-9-]+-services-data", reference)):
        raise ValueError("Use the configured persistent data-disk identifier")
    return Path(reference).resolve(strict=True)


def prepare(reference: str) -> None:
    for attempt in range(120):
        try:
            device = resolve_device(reference)
            if not stat.S_ISBLK(device.stat().st_mode):
                raise ValueError("The data device is not a block device")
            break
        except FileNotFoundError:
            if attempt == 119:
                raise RuntimeError("Services data disk did not attach") from None
            time.sleep(5)
    if TARGET.is_mount():
        mounted = output("findmnt", "--noheadings", "--mountpoint", str(TARGET), "--output", "SOURCE")
        if Path(mounted).resolve() != device:
            raise ValueError("Services data directory is mounted from another disk")
    else:
        block = json.loads(output("lsblk", "--json", "--paths", "--output", "PATH,TYPE,MOUNTPOINTS", str(device)))["blockdevices"][0]
        properties = output("blkid", "--probe", "--output", "export", str(device), allowed=(0, 2))
        signature = dict(line.split("=", 1) for line in properties.splitlines() if "=" in line and not line.startswith("DEVNAME="))
        if inspect_disk(block, signature):
            subprocess.run(["mkfs.ext4", "-m", "0", "-L", "meshable-data", str(device)], check=True)
        uuid = output("blkid", "--match-tag", "UUID", "--output", "value", str(device))
        if not re.fullmatch(r"[a-fA-F0-9-]+", uuid):
            raise ValueError("Data disk has no valid filesystem UUID")
        fstab = Path("/etc/fstab")
        content = fstab.read_text()
        entry = f"UUID={uuid} {TARGET} ext4 defaults,nofail 0 2"
        existing = [line for line in content.splitlines() if not line.lstrip().startswith("#") and len(line.split()) > 1 and line.split()[1] == str(TARGET)]
        if existing and any(line.split()[0] != f"UUID={uuid}" for line in existing):
            raise ValueError("An existing fstab entry points at another services disk")
        if not existing:
            with fstab.open("a") as stream:
                stream.write("\n" + entry + "\n")
        TARGET.mkdir(parents=True, exist_ok=True)
        subprocess.run(["mount", str(TARGET)], check=True)
    for name, uid in {"mongodb": 999, "redis": 999, "superset": 1000}.items():
        directory = TARGET / name
        if not directory.exists():
            directory.mkdir(mode=0o750)
            os.chown(directory, uid, uid)


if __name__ == "__main__":
    import fcntl
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device", required=True)
    args = parser.parse_args()
    with open("/run/meshable-data.lock", "w") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        prepare(args.device)
