import type { StorageProvider } from '../../../api/types'

export function ServerIdentityHelp({ provider }: { provider: StorageProvider }) {
  return <div className="field-help">
    <p>Server identity means the cloud identity or credentials available to the backend server running this app. The app uses that identity to access storage for your users. Your app sign-in does not grant cloud storage access.</p>
    <p>{provider === 'AZURE_BLOB'
      ? 'For Azure, this can be a managed identity or service principal configured on the server. Grant it Storage Blob Data Reader access to browse and download, or Storage Blob Data Contributor access for uploads and folder changes.'
      : provider === 'GCS'
        ? 'For Google Cloud, this uses Application Default Credentials: an attached service account, Workload Identity Federation, or credentials configured on the backend with GOOGLE_APPLICATION_CREDENTIALS. Grant Storage Object Viewer on this bucket to browse and download, or Storage Object User for uploads and folder changes.'
        : 'For AWS, this can be the IAM role attached to the server or container, or AWS credentials configured on the backend. Grant s3:ListBucket for this prefix and s3:GetObject to download; uploads and folder changes also need s3:PutObject and s3:DeleteObject. S3-compatible services such as MinIO need their credentials configured on the backend.'}</p>
    <p>Configure this identity in your hosting environment before testing. For local development or Docker, credentials must be available inside the backend process or container. Saving with this option clears any credentials saved for this connection.</p>
  </div>
}
