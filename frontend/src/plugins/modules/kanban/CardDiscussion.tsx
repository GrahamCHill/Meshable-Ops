import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { boardsApi, type BoardCard } from './api'
import { useAvailablePlugins } from '../../../api/plugins'
import { pluginRole } from '../../../api/access'
import { useAuthStore } from '../../../hooks/useAuthStore'
import { getErrorMessage } from '../../../components/admin/adminUtils'
import { Button } from '../../../components/ui'

export function CardDiscussion({boardId, cards, locked}: {boardId: string; cards: BoardCard[]; locked: boolean}) {
  const [card, setCard] = useState(''); const [text, setText] = useState(''); const [mentions, setMentions] = useState<string[]>([]); const [error, setError] = useState(''); const [busy, setBusy] = useState(false)
  const plugins = useAvailablePlugins(); const role = useAuthStore(state => pluginRole(state.user?.role ?? '')); const client = useQueryClient()
  const table = plugins.data?.find(plugin => plugin.manifest.id === 'kanban')?.tableAccess.plugin_kanban_comments
  const canRead = table?.readRoles.includes(role) === true; const canWrite = table?.writeRoles.includes(role) === true
  const comments = useQuery({queryKey: ['kanban-comments', boardId, card], queryFn: () => boardsApi.comments(boardId, card), enabled: !!card && canRead})
  const people = useQuery({queryKey: ['board-members', boardId], queryFn: () => boardsApi.members(boardId), enabled: canRead})
  const add = async () => {setBusy(true); setError(''); try {await boardsApi.comment(boardId, card, text, mentions); setText(''); setMentions([]); await client.invalidateQueries({queryKey: ['kanban-comments', boardId, card]})} catch (failure) {setError(getErrorMessage(failure))} finally {setBusy(false)}}
  if (!canRead) return null
  return <section className="page-stack"><h2>Card comments & mentions</h2><label className="form-field">Ticket<select disabled={locked} value={card} onChange={event => {setCard(event.target.value); setText(''); setMentions([]); setError('')}}><option value="">Choose a saved ticket</option>{cards.map(item => <option key={item.id} value={item.id}>{item.reference} {item.title}</option>)}</select></label>
    {locked && <p>Save or discard board changes before commenting.</p>}{comments.isError && <p role="alert">Unable to load comments.</p>}{comments.data?.map(comment => <article key={comment.id} className="page-stack"><small>{comment.authorName} · {new Date(comment.createdAt).toLocaleString()}</small><p className="project-note">{comment.text}</p>{comment.mentionIds.length > 0 && <small>Mentions: {comment.mentionIds.map(id => people.data?.find(person => person.id === id)?.name ?? 'Former member').join(', ')}</small>}</article>)}
    {card && canWrite && <form className="page-stack" onSubmit={event => {event.preventDefault(); void add()}}><fieldset className="page-stack" disabled={locked || busy}><label className="form-field">Comment<textarea rows={4} required maxLength={4000} value={text} onChange={event => setText(event.target.value)} /></label><p>Select @mentions to notify members using their configured communication preferences.</p><div className="project-choices">{people.data?.map(person => <label key={person.id}><input type="checkbox" checked={mentions.includes(person.id)} onChange={event => setMentions(event.target.checked ? [...mentions, person.id] : mentions.filter(id => id !== person.id))} />@{person.name}</label>)}</div><div><Button type="submit" loading={busy} disabled={!text.trim()}>Add comment</Button></div></fieldset></form>}{error && <p role="alert">{error}</p>}
  </section>
}
