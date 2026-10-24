# Google Drive

Google Drive is a separate optional plugin with a workspace folder browser. It starts disabled and administrator-only. Configure it in **Administration → Storage & sharing → Google Drive**, or **Plugins → Google Drive → Configuration**.

Supply a Google OAuth client ID, client secret, offline refresh token, and root folder ID. `root` selects the connected account's My Drive. Obtain consent outside Meshable Ops using the narrow `https://www.googleapis.com/auth/drive.metadata.readonly` scope and the intended account. The application refreshes access tokens server-side; it does not provide a Google sign-in/consent wizard. Secrets use the existing encrypted plugin configuration and are never returned to the browser. Save settings before enabling the plugin.

After activation, **Google Drive** appears in navigation. Members explicitly allowed by the plugin and access controls share the connected account's metadata within the configured root. Prefer a dedicated account with access only to intended resources. The folder restriction narrows browsing; it does not change Google's permissions or confer access when a link opens in a member's own Google account.

The browser lists names, types, sizes, modification times, folders, and safe Google web links, with breadcrumbs and previous/next pagination. Each page contains at most 100 items. Before listing a requested folder, the backend follows actual parent IDs to verify it belongs under the configured root, rejects shortcuts/cycles, and limits ancestry to 30 levels. Shared-drive flags are included, subject to the account's permissions.

This version reads metadata only. It does not upload, download, synchronize, mount Drive in S3/Blob storage, or index file contents. Google Docs links open through Google. Root/credential changes or disabling the plugin invalidate in-flight results. No provider token or download URL is returned.

`GET /api/plugins/google-drive/files?folder=<id>&pageToken=<token>` returns `{items,nextPageToken,folderId,rootId}`. Omit both parameters for the first root page. Calls are limited to 20/minute/member and use fixed Google HTTPS endpoints with bounded responses, timeouts, no redirects, and credential-free error messages.

See Google's [files.list reference](https://developers.google.com/workspace/drive/api/reference/rest/v3/files/list) and [OAuth web-server flow](https://developers.google.com/identity/protocols/oauth2/web-server#offline).
