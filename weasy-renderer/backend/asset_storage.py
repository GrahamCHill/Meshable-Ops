"""Private image object storage. Clients supply keys, never arbitrary fetch URLs."""
import base64
from functools import lru_cache
from io import BytesIO
import os
from pathlib import Path
import re
from uuid import uuid4

from PIL import Image
from pydantic import Field, field_validator
from backend.media import MediaModel, validate_raster

MAX_BYTES = 262480


class AssetError(Exception):
    def __init__(self, message, status=422):
        super().__init__(message); self.status = status


def valid_key(key):
    return isinstance(key, str) and len(key) <= 240 and re.fullmatch(r"[a-zA-Z0-9][a-zA-Z0-9_./-]*", key) and all(part not in ("", ".", "..") for part in key.split("/"))


def checked_key(key):
    if not valid_key(key): raise AssetError("Invalid image asset key.")
    return key


def raster_bytes(raw):
    if not raw or len(raw) > MAX_BYTES: raise AssetError("Stored images must be PNG/JPEG raster images up to 262,480 bytes.")
    try:
        with Image.open(BytesIO(raw)) as image:
            if image.format not in ("PNG", "JPEG"): raise ValueError()
            mime, width, height = "image/png" if image.format == "PNG" else "image/jpeg", image.width, image.height
        data_url = f"data:{mime};base64," + base64.b64encode(raw).decode("ascii")
        validate_raster(data_url)
    except Exception as error:
        raise AssetError("The stored object is not a valid PNG or JPEG raster image.") from error
    return raw, mime, data_url, width, height


class AssetUpload(MediaModel):
    name: str = Field(min_length=1, max_length=160)
    dataUrl: str = Field(max_length=350000)

    @field_validator("dataUrl")
    @classmethod
    def raster(cls, value): return validate_raster(value)


class FileAssetStore:
    provider = "filesystem"
    def __init__(self, directory): self.directory = Path(directory).resolve()
    def path(self, key):
        path = (self.directory / checked_key(key)).resolve()
        if not path.is_relative_to(self.directory): raise AssetError("Invalid image asset path.")
        return path
    def put(self, key, raw, mime):
        path = self.path(key); path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("xb") as output: output.write(raw)
    def get(self, key):
        try:
            with self.path(key).open("rb") as source: return source.read(MAX_BYTES + 1)
        except FileNotFoundError as error: raise AssetError("Image asset not found.", 404) from error


class S3AssetStore:
    provider = "s3"
    def __init__(self, bucket, prefix="weasycanvas", client=None):
        self.bucket, self.prefix = bucket, prefix.strip("/")
        if self.prefix: checked_key(self.prefix)
        if client is None:
            import boto3
            from botocore.config import Config
            client = boto3.client("s3", endpoint_url=os.getenv("WEASYCANVAS_S3_ENDPOINT_URL") or None,
                                  region_name=os.getenv("AWS_REGION") or os.getenv("AWS_DEFAULT_REGION") or None,
                                  config=Config(connect_timeout=5, read_timeout=15, retries={"max_attempts": 2}))
        self.client = client
    def key(self, key): return f"{self.prefix}/{checked_key(key)}" if self.prefix else checked_key(key)
    def put(self, key, raw, mime):
        self.client.put_object(Bucket=self.bucket, Key=self.key(key), Body=raw, ContentType=mime, CacheControl="private, max-age=300")
    def get(self, key):
        result = self.client.get_object(Bucket=self.bucket, Key=self.key(key)); body = result["Body"]
        try:
            if result.get("ContentLength", 0) > MAX_BYTES: raise AssetError("Stored image exceeds the size limit.")
            return body.read(MAX_BYTES + 1)
        finally: body.close()


class AzureAssetStore:
    provider = "azure"
    def __init__(self, container, prefix="weasycanvas", client=None, content_settings=None):
        self.prefix = prefix.strip("/")
        if self.prefix: checked_key(self.prefix)
        if client is None:
            from azure.storage.blob import BlobServiceClient, ContentSettings
            connection = os.getenv("AZURE_STORAGE_CONNECTION_STRING")
            if connection:
                service = BlobServiceClient.from_connection_string(connection, connection_timeout=5, read_timeout=15)
            else:
                from azure.identity import DefaultAzureCredential
                account = os.getenv("WEASYCANVAS_AZURE_ACCOUNT_URL")
                if not account: raise AssetError("Configure the Azure storage account URL or connection string.", 503)
                service = BlobServiceClient(account_url=account, credential=DefaultAzureCredential(), connection_timeout=5, read_timeout=15)
            client, content_settings = service.get_container_client(container), ContentSettings
        self.client, self.content_settings = client, content_settings
    def key(self, key): return f"{self.prefix}/{checked_key(key)}" if self.prefix else checked_key(key)
    def put(self, key, raw, mime):
        self.client.get_blob_client(self.key(key)).upload_blob(raw, overwrite=False, content_settings=self.content_settings(content_type=mime), timeout=15)
    def get(self, key):
        blob = self.client.get_blob_client(self.key(key))
        if blob.get_blob_properties().size > MAX_BYTES: raise AssetError("Stored image exceeds the size limit.")
        return blob.download_blob(length=MAX_BYTES + 1, max_concurrency=1, timeout=15).readall()


@lru_cache(maxsize=1)
def get_asset_store():
    provider = os.getenv("WEASYCANVAS_ASSET_STORAGE", "").lower()
    prefix = os.getenv("WEASYCANVAS_ASSET_PREFIX", "weasycanvas")
    try:
        if not provider: return None
        if provider == "filesystem":
            directory = os.getenv("WEASYCANVAS_ASSET_DIRECTORY")
            if not directory: raise AssetError("Configure WEASYCANVAS_ASSET_DIRECTORY for local asset storage.", 503)
            return FileAssetStore(directory)
        if provider == "s3":
            bucket = os.getenv("WEASYCANVAS_S3_BUCKET")
            if not bucket: raise AssetError("Configure WEASYCANVAS_S3_BUCKET for S3 image storage.", 503)
            return S3AssetStore(bucket, prefix)
        if provider in ("azure", "azure-blob"):
            container = os.getenv("WEASYCANVAS_AZURE_CONTAINER")
            if not container: raise AssetError("Configure WEASYCANVAS_AZURE_CONTAINER for Azure image storage.", 503)
            return AzureAssetStore(container, prefix)
        raise AssetError("Unknown image storage provider.", 503)
    except ImportError as error:
        raise AssetError("Install the optional SDK requirements for the configured image storage provider.", 503) from error
    except AssetError:
        raise
    except Exception as error:
        raise AssetError("Could not initialize image storage. Check the server configuration.", 503) from error


def configured_store():
    store = get_asset_store()
    if store is None: raise AssetError("Image storage is not configured on this server.", 503)
    return store


def load_asset(key, store=None):
    checked_key(key)
    try: return raster_bytes((store if store is not None else configured_store()).get(key))
    except AssetError: raise
    except Exception as error:
        code = getattr(error, "response", {}).get("Error", {}).get("Code")
        if code in ("NoSuchKey", "404", "NotFound") or getattr(error, "status_code", None) == 404:
            raise AssetError("Image asset not found.", 404) from error
        raise AssetError("Image storage could not read this object.", 502) from error


def store_asset(image):
    raw = base64.b64decode(image.dataUrl.split(",", 1)[1], validate=True)
    raw, mime, _, width, height = raster_bytes(raw)
    key = f"images/{uuid4().hex}.{'png' if mime == 'image/png' else 'jpg'}"
    try: configured_store().put(key, raw, mime)
    except AssetError: raise
    except Exception as error: raise AssetError("Image storage could not upload this object.", 502) from error
    return {"assetKey": key, "name": image.name, "contentType": mime, "width": width, "height": height}


def resolve_image_assets(images, store=None):
    cache, result = {}, []
    for image in images:
        if image.assetKey:
            if image.assetKey not in cache: cache[image.assetKey] = load_asset(image.assetKey, store)[2]
            image = image.model_copy(update={"assetKey": None, "dataUrl": cache[image.assetKey]})
        result.append(image)
    if sum(len(image.dataUrl or "") for image in result) > 1500000:
        raise AssetError("Use at most 1.5 MB of resolved image data across the document.")
    return result
