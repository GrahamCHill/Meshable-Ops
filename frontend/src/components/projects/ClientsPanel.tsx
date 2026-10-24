import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { projectsApi, type Client, type ClientInput } from '../../api/projects'
import { ContactFields, Contacts, LinkFields } from './ContactFields'
import { ContextExtensions } from './ContextExtensions'
import { getErrorMessage } from '../admin/adminUtils'
import { Button } from '../ui'

export function ClientsPanel() {
  const client = useQueryClient(); const clients = useQuery({queryKey: ['project-clients'], queryFn: projectsApi.clients})
  const [selected, setSelected] = useState<Client>(); const [draft, setDraft] = useState<ClientInput>(); const [editingId, setEditingId] = useState<string>(); const [error, setError] = useState(''); const [busy, setBusy] = useState(false)
  const edit = (value?: Client) => {setSelected(undefined); setEditingId(value?.id); setDraft(value ?? {name: '', details: {description: '', contacts: [], links: []}, revision: -1}); setError('')}
  const save = async () => {if (!draft) return; setBusy(true); setError(''); try {const saved = await projectsApi.saveClient(editingId, draft); setDraft(undefined); setSelected(saved); await Promise.all([client.invalidateQueries({queryKey: ['project-clients']}), client.invalidateQueries({queryKey: ['project']}), client.invalidateQueries({queryKey: ['projects']})])} catch (failure) {setError(getErrorMessage(failure))} finally {setBusy(false)}}
  return <section className="page-stack"><div className="inline-actions"><h2>Clients</h2><Button onClick={() => edit()} disabled={!!draft}>New client</Button></div><p>Set contacts on each client. Linked projects use those contacts unless they have a project-specific override. Projects without a client use their own contacts.</p>
    {clients.isLoading && <p role="status">Loading clients…</p>}{clients.isError && <p role="alert">Unable to load clients.</p>}
    {!draft && <div className="project-table-wrap"><table className="project-table"><thead><tr><th>Client</th><th>Contacts</th><th>Actions</th></tr></thead><tbody>{clients.data?.map(item => <tr key={item.id}><td><button onClick={() => setSelected(item)}>{item.name}</button></td><td>{item.details.contacts.length}</td><td>{item.canManage && <Button variant="ghost" onClick={() => edit(item)}>Edit client</Button>}</td></tr>)}</tbody></table></div>}
    {draft && <form className="page-stack" onSubmit={event => {event.preventDefault(); void save()}}><fieldset disabled={busy} className="page-stack"><label className="form-field">Client name<input required maxLength={160} value={draft.name} onChange={event => setDraft({...draft, name: event.target.value})} /></label><label className="form-field">Description<textarea maxLength={12000} rows={4} value={draft.details.description} onChange={event => setDraft({...draft, details: {...draft.details, description: event.target.value}})} /></label><h3>Client contacts</h3><ContactFields value={draft.details.contacts} onChange={contacts => setDraft({...draft, details: {...draft.details, contacts}})} /><h3>Web links</h3><LinkFields value={draft.details.links} onChange={links => setDraft({...draft, details: {...draft.details, links}})} /><div className="inline-actions"><Button type="submit" loading={busy}>Save client</Button><Button type="button" variant="ghost" onClick={() => setDraft(undefined)}>Cancel</Button></div></fieldset></form>}
    {selected && !draft && <section className="page-stack"><h3>{selected.name}</h3><p className="project-note">{selected.details.description}</p><Contacts contacts={selected.details.contacts} />{selected.details.links.map((link, index) => <a key={index} href={link.url} target="_blank" rel="noreferrer">{link.label}</a>)}<Link to={`/projects?client=${selected.id}`}>View client projects</Link><ContextExtensions kind="CLIENT" id={selected.id} /></section>}
    {error && <p role="alert">{error}</p>}
  </section>
}
