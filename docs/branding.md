# Workspace branding

Open **Administration > Branding** (`/admin/branding`) to set the business name, colours, logo/favicon, and sign-in background. Changes are workspace-wide and administrator-only. Branding metadata and images are public so sign-in works before authentication.

## Name and colours

Set **Application name** to the business name (1–80 characters). It appears in navigation, above the sign-in form, and in the browser tab title. The default is Meshable Ops.

The colour controls accept six-digit hex values for the sidebar background, sidebar text/links, and content links. Unchecked overrides follow the light/dark theme. If a sidebar background is customized without an explicit text colour, the app chooses black or white for contrast. Overrides apply in both themes, including mobile navigation; check both when choosing colours. Buttons and navigation cards retain their basic layouts.

Members can change their theme with the **Dark mode** sun/moon toggle in the account dropdown or **User settings > Appearance**. The choice is saved in the current browser. The switch supports keyboard input and skips its animation when reduced motion is preferred.

The preview shows unsaved name/colour changes. **Save branding settings** saves identity, colour, and background preferences together. **Reload saved values** discards the current draft; **Use default settings** prepares the defaults for saving. Concurrent edits use a revision check: a stale save returns a conflict rather than overwriting another administrator's settings.

## Logo and favicon

The compact image controls sit together below the appearance settings. Choose an image, inspect the preview, then save that image. **Restore placeholder** removes the custom logo. **Use logo** removes a separate favicon. Without a separate favicon, browser tabs use the logo; without a custom logo, both use the bundled [placeholder](../frontend/public/brand-mark.svg).

| Image | Recommended upload | Limits and processing |
| --- | --- | --- |
| Logo | **256 × 256 px**, square PNG/WebP with transparency | PNG/JPEG/WebP, 1 MiB, at most 2048 × 2048 px; saved as PNG with a longest side of 512 px |
| Favicon | **64 × 64 px**, simple square mark clear at 16 × 16 px | Same input limits; saved on a transparent 64 × 64 px PNG canvas |
| Sign-in background | **1920 × 1080 px**, important content near the centre | PNG/JPEG/WebP, 5 MiB, at most 4096 × 4096 px; saved as JPEG at 85% quality with a longest side of 1920 px |

The logo appears beside the name in navigation and above the name/form on sign-in. Navigation uses a 28 px mark and sign-in uses a 64 px mark; wide wordmarks appear small. Recommendations are not required input dimensions. The server checks decoded raster format and dimensions, ignores filename/content-type claims, and removes original metadata/animation. SVG/GIF uploads are unsupported. Background transparency is flattened onto white before JPEG encoding.

## Sign-in background

Upload up to five **Sign-in images**, shown in numbered order. Each can be replaced or removed independently; an added image uses the first empty numbered slot. Image uploads/removals apply immediately and do not discard unsaved name/colour settings. The selected display mode is saved separately with **Save branding settings**:

- **No image:** plain background; uploaded images remain available.
- **Single image:** select an image or use the first available one.
- **Slideshow:** cycle through all available images, with a configurable interval of 5–60 seconds (default 8).

Background modes need at least one uploaded image. A slideshow with one image stays still. The sign-in panel remains opaque for readability, with the logo at the top. Backgrounds fill the viewport and crop as necessary; do not place essential text at image edges.

The slideshow includes previous/next and pause/play controls. It stops automatic movement for reduced-motion preferences and while the page is hidden; reduced-motion users can still switch images manually. Missing/broken images are skipped. Removing a selected single image falls back to the first remaining image; removing all images gives a plain sign-in background. Failures loading branding do not prevent sign-in. The bookmarked `/login` route serves the app form directly; Superset's prefixed login remains separate.

## Persistence and delivery

`branding_preferences`, `branding_assets`, and `branding_login_images` persist in PostgreSQL independently of object storage. Include them in database backups; the metadata JSON export omits them. Rebuilds/restarts preserve branding with the database. Missing settings use defaults without inserting demo branding rows.

Saving refreshes the current browser immediately. Other open sessions refresh on focus or approximately once a minute. New pages initially use the bundled title/icon until metadata loads. Versioned image URLs support caching and change on replacement; removed/replaced URLs return 404, although existing browser caches may retain old images.

No external image URLs or arbitrary CSS are accepted. All image responses retain crawler-exclusion headers. Use images suitable for public display. Changing the display name does not rename source packages, operational identifiers, existing authenticator issuers, or generated documents.

See the [API reference](api.md#branding) and [crawler exclusion](crawler-exclusion.md).

Category artwork and sidebar ordering are separate workspace settings under [Navigation & section icons](navigation-and-icons.md). Project icons are managed on each project's detail page.
