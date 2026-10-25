import { Button, Card, Spinner } from '../../ui'
import { ReindexScheduleSettings } from './ReindexScheduleSettings'
import { DefaultConnectionSettings } from './DefaultConnectionSettings'
import { useStorageSettings, type StorageSettingsTab } from './useStorageSettings'

export function StorageSettingsPanel({ tab }: { tab: StorageSettingsTab }) {
  const state = useStorageSettings()
  const { form, error, setError, notice, setNotice, settings, restore, check, save, dirty, busy, validate } = state
  if (tab === 'shared' || tab === 'cleanup') return null
  if (settings.isLoading) return <Card><Spinner /> Loading settings…</Card>
  if (settings.isError) return <Card><div role="alert" className="feedback error">Unable to load storage settings.</div><Button variant="secondary" onClick={() => settings.refetch()}>Try again</Button></Card>
  return (<form className="page-stack" onSubmit={event => { event.preventDefault(); if (validate()) save.mutate(form) }}>
    <fieldset disabled={busy} className="settings-fieldset">
      {tab === 'connection' ? <DefaultConnectionSettings state={state} /> : <ReindexScheduleSettings state={state} />}
    </fieldset>
    {error && <div role="alert" className="feedback error">{error}</div>}
    {notice && <div role="status" className="feedback success">{notice}</div>}
    <div className="save-bar"><span>{dirty ? 'You have unsaved changes' : 'All changes saved'}</span><div className="inline-actions"><Button type="button" variant="ghost" disabled={!dirty || busy} onClick={() => { if (settings.data) restore(settings.data); check.reset(); setError(''); setNotice('') }}>Discard changes</Button><Button type="submit" loading={save.isPending} disabled={!dirty || check.isPending}> Save settings</Button></div></div>
  </form>)
}
