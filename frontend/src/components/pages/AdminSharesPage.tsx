import { useEffect, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { sharesApi, type ShareSettings } from '../../api/shares'
import { AdminPageHeader } from '../admin/AdminPageHeader'
import { getErrorMessage } from '../admin/adminUtils'
import { Button, Card, Spinner } from '../ui'

export function AdminSharesPage() {
  const client = useQueryClient()
  const settings = useQuery({ queryKey: ['share-settings'], queryFn: sharesApi.settings })
  const list = useQuery({ queryKey: ['admin-shares'], queryFn: sharesApi.list })
  const [draft, setDraft] = useState<ShareSettings>()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  useEffect(() => { if (settings.data) setDraft(previous => previous ?? settings.data) }, [settings.data])
  const run = async (action: () => Promise<void>) => { setBusy(true); setError(''); setNotice(''); try { await action() } catch (failure) { setError(getErrorMessage(failure, 'Unable to update sharing.')) } finally { setBusy(false) } }
  return <div className="page-stack" style={{ maxWidth: 900 }}>
    <AdminPageHeader title="File sharing" description="Email password-protected, watermarked PDF attachments from the storage browser." />
    {settings.isLoading && <Spinner />}{settings.isError && <p role="alert">Unable to load sharing settings.</p>}
    {draft && <form className="page-stack" onSubmit={e => { e.preventDefault(); void run(async () => { const saved = await sharesApi.saveSettings(draft); client.setQueryData(['share-settings'], saved); setNotice('Sharing settings saved.') }) }}>
      <fieldset disabled={busy} className="page-stack">
        <label className="checkbox-label"><input type="checkbox" checked={draft.enabled} onChange={e => setDraft({ ...draft, enabled: e.target.checked })} /> Enable PDF sharing</label>
        <div className="form-field"><label htmlFor="share-origin">Public app origin</label><input id="share-origin" type="url" maxLength={2000} placeholder="https://app.example.com" value={draft.publicBaseUrl} onChange={e => setDraft({ ...draft, publicBaseUrl: e.target.value })} /><small>HTTPS origin with no path. Uses APP_PUBLIC_BASE_URL when blank. Loopback HTTP is allowed for development.</small></div>
        <div className="form-field"><label htmlFor="share-max-hours">Maximum online link lifetime (hours)</label><input id="share-max-hours" type="number" min={1} max={720} required value={draft.maxLifetimeHours} onChange={e => setDraft({ ...draft, maxLifetimeHours: Number(e.target.value) })} /></div>
      </fieldset>
      {!settings.data?.emailAvailable && <p>Configure email delivery before sharing PDFs.</p>}
      <p className="field-help">Disabling sharing hides the storage action and blocks existing online links. Download policy also applies. Link expiry and revocation cannot retract an emailed attachment; the attachment keeps its password and watermark.</p>
      <div><Button type="submit" loading={busy}>Save sharing settings</Button></div>
    </form>}
    {error && <p role="alert" className="feedback error">{error}</p>}{notice && <p role="status">{notice}</p>}
    <section className="page-stack"><h2>Recent shares</h2><p>Open a PDF in storage and choose Email PDF to send a new protected copy. The latest 100 shares appear here.</p>
      {list.isLoading && <Spinner />}{list.isError && <p role="alert">Unable to load shares.</p>}{list.data?.length === 0 && <p>No files shared yet.</p>}
      {list.data?.map(share => <Card key={share.id}><div className="inline-actions"><div style={{ flex: 1 }}><strong>{share.filename}</strong><p>{share.recipient} · {share.deliveryStatus}</p><small>Online link: {share.revoked ? 'Revoked' : new Date(share.expiresAt).getTime() <= Date.now() ? 'Expired' : `Expires ${new Date(share.expiresAt).toLocaleString()}`}</small></div>{!share.revoked && <Button variant="ghost" disabled={busy} onClick={() => void run(async () => { await sharesApi.revoke(share.id); await list.refetch(); setNotice('Online link revoked. The emailed attachment remains accessible with its password.') })}>Revoke online link</Button>}</div></Card>)}
    </section>
  </div>
}
