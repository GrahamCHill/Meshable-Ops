import { useEffect, useId, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { adminSettingsApi, itemsApi, type AdminSettings } from '../../api/client'
import { Badge, Button, Card, Spinner } from '../ui'
import { AdminPageHeader } from '../admin/AdminPageHeader'
import { StorageConnectionsPanel } from '../admin/StorageConnectionsPanel'
import { formatDate, getErrorMessage } from '../admin/adminUtils'

type SettingsForm = Pick<AdminSettings, 'storageProvider' | 'storageRootPath' | 'vectorReindexEnabled' | 'vectorReindexIntervalMinutes'> & {
  storageBucket: string; storageRegion: string; storageEndpoint: string; storageAccessKey: string; storageSecretKey: string
}
const emptyForm: SettingsForm = {
  storageProvider: 'NONE', storageBucket: '', storageRegion: '', storageEndpoint: '', storageAccessKey: '',
  storageSecretKey: '', storageRootPath: 'folders', vectorReindexEnabled: false, vectorReindexIntervalMinutes: 1440,
}
function toForm(settings: AdminSettings): SettingsForm {
  return {
    storageProvider: settings.storageProvider,
    storageBucket: settings.storageBucket ?? '', storageRegion: settings.storageRegion ?? '',
    storageEndpoint: settings.storageEndpoint ?? '', storageAccessKey: settings.storageAccessKey ?? '',
    storageSecretKey: settings.storageSecretKey ?? '', storageRootPath: settings.storageRootPath,
    vectorReindexEnabled: settings.vectorReindexEnabled,
    vectorReindexIntervalMinutes: settings.vectorReindexIntervalMinutes,
  }
}

export function AdminStoragePage() {
  const fieldId = useId()
  const queryClient = useQueryClient()
  const edited = useRef(false)
  const [form, setForm] = useState(emptyForm)
  const [tab, setTab] = useState<'shared' | 'connection' | 'vectors'>('shared')
  const [identity, setIdentity] = useState(false)
  const [showSecret, setShowSecret] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const settings = useQuery({ queryKey: ['admin-settings'], queryFn: adminSettingsApi.get })
  const restore = (saved: AdminSettings) => {
    edited.current = false
    setForm(toForm(saved))
    setIdentity(!saved.storageAccessKey && !saved.storageSecretKey)
    setShowSecret(false)
  }
  useEffect(() => { if (settings.data && !edited.current) restore(settings.data) }, [settings.data])

  const check = useMutation({ mutationFn: adminSettingsApi.testStorage })
  const save = useMutation({
    mutationFn: adminSettingsApi.update,
    onSuccess: saved => {
      restore(saved)
      check.reset()
      queryClient.setQueryData(['admin-settings'], saved)
      queryClient.invalidateQueries({ queryKey: ['items'] })
      setError('')
      setNotice(`Settings saved ${formatDate(saved.updatedAt)}`)
    },
    onError: err => { setNotice(''); setError(getErrorMessage(err, 'Unable to save settings.')) },
  })
  const reindex = useMutation({
    mutationFn: itemsApi.reindex,
    onSuccess: result => { queryClient.invalidateQueries({ queryKey: ['admin-settings'] }); setNotice(result.message); setError('') },
    onError: err => { setNotice(''); setError(getErrorMessage(err, 'Unable to recalculate vectors.')) },
  })
  const update = (patch: Partial<SettingsForm>) => {
    edited.current = true
    setForm(previous => ({ ...previous, ...patch }))
    setError(''); setNotice(''); check.reset()
  }
  const dirty = !!settings.data && JSON.stringify(form) !== JSON.stringify(toForm(settings.data))
  const azure = form.storageProvider === 'AZURE_BLOB'
  const enabled = form.storageProvider !== 'NONE'
  const busy = save.isPending || check.isPending
  const prefix = form.storageRootPath.trim().replace(/\\/g, '/').split('/').filter(Boolean).join('/')
  const validate = () => {
    const message = !prefix || prefix.split('/').some(part => part === '.' || part === '..') ? 'Enter a folder prefix without . or .. segments.'
      : enabled && !form.storageBucket.trim() ? `Enter a ${azure ? 'container' : 'bucket'} name.`
      : azure && !form.storageEndpoint.trim() ? 'Enter the Azure Blob service endpoint.'
      : enabled && !identity && (!form.storageAccessKey.trim() || !form.storageSecretKey.trim()) ? 'Complete both credential fields or select server identity.'
      : form.vectorReindexIntervalMinutes < 15 || form.vectorReindexIntervalMinutes > 10080 ? 'Choose an interval between 15 and 10080 minutes.' : ''
    setError(message)
    return !message
  }

  return (
    <div className="page-stack">
      <AdminPageHeader title="Storage connections" description="Manage named buckets and containers, select accessible folders, and maintain the search index."  actions={<Badge color="accent">Super admin · ADMIN</Badge>} />
      <nav className="section-tabs" aria-label="Storage settings">
        <button className={tab === 'shared' ? 'is-active' : ''} aria-current={tab === 'shared' ? 'page' : undefined} onClick={() => setTab('shared')}>Shared locations</button>
        <button className={tab === 'connection' ? 'is-active' : ''} aria-current={tab === 'connection' ? 'page' : undefined} onClick={() => setTab('connection')}>Default connection</button>
        <button className={tab === 'vectors' ? 'is-active' : ''} aria-current={tab === 'vectors' ? 'page' : undefined} onClick={() => setTab('vectors')}>Search index</button>
      </nav>
      {tab === 'shared' ? <StorageConnectionsPanel /> : settings.isLoading ? <Card><Spinner /> Loading settings…</Card> : settings.isError ? <Card><div role="alert" className="feedback error">Unable to load storage settings.</div><Button variant="secondary" onClick={() => settings.refetch()}>Try again</Button></Card> : (
        <form className="page-stack" onSubmit={event => { event.preventDefault(); if (validate()) save.mutate(form) }}>
          <fieldset disabled={busy} className="settings-fieldset">
            {tab === 'connection' ? <div className="storage-layout">
              <Card>
                <div className="step-heading"><span>1</span><div><h2>Choose your provider</h2><p>Use a cloud service or an S3-compatible store.</p></div></div>
                <div className="provider-options" role="group" aria-label="Storage provider">
                  {([{ value: 'S3', label: 'Amazon S3', detail: 'AWS, MinIO & compatible' }, { value: 'AZURE_BLOB', label: 'Azure Blob', detail: 'Azure Storage & Azurite' }, { value: 'NONE', label: 'No storage', detail: 'Disconnect object storage' }] as const).map(provider => (
                    <button type="button" key={provider.value} className={`provider-option${form.storageProvider === provider.value ? ' is-selected' : ''}`} aria-pressed={form.storageProvider === provider.value} onClick={() => {
                      if (provider.value === form.storageProvider) return
                      update({ storageProvider: provider.value, storageBucket: '', storageEndpoint: '', storageRegion: provider.value === 'S3' ? 'us-east-1' : '', storageAccessKey: '', storageSecretKey: '' })
                      setIdentity(true); setShowSecret(false)
                    }}><strong>{provider.label}</strong><small>{provider.detail}</small></button>
                  ))}
                </div>
                {enabled ? <>
                  <div className="step-heading"><span>2</span><div><h2>Connection details</h2><p>{azure ? 'Use the Blob service endpoint for your storage account.' : 'Enter your bucket and AWS region. Custom endpoints are optional.'}</p></div></div>
                  <div className="form-grid">
                    <div className="form-field"><label htmlFor={`${fieldId}-bucket`}>{azure ? 'Container name' : 'Bucket name'}</label><input id={`${fieldId}-bucket`} maxLength={255} placeholder="contract-documents" value={form.storageBucket} onChange={event => update({ storageBucket: event.target.value })} /></div>
                    {!azure && <div className="form-field"><label htmlFor={`${fieldId}-region`}>AWS region</label><input id={`${fieldId}-region`} maxLength={255} placeholder="us-east-1 (default)" value={form.storageRegion} onChange={event => update({ storageRegion: event.target.value })} /></div>}
                    <div className="form-field full-width"><label htmlFor={`${fieldId}-endpoint`}>{azure ? 'Blob service endpoint' : 'Custom endpoint (optional)'}</label><input id={`${fieldId}-endpoint`} maxLength={512} type="url" placeholder={azure ? 'https://account.blob.core.windows.net' : 'http://minio:9000'} value={form.storageEndpoint} onChange={event => update({ storageEndpoint: event.target.value })} /><small>{azure ? 'For Azurite, include the account path, for example http://azurite:10000/devstoreaccount1.' : 'Leave blank for AWS. For MinIO, use an address reachable by the application server.'}</small></div>
                  </div>
                  <div className="step-heading"><span>3</span><div><h2>Authentication</h2><p>Credentials are used by the application server.</p></div></div>
                  <div className="segmented-control" role="group" aria-label="Authentication method">
                    <button type="button" aria-pressed={!identity} className={!identity ? 'is-active' : ''} onClick={() => { setIdentity(false); check.reset() }}>{azure ? 'Account key' : 'Access keys'}</button>
                    <button type="button" aria-pressed={identity} className={identity ? 'is-active' : ''} onClick={() => { setIdentity(true); update({ storageAccessKey: '', storageSecretKey: '' }) }}>Server identity</button>
                  </div>
                  {identity ? <p className="field-help">{azure ? 'Uses the Azure identity configured on the server. Grant access to this container before testing.' : 'Uses the AWS credentials or IAM role configured on the server. Grant access to this bucket and prefix before testing.'}</p> : <div className="form-grid" style={{ marginTop: 16 }}>
                    <div className="form-field"><label htmlFor={`${fieldId}-access-key`}>{azure ? 'Storage account name' : 'Access key ID'}</label><input id={`${fieldId}-access-key`} autoComplete="off" maxLength={255} value={form.storageAccessKey} onChange={event => update({ storageAccessKey: event.target.value })} /></div>
                    <div className="form-field"><label htmlFor={`${fieldId}-secret-key`}>{azure ? 'Storage account key' : 'Secret access key'}</label><div className="secret-input"><input id={`${fieldId}-secret-key`} type={showSecret ? 'text' : 'password'} autoComplete="new-password" maxLength={512} value={form.storageSecretKey} onChange={event => update({ storageSecretKey: event.target.value })} /><button type="button" className="utility-button" aria-label={showSecret ? 'Hide secret key' : 'Show secret key'} onClick={() => setShowSecret(!showSecret)}>{showSecret ? 'Hide' : 'Show'}</button></div></div>
                  </div>}
                </> : <p className="field-help">Save this selection to disable object storage. Existing objects stay with their current provider.</p>}
              </Card>
              <div className="page-stack">
                <Card>
                  <h2 className="card-title">Storage view path</h2><p className="field-help">The app browses registered document folders beneath this prefix.</p>
                  <div className="form-field"><label htmlFor={`${fieldId}-root-path`}>Folder prefix</label><input id={`${fieldId}-root-path`} maxLength={512} placeholder="contract-management/documents" value={form.storageRootPath} onChange={event => update({ storageRootPath: event.target.value })} /></div>
                  <div className="connection-preview"><span>Application scope</span><code>{enabled ? `${azure ? 'azure' : 's3'}://${form.storageBucket.trim() || (azure ? 'container' : 'bucket')}/${prefix}/` : 'Object storage disabled'}</code></div>
                  {settings.data && (form.storageProvider !== settings.data.storageProvider || form.storageBucket.trim() !== (settings.data.storageBucket ?? '') || prefix !== settings.data.storageRootPath || form.storageEndpoint.trim() !== (settings.data.storageEndpoint ?? '')) && <p className="feedback warning">Changing the location repoints the app. Existing files are not moved or copied.</p>}
                </Card>
                <Card>
                  <h2 className="card-title">Check before saving</h2><p className="field-help">Test these details without changing the saved connection. This checks read access to the prefix; it does not upload files.</p>
                  <Button type="button" variant="secondary" disabled={!enabled} loading={check.isPending} onClick={() => { if (validate()) check.mutate(form) }}> Test connection</Button>
                  <div aria-live="polite">{check.isSuccess && <p className="feedback success">{check.data.message}</p>}{check.isError && <p role="alert" className="feedback error">{getErrorMessage(check.error, 'Connection failed. Check your details and try again.')}</p>}</div>
                </Card>
              </div>
            </div> : <Card>
              <h2 className="card-title">Search index maintenance</h2><p className="field-help">Recalculate searchable vectors for folder and document metadata.</p>
              <label className="checkbox-label"><input type="checkbox" checked={form.vectorReindexEnabled} onChange={event => update({ vectorReindexEnabled: event.target.checked })} /> Recalculate automatically</label>
              <div className="form-field" style={{ maxWidth: 360, marginTop: 16 }}><label htmlFor={`${fieldId}-interval`}>Interval in minutes</label><input id={`${fieldId}-interval`} type="number" min={15} max={10080} disabled={!form.vectorReindexEnabled} value={form.vectorReindexIntervalMinutes} onChange={event => update({ vectorReindexIntervalMinutes: Number(event.target.value) })} /><small>15 minutes to 7 days. Save to apply scheduling changes.</small></div>
              <p className="field-help">Last run: {formatDate(settings.data?.lastVectorReindexAt)}<br />{settings.data?.lastVectorReindexStatus ?? 'No runs recorded yet.'}</p>
              <Button type="button" variant="secondary" disabled={dirty} loading={reindex.isPending} onClick={() => reindex.mutate()}> Recalculate now</Button>
              {dirty && <p className="field-help">Save or discard changes before running the index against the saved connection.</p>}
            </Card>}
          </fieldset>
          {error && <div role="alert" className="feedback error">{error}</div>}
          {notice && <div role="status" className="feedback success">{notice}</div>}
          <div className="save-bar"><span>{dirty ? 'You have unsaved changes' : 'All changes saved'}</span><div className="inline-actions"><Button type="button" variant="ghost" disabled={!dirty || busy} onClick={() => { if (settings.data) restore(settings.data); check.reset(); setError(''); setNotice('') }}>Discard changes</Button><Button type="submit" loading={save.isPending} disabled={!dirty || check.isPending}> Save settings</Button></div></div>
        </form>
      )}
    </div>
  )
}
