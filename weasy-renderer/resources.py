"""Bounded raster-only resource policy shared by every PDF worker."""
import base64

MAX_PDF_BYTES = 10 * 1024 * 1024


def inline_only(url, *args, **kwargs):
    # No network, redirects, filesystem URLs, SVG, or embedded PDF attachments.
    if not isinstance(url, str) or len(url) > 350000 or not url.startswith(("data:image/png;base64,", "data:image/jpeg;base64,", "data:image/webp;base64,")):
        raise ValueError("Only embedded raster images are allowed")
    prefix, encoded = url.split(",", 1)
    raw = base64.b64decode(encoded, validate=True)
    if len(raw) > 262500:
        raise ValueError("Image exceeds the renderer limit")
    return {"string": raw, "mime_type": prefix[5:].split(";")[0]}


