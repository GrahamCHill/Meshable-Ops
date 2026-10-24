"""Validated raster images and signatures, with optional host-managed object storage keys."""
import base64
from datetime import datetime, timedelta, timezone
from html import escape
from io import BytesIO
import re
from typing import Literal

from PIL import Image
from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

SIGNATURE_MARKER = re.compile(r"\{\{signature\}\}|^[ \t]*+(?:(?:signature|signed(?: by)?|sign here)[ \t]*+:?[ \t]*+)?_{5,}+[ \t]*+$", re.I | re.M)


def validate_raster(value: str) -> str:
    match = re.fullmatch(r"data:image/(png|jpeg);base64,([A-Za-z0-9+/]+={0,2})", value)
    if not match:
        raise ValueError("Use an embedded PNG or JPEG image.")
    try:
        raw = base64.b64decode(match[2], validate=True)
        with Image.open(BytesIO(raw)) as image:
            if image.format != ("PNG" if match[1] == "png" else "JPEG") or image.width * image.height > 25_000_000:
                raise ValueError("Unsupported image format or resolution.")
            image.verify()
    except Exception as error:
        raise ValueError("The embedded image is not a valid raster image.") from error
    return value


class MediaModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class DocumentImage(MediaModel):
    id: str = Field(max_length=100)
    name: str = Field(max_length=160)
    dataUrl: str | None = Field(default=None, max_length=350000)
    assetKey: str | None = Field(default=None, min_length=1, max_length=240)
    section: Literal["header", "title", "details", "text", "body", "closing", "footer"]
    sectionId: str | None = Field(default=None, min_length=1, max_length=100, pattern=r"^[a-zA-Z0-9_-]+$")
    widthMm: float = Field(ge=6, le=180)
    heightMm: float = Field(ge=2, le=100)
    xMm: float = Field(default=0, ge=0, le=280)

    @field_validator("dataUrl")
    @classmethod
    def raster(cls, value):
        return validate_raster(value) if value is not None else None

    @model_validator(mode="after")
    def source(self):
        from backend.asset_storage import valid_key
        if (self.dataUrl is None) == (self.assetKey is None) or self.assetKey is not None and not valid_key(self.assetKey):
            raise ValueError("Supply exactly one valid image dataUrl or assetKey.")
        return self


class SignatureSettings(MediaModel):
    id: str | None = Field(default=None, min_length=1, max_length=100, pattern=r"^[a-zA-Z][a-zA-Z0-9_.-]*$")
    label: str = Field(default="Signature", min_length=1, max_length=100)
    signerName: str = Field(default="", max_length=160)
    required: bool = False
    widthMm: float = Field(default=70, ge=40, le=120)
    align: Literal["left", "center", "right"] = "left"


class DocumentSignature(SignatureSettings):
    dataUrl: str | None = Field(default=None, max_length=350000)
    signedAt: datetime | None = None

    @field_validator("dataUrl")
    @classmethod
    def raster(cls, value):
        return validate_raster(value) if value else value


def normalize_signature_ids(signatures, fallback=None):
    """Migrate old position-only fields once; keep IDs intact on later updates."""
    if not isinstance(signatures, list) or any(not isinstance(field, dict) for field in signatures):
        return signatures
    fields = [{**field, **({"id": fallback[i].get("id")} if field.get("id") is None and isinstance(fallback, list) and i < len(fallback) and isinstance(fallback[i], dict) and fallback[i].get("id") else {})} for i, field in enumerate(signatures)]
    named = [field["id"] for field in fields if isinstance(field.get("id"), str)]
    if len(set(named)) != len(named):
        raise ValueError("Signature field IDs must be unique.")
    reserved = set(named)
    for index, field in enumerate(fields):
        if field.get("id") is not None:
            continue
        sequence = index + 1
        while f"signature-{sequence}" in reserved:
            sequence += 1
        field["id"] = f"signature-{sequence}"
        reserved.add(field["id"])
    return fields


def media_margins(layout, images, organization="", reference="", sections=None, footer=None):
    top = sum(i.heightMm + 3 for i in images if i.section == "header")
    bottom = sum(i.heightMm + 3 for i in images if i.section == "footer")
    kinds = {s.kind for s in sections} if sections is not None else {"header", "footer"}
    if "header" in kinds:
        top += (len(re.split(r"\r\n|\r|\n", organization)) - 1) * 12 * 1.4 * 25.4 / 96
    if "footer" in kinds:
        bottom += (len(re.split(r"\r\n|\r|\n", footer if footer is not None else f"{organization} · {reference}")) - 1) * 10 * 1.4 * 25.4 / 96
    return max(layout.marginTop, 26 + top if top else 0), max(layout.marginBottom, 22 + bottom if bottom else 0)


def validate_media(layout, images, sections=None, organization="", reference="", footer=None):
    if len({i.id for i in images}) != len(images) or sum(len(i.dataUrl or "") for i in images) > 1_500_000:
        raise ValueError("Use unique image IDs and at most 1.5 MB of embedded image data.")
    width, height = (210, 297) if layout.size == "A4" else (215.9, 279.4)
    if layout.orientation == "landscape":
        width, height = height, width
    if any(i.xMm + i.widthMm > width - layout.marginLeft - layout.marginRight + .1 for i in images):
        raise ValueError("An image extends beyond the page margins.")
    if sections and any(i.section not in [s.kind for s in sections] for i in images):
        raise ValueError("An image must belong to an existing section.")
    if any(i.sectionId and (i.section not in ("body", "text") or not sections or not any(s.kind == i.section and (s.id or s.kind) == i.sectionId for s in sections)) for i in images):
        raise ValueError("A paragraph image must belong to an existing paragraph section ID.")
    top, bottom = media_margins(layout, images, organization, reference, sections, footer)
    if height - top - bottom < 100:
        raise ValueError("Header/footer content leaves too little writing space. Reduce the text or image height.")


def render_images(images, section, section_id=None, primary_id="body"):
    return "".join(f'<div class="document-image-row"><img alt="{escape(i.name, quote=True)}" src="{i.dataUrl}" style="width:{i.widthMm:g}mm;height:{i.heightMm:g}mm;margin-left:{i.xMm:g}mm"></div>' for i in images if i.section == section and (section not in ("body", "text") or section_id is None or (i.sectionId or primary_id) == section_id))


def render_signature(signature):
    if signature is None:
        signature = DocumentSignature()
    margin = "margin-left:auto;margin-right:auto" if signature.align == "center" else "margin-left:auto" if signature.align == "right" else "margin-left:0"
    image = f'<img class="signature-ink" src="{signature.dataUrl}" alt="Signature">' if signature.dataUrl else ""
    date = signature.signedAt.astimezone(timezone(timedelta(hours=2))).strftime("%d %b %Y").lstrip("0") if signature.signedAt else ""
    name = escape(signature.signerName or signature.label) + (" *" if signature.required else "")
    identity = f' data-signature-id="{escape(signature.id, quote=True)}"' if signature.id else ""
    return f'<div class="signature-field"{identity} style="width:{signature.widthMm:g}mm;{margin}"><div class="signature-ink-area">{image}</div><div class="signature-line"></div><div class="signature-caption"><strong>{name}</strong><span>{date}</span></div></div>'


def render_signatures(signatures):
    rows = []
    for row in range(0, len(signatures), 2):
        cells = []
        for column, signature in enumerate(signatures[row:row + 2]):
            if len(signatures) > 1:
                signature = signature.model_copy(update={"align": "right" if column else "left"})
            cells.append(f'<div class="signature-cell">{render_signature(signature)}</div>')
        pair = " signature-pair" if len(signatures) > 1 else ""
        rows.append(f'<div class="signature-row{pair}">{"".join(cells)}</div>')
    return f'<div class="signature-group">{"".join(rows)}</div>' if rows else ""
