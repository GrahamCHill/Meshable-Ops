import { useEffect, useRef, useState } from 'react'
import { Button } from '../../../components/ui'
import type { BoardCard } from './api'
import type { BoardSprint, CardStatus } from './api'
import type { Person } from '../../../api/projects'

export function CardEditor({ card, onSave, onCancel, people = [], sprints = [] }: { card: BoardCard; onSave: (card: BoardCard) => void; onCancel: () => void; people?: Person[]; sprints?: BoardSprint[] }) {
  const dialog = useRef<HTMLDialogElement>(null)
  useEffect(() => {const element = dialog.current; element?.showModal(); return () => element?.close()}, [])
  const [title, setTitle] = useState(card.title)
  const [description, setDescription] = useState(card.description)
  const [dueDate, setDueDate] = useState(card.dueDate ?? '')
  const [fields, setFields] = useState(card)
  return <dialog ref={dialog} className="project-dialog" aria-labelledby="edit-ticket-heading" onCancel={onCancel}><form className="page-stack" onSubmit={event => { event.preventDefault(); if (title.trim()) onSave({ ...fields, title: title.trim(), description, dueDate: dueDate || null }) }}>
    <h2 id="edit-ticket-heading">{card.reference ? 'Edit ticket' : 'New ticket'}</h2>
    <small>{card.reference || 'ID assigned when saved'} · {card.id}</small>
    <label className="form-field">Card title<input autoFocus required maxLength={160} value={title} onChange={event => setTitle(event.target.value)} /></label>
    <label className="form-field">Description<textarea rows={3} maxLength={2000} value={description} onChange={event => setDescription(event.target.value)} /></label>
    <div className="project-fields">
    <label className="form-field">Due date<input type="date" min="1900-01-01" max="9999-12-31" value={dueDate} onChange={event => setDueDate(event.target.value)} /></label>
    <label className="form-field">Assignee<select value={fields.assigneeId ?? ''} onChange={event => setFields({...fields, assigneeId: event.target.value || null})}><option value="">Unassigned</option>{people.map(person => <option key={person.id} value={person.id}>{person.name}</option>)}</select></label>
    <label className="form-field">Status<select value={fields.status ?? 'TODO'} onChange={event => setFields({...fields, status: event.target.value as CardStatus, completionDate: event.target.value === 'DONE' ? fields.completionDate : null})}><option value="TODO">To do</option><option value="IN_PROGRESS">In progress</option><option value="DONE">Done</option></select></label>
    <label className="form-field">Start date<input type="date" value={fields.startDate ?? ''} onChange={event => setFields({...fields, startDate: event.target.value || null})} /></label>
    <label className="form-field">Completion date<input type="date" disabled={fields.status !== 'DONE'} value={fields.completionDate ?? ''} onChange={event => setFields({...fields, completionDate: event.target.value || null})} /><small>Filled automatically when completed if left blank.</small></label>
    <label className="form-field">Sprint<select value={fields.sprintId ?? ''} onChange={event => setFields({...fields, sprintId: event.target.value || null})}><option value="">No sprint</option>{sprints.map(sprint => <option key={sprint.id} value={sprint.id}>{sprint.name}</option>)}</select></label>
    <label className="form-field">Story points<input type="number" min={0} max={1000} step={0.5} value={fields.storyPoints ?? ''} onChange={event => setFields({...fields, storyPoints: event.target.value === '' ? null : Number(event.target.value)})} /></label>
    </div>
    <fieldset className="project-choices"><legend>Reviewers</legend>{people.map(person => <label key={person.id}><input type="checkbox" checked={fields.reviewerIds?.includes(person.id) ?? false} onChange={event => setFields({...fields, reviewerIds: event.target.checked ? [...(fields.reviewerIds ?? []), person.id] : fields.reviewerIds?.filter(id => id !== person.id)})} /> {person.name}</label>)}</fieldset>
    <label><input type="checkbox" checked={fields.blocked ?? false} onChange={event => setFields({...fields, blocked: event.target.checked})} />Blocked</label>
    <div className="inline-actions"><Button type="submit" disabled={!title.trim()}>Apply card</Button><Button type="button" variant="ghost" onClick={onCancel}>Cancel</Button></div>
  </form></dialog>
}
