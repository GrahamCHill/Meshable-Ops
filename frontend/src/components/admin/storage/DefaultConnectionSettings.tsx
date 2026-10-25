import { Button, Card } from '../../ui'
import { getErrorMessage } from '../adminUtils'
import type { StorageSettingsState } from './useStorageSettings'
import { ServerIdentityHelp } from './ServerIdentityHelp'
import { ServiceAccountJsonField } from './ServiceAccountJsonField'

export function DefaultConnectionSettings({ state }: { state: StorageSettingsState }) {
  const { fieldId, form, identity, setIdentity, showSecret, setShowSecret, settings, check, update, azure, gcs, enabled, prefix, validate } = state
  return (<div className="storage-layout">
    <Card>
      <div className="step-heading"><span>1</span><div><h2>Choose your provider</h2><p>Use a cloud service or an S3-compatible store.</p></div></div>
      <div className="provider-options" role="group" aria-label="Storage provider">
        {([{ value: 'S3', label: 'Amazon S3', detail: 'AWS, MinIO & compatible' }, { value: 'AZURE_BLOB', label: 'Azure Blob', detail: 'Azure Storage & Azurite' }, { value: 'GCS', label: 'Google Cloud Storage', detail: 'GCP buckets' }, { value: 'NONE', label: 'No storage', detail: 'Disconnect object storage' }] as const).map(provider => (
          <button type="button" key={provider.value} className={`provider-option${form.storageProvider === provider.value ? ' is-selected' : ''}`} aria-pressed={form.storageProvider === provider.value} onClick={() => {
            if (provider.value === form.storageProvider) return
            update({ storageProvider: provider.value, storageBucket: '', storageEndpoint: '', storageRegion: provider.value === 'S3' ? 'us-east-1' : '', storageAccessKey: '', storageSecretKey: '' })
            setIdentity(true); setShowSecret(false)
          }}><strong>{provider.label}</strong><small>{provider.detail}</small></button>
        ))}
      </div>
      {enabled ? <>
        <div className="step-heading"><span>2</span><div><h2>Connection details</h2><p>{gcs ? 'Enter the bucket name without gs://. No region, custom endpoint, or project ID is required.' : azure ? 'Use the Blob service endpoint for your storage account.' : 'Enter your bucket and AWS region. Custom endpoints are optional.'}</p></div></div>
        <div className="form-grid">
          <div className="form-field"><label htmlFor={`${fieldId}-bucket`}>{azure ? 'Container name' : 'Bucket name'}</label><input id={`${fieldId}-bucket`} maxLength={255} placeholder="contract-documents" value={form.storageBucket} onChange={event => update({ storageBucket: event.target.value })} /></div>
          {form.storageProvider === 'S3' && <div className="form-field"><label htmlFor={`${fieldId}-region`}>AWS region</label><input id={`${fieldId}-region`} maxLength={255} placeholder="us-east-1 (default)" value={form.storageRegion} onChange={event => update({ storageRegion: event.target.value })} /></div>}
          {!gcs && <div className="form-field full-width"><label htmlFor={`${fieldId}-endpoint`}>{azure ? 'Blob service endpoint' : 'Custom endpoint (optional)'}</label><input id={`${fieldId}-endpoint`} maxLength={512} type="url" placeholder={azure ? 'https://account.blob.core.windows.net' : 'https://s3.example.com'} value={form.storageEndpoint} onChange={event => update({ storageEndpoint: event.target.value })} /><small>{azure ? 'For Azurite, include the account path after the configured emulator endpoint.' : 'Leave blank for AWS. For MinIO, use an address reachable by the application server.'}</small></div>}
        </div>
        <div className="step-heading"><span>3</span><div><h2>Authentication</h2><p>Credentials are encrypted on the server and never returned here. Leave fields blank to retain credentials for the same location.</p></div></div>
        <div className="segmented-control" role="group" aria-label="Authentication method">
          <button type="button" aria-pressed={!identity} className={!identity ? 'is-active' : ''} onClick={() => { setIdentity(false); check.reset() }}>{gcs ? 'Service account JSON key' : azure ? 'Account key' : 'Access keys'}</button>
          <button type="button" aria-pressed={identity} className={identity ? 'is-active' : ''} onClick={() => { setIdentity(true); update({ storageAccessKey: '', storageSecretKey: '' }) }}>Server identity</button>
        </div>
        {identity ? <ServerIdentityHelp provider={form.storageProvider} /> : gcs ? <ServiceAccountJsonField id={`${fieldId}-secret-key`} value={form.storageSecretKey} onChange={value => update({ storageSecretKey: value })} configured={!!settings.data?.storageSecretKeyConfigured && settings.data.storageProvider === 'GCS' && form.storageBucket.trim() === (settings.data.storageBucket ?? '')} /> : <div className="form-grid" style={{ marginTop: 16 }}>
          <div className="form-field"><label htmlFor={`${fieldId}-access-key`}>{azure ? 'Storage account name' : 'Access key ID'}</label><input id={`${fieldId}-access-key`} type="password" placeholder={settings.data?.storageAccessKeyConfigured ? 'Configured; leave blank to retain' : ''} autoComplete="off" maxLength={255} value={form.storageAccessKey} onChange={event => update({ storageAccessKey: event.target.value })} /></div>
          <div className="form-field"><label htmlFor={`${fieldId}-secret-key`}>{azure ? 'Storage account key' : 'Secret access key'}</label><div className="secret-input"><input id={`${fieldId}-secret-key`} placeholder={settings.data?.storageSecretKeyConfigured ? 'Configured; leave blank to retain' : ''} type={showSecret ? 'text' : 'password'} autoComplete="new-password" maxLength={512} value={form.storageSecretKey} onChange={event => update({ storageSecretKey: event.target.value })} /><button type="button" className="utility-button" aria-label={showSecret ? 'Hide secret key' : 'Show secret key'} onClick={() => setShowSecret(!showSecret)}>{showSecret ? 'Hide' : 'Show'}</button></div></div>
        </div>}
      </> : <p className="field-help">Save this selection to disable object storage. Existing objects stay with their current provider.</p>}
    </Card>
    <div className="page-stack">
      <Card>
        <h2 className="card-title">Storage view path</h2><p className="field-help">The app browses registered document folders beneath this prefix.</p>
        <div className="form-field"><label htmlFor={`${fieldId}-root-path`}>Folder prefix</label><input id={`${fieldId}-root-path`} maxLength={512} placeholder="contract-management/documents" value={form.storageRootPath} onChange={event => update({ storageRootPath: event.target.value })} /></div>
        <div className="connection-preview"><span>Application scope</span><code>{enabled ? `${gcs ? 'gs' : azure ? 'azure' : 's3'}://${form.storageBucket.trim() || (azure ? 'container' : 'bucket')}/${prefix}/` : 'Object storage disabled'}</code></div>
        {settings.data && (form.storageProvider !== settings.data.storageProvider || form.storageBucket.trim() !== (settings.data.storageBucket ?? '') || prefix !== settings.data.storageRootPath || form.storageEndpoint.trim() !== (settings.data.storageEndpoint ?? '')) && <p className="feedback warning">Changing the location repoints the app. Existing files are not moved or copied.</p>}
      </Card>
      <Card>
        <h2 className="card-title">Check before saving</h2><p className="field-help">Test these details without changing the saved connection. This checks read access to the prefix; it does not upload files.</p>
        <Button type="button" variant="secondary" disabled={!enabled} loading={check.isPending} onClick={() => { if (validate()) check.mutate(form) }}> Test connection</Button>
        <div aria-live="polite">{check.isSuccess && <p className="feedback success">{check.data?.message}</p>}{check.isError && <p role="alert" className="feedback error">{getErrorMessage(check.error, 'Connection failed. Check your details and try again.')}</p>}</div>
      </Card>
    </div>
  </div>)
}
