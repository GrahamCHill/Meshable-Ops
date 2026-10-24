# Storage connections and shared folders

Connect S3-compatible buckets or Azure Blob containers in **Administration > Storage connections**. For MinIO/Azurite credentials and startup commands, see [development setup](getting-started.md).

## Shared cloud locations

The existing `ADMIN` role acts as super admin for storage configuration. From
**Administration > Storage connections > Shared locations**, admins can:

1. Add independent S3 buckets or Azure Blob containers, each with an in-app nickname.
2. Set an optional start-folder boundary and test the connection.
3. Browse the provider's folders and select the folders to expose, assigning each a nickname.
4. Save a folder's **Accessible** setting or disable a whole connection to revoke shared access.

Storage displays each enabled connection as a named folder containing its accessible selections.
Selections include all descendants. Parent/child selections within a connection cannot overlap.
Saving a new connection does not share any folders automatically. Regular authenticated users
see only shared locations; credentials and admin folder discovery remain restricted to `ADMIN`.
Hidden selections are blocked for browsing, downloads, uploads, and vector search. Uploads still
require the separate upload permission. Removing a selection removes its application metadata,
not the objects in cloud storage. Existing application-managed folders retain their visibility
and can be renamed or hidden in the same admin workspace.

Connection nicknames, selected-folder nicknames, and visibility are stored in PostgreSQL.
Nicknames do not rename buckets, containers, or cloud object keys. The backend schema update
adds connection references and defaults existing folders to accessible; prefix uniqueness is
scoped per connection so matching folder paths can be shared from different buckets.

## Default storage view path

The default connection exposes registered, application-managed storage roots beneath a
configured prefix such as `contract-management/documents`. Named connections expose only
the folders explicitly selected by an admin, within their optional start-folder boundary.
Configure this persistently from **Administration > Storage connections > Default connection > Storage view path**.
The value is stored in PostgreSQL. Do not use `/`, `.` or `..` as path segments.

For either local provider, set the prefix before startup:

```bash
APP_STORAGE_ROOT_PATH=contract-management/documents docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build -d
```

PowerShell:

```powershell
$env:APP_STORAGE_ROOT_PATH = "contract-management/documents"
docker compose -f docker-compose.yml -f docker-compose.dev.azurite.yml up --build -d
```

For a deployed S3 or Azure Blob service, set these runtime variables in `.env.production`:

```dotenv
APP_SEED_STORAGE_PROVIDER=S3
APP_SEED_STORAGE_BUCKET=my-bucket
APP_SEED_STORAGE_REGION=us-east-1
APP_SEED_STORAGE_ENDPOINT=https://s3.example.com
APP_SEED_STORAGE_ACCESS_KEY=...
APP_SEED_STORAGE_SECRET_KEY=...
APP_SEED_STORAGE_ROOT_PATH=contract-management/documents
```

For Azure Blob, use `AZURE_BLOB`, the container as the bucket value, the Blob service
endpoint (for example `https://account.blob.core.windows.net`), and the storage account
name/key as access/secret values. Startup values seed PostgreSQL only when settings are
unconfigured. Set `APP_SEED_STORAGE_FORCE=true` to intentionally replace persisted settings.
Keep credentials as runtime secrets; do not bake them into the backend image.

The development overrides also leave `APP_SEED_STORAGE_FORCE` disabled by default, so an
administrator's saved provider and storage view path survive container restarts. When switching
an existing development database between MinIO and Azurite, run that one startup with
`APP_SEED_STORAGE_FORCE=true`, then remove or unset it again.

Changing the storage view path repoints application roots into the new namespace on the
next listing/reindex. It does not copy existing objects from the old prefix.

The connection screen shows fields for the selected provider and supports either explicit
keys or the identity configured on the application server. Use **Test connection** before
saving to check read access against the draft bucket/container and prefix. This check does
not save settings, create a container, or upload objects; write permissions must be granted
separately for uploads. Automatic vector recalculation is configured on the **Search index** tab.

## Browsing, indexing, and uploads

The **Storage** page stores searchable root metadata in PostgreSQL and creates a matching
`{configured-storage-view-path}/{folder-id}/` prefix in MinIO/S3 or Azure Blob/Azurite.
Opening a root explores
its child virtual folders and objects directly from the configured provider. Root metadata and
each discovered document's filename/path/content type are vectorized separately in pgvector.
Content extraction for supported text, PDF, and DOCX files can be enabled from **Administration > AI & search**.
Chat and embedding providers can use environment settings or encrypted admin settings. OCR and image classification remain extension points. See
[Document Folder and Processing Extension](document-processing-extension.md).

Authenticated accounts can download documents and folder ZIPs from accessible selections when the current document-access policy allows it. Uploading
files or a folder tree requires the independent **Allow document and folder uploads** permission.
Admins grant or revoke it from **Administration > Users & access**; it defaults to disabled even for administrators.
Upload paths are constrained to the selected storage root and uploaded
document metadata is synchronized and vectorized immediately.

Default transfer limits are 50 MB per file and 250 MB per request. Override them at backend
startup when needed:

```dotenv
APP_UPLOAD_MAX_FILE_SIZE=100MB
APP_UPLOAD_MAX_REQUEST_SIZE=500MB
```

The frontend nginx request limit is currently `250m`; update that proxy limit as well when
increasing the backend request size. Upload limits are separate from the 5 MiB content-indexing limit.

See [PDF access and download policies](document-access.md) and [indexing schedules](search-and-indexing.md).
