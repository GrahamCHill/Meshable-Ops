import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("mount_data", Path(__file__).resolve().parents[2] / "terraform/mount_data.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ServicesDiskTests(unittest.TestCase):
    def test_only_a_blank_unmounted_whole_disk_can_be_formatted(self):
        self.assertTrue(module.inspect_disk({"type": "disk", "mountpoints": [None]}, {}))
        self.assertFalse(module.inspect_disk({"type": "disk", "mountpoints": [None]}, {"TYPE": "ext4", "UUID": "existing-data"}))

    def test_mounted_system_disks_partitions_and_foreign_filesystems_are_rejected(self):
        for device, signature in [
            ({"type": "disk", "mountpoints": ["/"]}, {}),
            ({"type": "disk", "mountpoints": ["/srv/other-data"]}, {"TYPE": "ext4"}),
            ({"type": "part", "mountpoints": []}, {}),
            ({"type": "disk", "children": [{"type": "part"}]}, {}),
            ({"type": "disk"}, {"TYPE": "xfs"}),
            ({"type": "disk"}, {"PTTYPE": "gpt"}),
            ({"type": "disk"}, {"TYPE": "crypto_LUKS"}),
        ]:
            with self.assertRaises(ValueError):
                module.inspect_disk(device, signature)

    def test_an_arbitrary_system_device_cannot_be_selected(self):
        for reference in ("/dev/sda", "/dev/nvme0n1", "ebs:bad-id", "/dev/disk/azure/scsi1/lun0"):
            with self.assertRaises(ValueError):
                module.resolve_device(reference)
