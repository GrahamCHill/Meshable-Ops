import { useEffect, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Button } from '../../../components/ui'
import { getErrorMessage } from '../../../components/admin/adminUtils'
import { boardsApi, type Board, type BoardCard, type BoardInput } from './api'
import { CardEditor } from './CardEditor'
import { DevelopmentLinks } from './DevelopmentLinks'

export function BoardEditor({ id, initial, onSaved, onDirty, readOnly = false }: { id?: string; initial: BoardInput; onSaved: (board: Board) => void; onDirty: (dirty: boolean) => void; readOnly?: boolean }) {
  const [draft, setDraft] = useState(initial)
  const [dirty, setDirty] = useState(!id)
  const [editing, setEditing] = useState<BoardCard>()
  const save = useMutation({ mutationFn: () => boardsApi.save(id, draft), onSuccess: board => { setDirty(false); onDirty(false); onSaved(board) } })
  useEffect(() => { onDirty(dirty || !!editing) }, [dirty, editing, onDirty])
  useEffect(() => {
    if (!dirty && !editing) return
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = '' }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty, editing])
  const change = (next: BoardInput) => { if (readOnly) return; setDraft(next); setDirty(true); save.reset() }
  const move = (cardId: string, columnId: string) => change({ ...draft, content: { ...draft.content, cards: draft.content.cards.map(card => card.id === cardId ? { ...card, columnId } : card) } })
  return <div className="page-stack">
    <div className="inline-actions"><label className="form-field">Board name<input required maxLength={120} value={draft.name} disabled={save.isPending} onChange={event => change({ ...draft, name: event.target.value })} /></label><Button disabled={save.isPending || !dirty || !draft.name.trim() || !!editing} onClick={() => save.mutate()}>Save board</Button><span role="status">{dirty ? 'Unsaved changes' : 'Saved'}</span></div>
    {save.isError && <p role="alert" className="feedback error">{getErrorMessage(save.error, 'Unable to save board. Reload the saved board if another person has changed it.')}</p>}
    <fieldset className="kanban-editor" disabled={save.isPending || readOnly}>
      <div className="kanban-columns">
        {draft.content.columns.map(column => <section className="kanban-column page-stack" key={column.id} aria-label={column.title}
          onDragOver={event => { if (!readOnly && !save.isPending) event.preventDefault() }} onDrop={event => { event.preventDefault(); if (readOnly || save.isPending) return; const card = event.dataTransfer.getData('text/plain'); if (draft.content.cards.some(item => item.id === card)) move(card, column.id) }}>
          <label className="form-field">List name<input aria-label={`${column.title} list name`} required maxLength={80} value={column.title} onChange={event => change({ ...draft, content: { ...draft.content, columns: draft.content.columns.map(item => item.id === column.id ? { ...item, title: event.target.value } : item) } })} /></label>
          {draft.content.cards.filter(card => card.columnId === column.id).map(card => <article className="kanban-card page-stack" key={card.id} draggable={!readOnly && !save.isPending && !editing} onDragStart={event => event.dataTransfer.setData('text/plain', card.id)}>
            {editing?.id === card.id ? <CardEditor card={editing} onCancel={() => setEditing(undefined)} onSave={updated => { change({ ...draft, content: { ...draft.content, cards: draft.content.cards.map(item => item.id === updated.id ? updated : item) } }); setEditing(undefined) }} /> : <><h3>{card.title}</h3>{card.dueDate && <small>Due {card.dueDate}</small>}{card.description && <p className="kanban-description">{card.description}</p>}<label className="form-field">Move {card.title}<select value={card.columnId} onChange={event => move(card.id, event.target.value)}>{draft.content.columns.map(item => <option key={item.id} value={item.id}>{item.title}</option>)}</select></label><div className="inline-actions"><Button variant="ghost" size="sm" disabled={!!editing} onClick={() => setEditing(card)}>Edit card</Button><Button variant="ghost" size="sm" disabled={!!editing} onClick={() => change({ ...draft, content: { ...draft.content, cards: draft.content.cards.filter(item => item.id !== card.id) } })}>Remove card</Button></div></>}
          </article>)}
          {editing?.columnId === column.id && !draft.content.cards.some(card => card.id === editing.id) ? <CardEditor card={editing} onCancel={() => setEditing(undefined)} onSave={card => { change({ ...draft, content: { ...draft.content, cards: [...draft.content.cards, card] } }); setEditing(undefined) }} /> : <Button variant="ghost" disabled={!!editing || draft.content.cards.length >= 200} onClick={() => setEditing({ id: crypto.randomUUID(), columnId: column.id, title: '', description: '' })}>Add card to {column.title}</Button>}
          {draft.content.columns.length > 1 && !draft.content.cards.some(card => card.columnId === column.id) && <Button variant="ghost" size="sm" disabled={!!editing} onClick={() => change({ ...draft, content: { ...draft.content, columns: draft.content.columns.filter(item => item.id !== column.id) } })}>Remove empty list</Button>}
        </section>)}
      </div>
      <Button variant="secondary" disabled={!!editing || draft.content.columns.length >= 20} onClick={() => change({ ...draft, content: { ...draft.content, columns: [...draft.content.columns, { id: crypto.randomUUID(), title: 'New list' }] } })}>Add list</Button>
    </fieldset>
    {id && <DevelopmentLinks boardId={id} cards={draft.content.cards} locked={readOnly || dirty || !!editing || save.isPending} />}
    <p className="field-help">Drag cards between lists or use their Move selector, then save the board. Boards are private to their owner and administrators. Up to 20 lists and 200 cards per board.</p>
  </div>
}
