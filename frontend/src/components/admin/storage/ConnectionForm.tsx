import { useId, useState } from 'react'
import { storageConnectionsApi, type StorageConnection, type StorageConnectionInput } from '../../../api/storageConnections'
import { Button, Card } from '../../ui'
import { getErrorMessage } from '../adminUtils'
import { useRefreshStorage } from './useRefreshStorage'
import { useCredentialRequest } from '../useCredentialRequest'
import { ServerIdentityHelp } from './ServerIdentityHelp'
import { ServiceAccountJsonField } from './ServiceAccountJsonField'

export function ConnectionForm({ initial, onClose }: { initial?: StorageConnection; onClose: () => void }) {
  const id = useId()
  const refresh = useRefreshStorage()
  const [form, setForm] = useState<StorageConnectionInput>({
    nickname: initial?.nickname ?? '', provider: initial?.provider ?? 'S3', bucket: initial?.bucket ?? '',
    region: initial?.region ?? 'us-east-1', endpoint: initial?.endpoint ?? '', accessKey: '',
    secretKey: '', useServerIdentity: initial ? !initial.secretConfigured && !initial.accessKeyConfigured : true,
    basePath: initial?.basePath ?? '', enabled: initial?.enabled ?? true,
  })
  const save = useCredentialRequest({ mutationFn: storageConnectionsApi.save, onSuccess: () => { refresh(); onClose() } })
  const azure = form.provider === 'AZURE_BLOB'
  const gcs = form.provider === 'GCS'
  const locationLocked = !!initial?.folders.length
  const sameService = initial?.provider === form.provider && (initial.endpoint ?? '') === form.endpoint.trim() && (!gcs || initial.bucket === form.bucket.trim())
  const savedAccess = sameService && !!initial?.accessKeyConfigured
  const savedSecret = sameService && !!initial?.secretConfigured
  const field = (key: keyof StorageConnectionInput, value: string | boolean) => setForm(previous => ({ ...previous, [key]: value }))
  return <Card>
    <form className="page-stack" onSubmit={event => { event.preventDefault(); save.mutate({ id: initial?.id, data: form }) }}>
      <div className="page-heading"><h2 className="card-title">{initial ? 'Edit connection' : 'Add a bucket or container'}</h2><Button type="button" variant="ghost" size="sm" onClick={onClose}> Close</Button></div>
      <fieldset className="settings-fieldset form-grid" disabled={save.isPending}>
        <div className="form-field"><label htmlFor={`${id}-nickname`}>In-app nickname</label><input id={`${id}-nickname`} required maxLength={100} placeholder="Legal archive" value={form.nickname} onChange={event => field('nickname', event.target.value)} /><small>This appears as a folder in Storage.</small></div>
        <div className="form-field"><label htmlFor={`${id}-provider`}>Provider</label><select id={`${id}-provider`} disabled={locationLocked} value={form.provider} onChange={event => setForm(previous => ({ ...previous, provider: event.target.value as StorageConnectionInput['provider'], bucket: '', region: event.target.value === 'S3' ? 'us-east-1' : '', endpoint: '', accessKey: '', secretKey: '', useServerIdentity: true }))}><option value="S3">Amazon S3 / compatible</option><option value="AZURE_BLOB">Azure Blob</option><option value="GCS">Google Cloud Storage (GCP)</option></select></div>
        <div className="form-field"><label htmlFor={`${id}-bucket`}>{azure ? 'Container name' : 'Bucket name'}</label><input id={`${id}-bucket`} disabled={locationLocked} required maxLength={255} value={form.bucket} onChange={event => field('bucket', event.target.value)} /></div>
        {form.provider === 'S3' && <div className="form-field"><label htmlFor={`${id}-region`}>Region</label><input id={`${id}-region`} maxLength={255} placeholder="us-east-1" value={form.region} onChange={event => field('region', event.target.value)} /></div>}
        {!gcs && <div className="form-field full-width"><label htmlFor={`${id}-endpoint`}>{azure ? 'Blob service endpoint' : 'Custom endpoint (optional)'}</label><input id={`${id}-endpoint`} disabled={locationLocked} type="url" required={azure} maxLength={512} placeholder={azure ? 'https://account.blob.core.windows.net' : 'https://s3.example.com'} value={form.endpoint} onChange={event => field('endpoint', event.target.value)} /></div>}
        {gcs && <p className="field-help full-width">Enter the bucket name without gs://. Google Cloud Storage uses its standard API endpoint; no region or project ID is required.</p>}
        <div className="form-field full-width"><label htmlFor={`${id}-base`}>Start folder (optional)</label><input id={`${id}-base`} disabled={locationLocked} maxLength={512} placeholder="Leave blank to choose folders from the bucket/container root" value={form.basePath} onChange={event => field('basePath', event.target.value)} /><small>Limits the admin folder picker to this prefix. Saving a connection shares no folders automatically.</small></div>
        {locationLocked && <p className="field-help full-width">The storage location is fixed while folders are selected. Remove those selections first to repoint this connection.</p>}
        <div className="form-field full-width"><label htmlFor={`${id}-auth`}>Authentication</label><select id={`${id}-auth`} value={form.useServerIdentity ? 'identity' : 'keys'} onChange={event => setForm(previous => ({ ...previous, useServerIdentity: event.target.value === 'identity', accessKey: '', secretKey: '' }))}><option value="identity">Use server identity</option><option value="keys">{gcs ? 'Service account JSON key' : azure ? 'Storage account key' : 'Access keys'}</option></select></div>
        {form.useServerIdentity && <div className="full-width"><ServerIdentityHelp provider={form.provider} /></div>}
        {!form.useServerIdentity && (gcs ? <ServiceAccountJsonField id={`${id}-secret`} value={form.secretKey} onChange={value => field('secretKey', value)} configured={savedSecret} required={!savedSecret} /> : <><div className="form-field"><label htmlFor={`${id}-access`}>{azure ? 'Storage account name' : 'Access key ID'}</label><input id={`${id}-access`} type="password" required={!savedAccess} placeholder={savedAccess ? 'Configured; leave blank to retain' : ''} autoComplete="off" maxLength={255} value={form.accessKey} onChange={event => field('accessKey', event.target.value)} /></div><div className="form-field"><label htmlFor={`${id}-secret`}>{azure ? 'Storage account key' : 'Secret access key'}</label><input id={`${id}-secret`} type="password" required={!savedSecret || !!form.accessKey} autoComplete="new-password" maxLength={512} placeholder={savedSecret ? 'Configured; leave blank to retain' : ''} value={form.secretKey} onChange={event => field('secretKey', event.target.value)} /></div></>)}
        <label className="checkbox-label full-width"><input type="checkbox" checked={form.enabled} onChange={event => field('enabled', event.target.checked)} /> Enable this connection in Storage</label>
      </fieldset>
      {save.isError && <p role="alert" className="feedback error">{getErrorMessage(save.error)}</p>}
      <div className="inline-actions"><Button type="submit" loading={save.isPending}> Save connection</Button><Button type="button" variant="ghost" onClick={onClose}>Cancel</Button></div>
    </form>
  </Card>
}
