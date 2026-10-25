import { useEffect, useId, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { generatedPdfCleanupApi, type PdfCleanupCounts, type PdfCleanupInput } from '../../../api/generatedPdfCleanup'
import { Button, Card, Spinner } from '../../ui'
import { Modal } from '../../ui/Modal'
import { formatDate, getErrorMessage } from '../adminUtils'

function summary(counts: PdfCleanupCounts) {
  const size = counts.bytes < 1024 * 1024 ? `${(counts.bytes / 1024).toFixed(1)} KiB` : `${(counts.bytes / (1024 * 1024)).toFixed(1)} MiB`
  return `${counts.reports} project reports and ${counts.documents} template PDFs (${size} of PDF content)`
}

export function GeneratedPdfCleanupSettings() {
  const id = useId()
  const client = useQueryClient()
  const edited = useRef(false)
  const [form, setForm] = useState<PdfCleanupInput>({ schedule: 'NEVER', retentionDays: 30, destination: 'AUTO', connectionId: null })
  const [confirm, setConfirm] = useState(false)
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')
  const query = useQuery({ queryKey: ['generated-pdf-cleanup'], queryFn: generatedPdfCleanupApi.get, refetchInterval: 30_000 })
  const saved = query.data?.settings
  useEffect(() => {
    if (saved && !edited.current) setForm({ schedule: saved.schedule, retentionDays: saved.retentionDays, destination: saved.destination, connectionId: saved.connectionId ?? null })
  }, [saved])
  const save = useMutation({
    mutationFn: generatedPdfCleanupApi.update,
    onSuccess: result => {
      edited.current = false
      setForm({ schedule: result.settings.schedule, retentionDays: result.settings.retentionDays, destination: result.settings.destination, connectionId: result.settings.connectionId ?? null })
      client.setQueryData(['generated-pdf-cleanup'], result)
      setError(''); setNotice('Generated PDF cleanup settings saved.')
    },
    onError: failure => { setNotice(''); setError(getErrorMessage(failure, 'Unable to save PDF cleanup settings.')) },
  })
  const clear = useMutation({
    mutationFn: generatedPdfCleanupApi.clear,
    onSuccess: result => {
      setConfirm(false); setError(''); setNotice(`Cleared ${summary(result)}.`)
      client.invalidateQueries({ queryKey: ['generated-pdf-cleanup'] })
      client.invalidateQueries({ queryKey: ['project-reports'] })
    },
    onError: failure => { setConfirm(false); setNotice(''); setError(getErrorMessage(failure, 'Unable to clear expired PDFs.')) },
  })
  const busy = save.isPending || clear.isPending
  const selectedDestination = form.destination === 'CONNECTION' ? `CONNECTION:${form.connectionId ?? ''}` : form.destination
  const storageChoices = query.data?.storage?.choices ?? []
  const dirty = !!saved && (form.schedule !== saved.schedule || form.retentionDays !== saved.retentionDays || form.destination !== saved.destination || (form.connectionId ?? null) !== (saved.connectionId ?? null))
  const update = (patch: Partial<PdfCleanupInput>) => {
    edited.current = true; setForm(previous => ({ ...previous, ...patch })); setNotice(''); setError('')
  }
  if (query.isLoading) return <Card><Spinner /> Loading PDF cleanup settings…</Card>
  if (!saved || query.isError) return <Card><div role="alert" className="feedback error">Unable to load PDF cleanup settings.</div><Button variant="secondary" onClick={() => query.refetch()}>Try again</Button></Card>
  return <div className="page-stack">
    <form className="page-stack" onSubmit={event => { event.preventDefault(); save.mutate(form) }}>
      <Card>
        <h2 className="card-title">Generated PDF storage</h2>
        <p className="field-help">Prefer a connected storage container for new document, billing and project report PDFs. If no location is configured or an upload fails, the PDF stays safely in the database. Existing database PDFs remain available.</p>
        <fieldset disabled={busy} className="settings-fieldset form-grid">
          <div className="form-field full-width"><label htmlFor={`${id}-destination`}>Destination for new PDFs</label><select id={`${id}-destination`} value={selectedDestination} onChange={event => {
            const value = event.target.value
            update(value.startsWith('CONNECTION:') ? { destination: 'CONNECTION', connectionId: value.slice(11) } : { destination: value as PdfCleanupInput['destination'], connectionId: null })
          }}>{!storageChoices.some(choice => choice.value === selectedDestination) && <option value={selectedDestination}>Saved destination (currently unavailable)</option>}{storageChoices.map(choice => <option key={choice.value} value={choice.value}>{choice.label}</option>)}</select><small>Automatic uses the default container, then the first enabled shared connection by nickname, then the database. Select a Drive destination explicitly to use its write access.</small></div>
        </fieldset>
        <p className="field-help">The dedicated <code>__generated_pdfs__</code> folder is excluded from this app’s search, embeddings and indexing. It is hidden from ordinary storage browsing and folder downloads; PDFs remain accessible through their document or report.</p>
        <p className="field-help">Drive destinations need upload, download and delete permission. Configure the connection in its plugin settings before selecting it. The storage choice applies to new PDFs; it does not move existing files.</p>
        <p className="field-help">Current destination: {query.data?.storage?.destination ?? 'Database'}{query.data?.storage?.folder && <> · <code>{query.data.storage.folder}</code></>}<br />{saved.lastStorageStatus}{saved.lastStorageAt && <> · {formatDate(saved.lastStorageAt)}</>}</p>
        {!!query.data?.storage?.pendingDeletes && <p className="field-help" role="status">Cloud deletions waiting or retrying: {query.data.storage.pendingDeletes}. Their locations are retained until deletion succeeds.</p>}
      </Card>
      <Card>
        <h2 className="card-title">Generated PDF cleanup</h2>
        <p className="field-help">Clear expired project report exports and rebuildable template PDFs to reduce stored export content. Automatic cleanup is off until you choose a schedule.</p>
        <fieldset disabled={busy} className="settings-fieldset form-grid">
          <div className="form-field"><label htmlFor={`${id}-schedule`}>Automatic cleanup</label><select id={`${id}-schedule`} value={form.schedule} onChange={event => update({ schedule: event.target.value as PdfCleanupInput['schedule'] })}><option value="NEVER">Never</option><option value="DAILY">Every day</option><option value="WEEKLY">Every week</option></select></div>
          <div className="form-field"><label htmlFor={`${id}-retention`}>Keep generated PDFs for (days)</label><input id={`${id}-retention`} type="number" required min={1} max={3650} step={1} value={form.retentionDays} onChange={event => update({ retentionDays: Number(event.target.value) })} /><small>Only PDFs older than this period are eligible, including when you clear them manually.</small></div>
        </fieldset>
        <p className="field-help">Uploaded files, Markdown, source data, templates, billing PDFs, and unclassified older documents are kept. Template documents generated with cleanup support retain their saved inputs and rebuild when viewed, downloaded, or emailed. Workflow references remain available.</p>
        <p className="field-help">Expired project exports leave the report history. Generate a fresh report from the project when needed; it will use current project data.</p>
        <p className="field-help">Next automatic run: {saved.schedule === 'NEVER' ? 'Never' : formatDate(saved.nextRunAt)}<br />Last successful cleanup: {formatDate(saved.lastRunAt)}<br />{saved.lastStatus === 'OK' ? `Last cleanup cleared ${summary(saved.lastResult)}.` : saved.lastStatus ?? 'No cleanups recorded yet.'}</p>
        <p className="field-help">The server must be running for scheduled cleanup. A missed run resumes after startup. External file deletions finish in the background and retry if storage is temporarily unavailable.</p>
      </Card>
      {error && <div role="alert" className="feedback error">{error}</div>}
      {notice && <div role="status" className="feedback success">{notice}</div>}
      <div className="save-bar"><span>{dirty ? 'You have unsaved changes' : 'All changes saved'}</span><div className="inline-actions"><Button type="button" variant="ghost" disabled={!dirty || busy} onClick={() => { edited.current = false; setForm({ schedule: saved.schedule, retentionDays: saved.retentionDays, destination: saved.destination, connectionId: saved.connectionId ?? null }); setError(''); setNotice('') }}>Discard changes</Button><Button type="submit" disabled={!dirty || busy} loading={save.isPending}>Save settings</Button></div></div>
    </form>
    <Card>
      <h2 className="card-title">Clear expired PDFs now</h2>
      <p className="field-help">Currently eligible under the saved {saved.retentionDays}-day retention: {summary(query.data!.eligible)}.</p>
      <Button variant="secondary" disabled={busy || dirty || query.isFetching || query.data!.eligible.reports + query.data!.eligible.documents === 0} onClick={() => setConfirm(true)}>Clear expired PDFs</Button>
      {dirty && <p className="field-help">Save or discard changes before clearing PDFs.</p>}
    </Card>
    {confirm && <Modal title="Clear expired generated PDFs?" onClose={() => setConfirm(false)} busy={busy}>
      <p>Clear {summary(query.data!.eligible)} older than {saved.retentionDays} days? Project exports will leave report history. Template documents keep their saved inputs and rebuild when opened.</p>
      <div className="inline-actions"><Button variant="ghost" disabled={busy} onClick={() => setConfirm(false)}>Cancel</Button><Button variant="danger" loading={clear.isPending} onClick={() => clear.mutate()}>Clear expired PDFs</Button></div>
    </Modal>}
  </div>
}
