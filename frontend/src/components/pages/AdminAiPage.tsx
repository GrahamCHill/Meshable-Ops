import {trimTrailingSlashes} from '../../lib/url'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { aiApi, endpointInput, type AiSettings, type AiSettingsInput } from '../../api/ai'
import { itemsApi } from '../../api/items'
import { AdminPageHeader } from '../admin/AdminPageHeader'
import { AiEndpointForm } from '../admin/ai/AiEndpointForm'
import { getErrorMessage } from '../admin/adminUtils'
import { Button, Spinner } from '../ui'

function draftOf(settings: AiSettings): AiSettingsInput {
  return { source: settings.source, enabled: settings.admin.enabled, indexContent: settings.admin.indexContent, searchEnabled: settings.admin.searchEnabled, launcherEnabled: settings.admin.launcherEnabled, chat: endpointInput(settings.admin.chat), embeddings: endpointInput(settings.admin.embeddings) }
}

export function AdminAiPage() {
  const client = useQueryClient()
  const query = useQuery({ queryKey: ['admin-ai-settings'], queryFn: aiApi.settings })
  const [draft, setDraft] = useState<AiSettingsInput | null>(null)
  const [dirty, setDirty] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  useEffect(() => { if (query.data && !dirty) setDraft(draftOf(query.data)) }, [query.data, dirty])
  const test = useMutation({ mutationFn: aiApi.test })
  const reindex = useMutation({ mutationFn: itemsApi.reindex })
  const change = (patch: Partial<AiSettingsInput>) => { setDraft(previous => previous && { ...previous, ...patch }); setDirty(true); setNotice(''); setError(''); test.reset() }
  const save = async () => {
    if (!draft) return
    setSaving(true); setError(''); setNotice('')
    try {
      // A manual request avoids keeping write-only API keys in the mutation cache.
      const saved = await aiApi.save(draft)
      client.setQueryData(['admin-ai-settings'], saved)
      client.invalidateQueries({ queryKey: ['ai-search-status'] })
      setDraft(draftOf(saved)); setDirty(false)
      setNotice('AI settings saved. Reindex storage after changing embeddings or content indexing.')
    } catch (failure) { setError(getErrorMessage(failure, 'Unable to save AI settings.')) }
    finally { setSaving(false) }
  }
  const settings = query.data
  const environment = draft?.source === 'ENVIRONMENT'
  const view = environment ? settings?.environment : settings?.admin
  const aiEnabled = environment ? view?.enabled : draft?.enabled
  const searchEnabled = environment ? view?.searchEnabled : draft?.searchEnabled
  const boundKey = (kind: 'chat' | 'embeddings') => !!view?.[kind].apiKeyConfigured && (environment || (draft?.[kind].provider === view[kind].provider && trimTrailingSlashes(draft[kind].baseUrl) === view[kind].baseUrl))

  return <div className="page-stack" style={{ maxWidth: 1000 }}>
    <AdminPageHeader title="AI & search" description="Connect answer generation to the documents your team can access in Storage." />
    <p><Link to="/admin/storage?tab=vectors">Manage indexing jobs and schedules</Link></p>
    {query.isLoading && <Spinner />}
    {query.isError && <div role="alert" className="feedback error">Unable to load AI settings. <Button variant="ghost" onClick={() => query.refetch()}>Retry</Button></div>}
    {settings && draft && view && <form className="page-stack" onSubmit={event => { event.preventDefault(); void save() }}>
      <fieldset disabled={saving} className="page-stack">
        <div className="form-field" style={{ maxWidth: 420 }}><label htmlFor="ai-configuration-source">Configuration source</label><select id="ai-configuration-source" value={draft.source} onChange={event => change({ source: event.target.value as AiSettings['source'] })}><option value="ENVIRONMENT">Server environment</option><option value="ADMIN">Admin settings</option></select><small>Environment and admin credentials stay separate. Switching source never copies keys between them.</small></div>
        {environment && <p className="field-help">Values below come from the server environment. Choose Admin settings to edit a separate saved configuration.</p>}
        {!environment && !settings.encryptionReady && <p role="status" className="feedback warning">Set APP_SECRETS_ENCRYPTION_KEY on the server before storing API keys.</p>}
        <label className="checkbox-label"><input type="checkbox" disabled={environment} checked={environment ? view.searchEnabled : draft.searchEnabled} onChange={event => change({ searchEnabled: event.target.checked })} /> Enable similarity search in Storage</label>
        <label className="checkbox-label"><input type="checkbox" disabled={environment} checked={environment ? view.launcherEnabled : draft.launcherEnabled} onChange={event => change({ launcherEnabled: event.target.checked })} /> Show bottom-right AI chat launcher</label>
        <label className="checkbox-label"><input type="checkbox" disabled={environment} checked={environment ? view.enabled : draft.enabled} onChange={event => change({ enabled: event.target.checked })} /> Enable AI answers</label>
        <p className="field-help">Storage search works without AI credentials. Switching search off hides its fields and blocks search and chat requests. The chat launcher appears only when search, AI answers, content indexing, and the launcher are enabled, with a chat model configured.</p>
        <label className="checkbox-label"><input type="checkbox" disabled={environment} checked={environment ? view.indexContent : draft.indexContent} onChange={event => change({ indexContent: event.target.checked })} /> Index document content</label>
        <p className="field-help">Supported text, PDF, and DOCX content goes to the configured embedding service. When someone asks a question, retrieved excerpts go to the configured chat provider. Only accessible folders are included.</p>
        {aiEnabled && <AiEndpointForm kind="chat" value={environment ? endpointInput(view.chat) : draft.chat} configured={boundKey('chat')} readOnly={environment} onChange={chat => change({ chat })} />}
        {searchEnabled && <AiEndpointForm kind="embeddings" value={environment ? endpointInput(view.embeddings) : draft.embeddings} configured={boundKey('embeddings')} readOnly={environment} onChange={embeddings => change({ embeddings })} />}
      </fieldset>
      {error && <p role="alert" className="feedback error">{error}</p>}
      {notice && <p role="status" className="feedback success">{notice}</p>}
      <div className="save-bar"><span>{dirty ? 'You have unsaved changes' : 'Settings are up to date'}</span><div className="inline-actions"><Button type="button" variant="ghost" disabled={!dirty || saving} onClick={() => { setDraft(draftOf(settings)); setDirty(false); setError(''); setNotice('') }}>Discard changes</Button><Button type="submit" disabled={!dirty} loading={saving}>Save AI settings</Button></div></div>
      <section className="page-stack"><h2 className="card-title">Check saved configuration</h2><p className="field-help">Tests use saved settings or environment settings. Reindexing reads shared storage and updates PostgreSQL; it may take a while for large locations.</p><div className="inline-actions">{aiEnabled && <Button type="button" variant="secondary" disabled={dirty || saving || test.isPending} onClick={() => test.mutate('chat')}>Test chat</Button>}{searchEnabled && <Button type="button" variant="secondary" disabled={dirty || saving || test.isPending} onClick={() => test.mutate('embeddings')}>Test embeddings</Button>}<Button type="button" variant="secondary" disabled={dirty || saving} loading={reindex.isPending} onClick={() => reindex.mutate()}>Reindex storage</Button></div>{test.isSuccess && <p role="status" className="feedback success">{test.data.message}</p>}{test.isError && <p role="alert" className="feedback error">{getErrorMessage(test.error)}</p>}{reindex.isSuccess && <p role="status">{reindex.data.message}</p>}{reindex.isError && <p role="alert" className="feedback error">{getErrorMessage(reindex.error)}</p>}</section>
    </form>}
  </div>
}
