import {Button} from '../../../components/ui'
import {Modal} from '../../../components/ui/Modal'
import {usePageDraft} from '../../../hooks/usePageDraft'
import type {BoardCard, BoardColumn, BoardEpic, BoardSprint} from './api'
import type {Person} from '../../../api/projects'
import {CardDiscussion} from './CardDiscussion'

export function CardEditor({boardId, card, columns, epics = [], cards, allowedColumns, onSave, onCancel, onRemove, people = [], sprints = [], busy = false, readOnly = false, error}: {
  boardId?: string; card: BoardCard; columns: BoardColumn[]; epics?: BoardEpic[]; cards: BoardCard[]; allowedColumns?: string[];
  onSave: (card: BoardCard) => void; onCancel: () => void; onRemove?: () => void; people?: Person[]; sprints?: BoardSprint[]; busy?: boolean; readOnly?: boolean; error?: string
}) {
  const key = 'ticket:' + (boardId ?? 'new') + ':' + card.id
  const [draft, setDraft] = usePageDraft(key, card)
  const [tags, setTags] = usePageDraft(key + ':tags', (card.tags ?? []).join(', '))
  const change = (fields: Partial<BoardCard>) => setDraft(value => ({...value, ...fields}))
  const tagValues = [...new Set(tags.split(',').map(value => value.trim()).filter(Boolean))]
  const tagError = tagValues.length > 20 || tagValues.some(value => value.length > 40)
  return <Modal title={card.reference || 'New ticket'} onClose={onCancel} busy={busy}>
    <form className="page-stack" onSubmit={event => {event.preventDefault(); if (!readOnly && !tagError && draft.title.trim()) onSave({...draft, title: draft.title.trim(), tags: tagValues})}}>
      <fieldset className="page-stack" disabled={busy || readOnly}>
        <label className="form-field">Ticket title<input autoFocus required maxLength={160} value={draft.title} onChange={event => change({title:event.target.value})}/></label>
        <label className="form-field">Description<textarea rows={5} maxLength={2000} value={draft.description} onChange={event => change({description:event.target.value})}/></label>
        <div className="project-fields">
          <label className="form-field">Column<select value={draft.columnId} onChange={event => {const column = columns.find(item => item.id === event.target.value); change({columnId:event.target.value, status:column?.status ?? draft.status})}}>{columns.map(column => <option key={column.id} value={column.id} disabled={column.id !== card.columnId && allowedColumns !== undefined && !allowedColumns.includes(column.id)}>{column.title}</option>)}</select></label>
          <label className="form-field">Type<select value={draft.type ?? 'TASK'} onChange={event => change({type:event.target.value as BoardCard['type'], parentId:null})}>{['TASK','STORY','BUG','SUBTASK'].map(value => <option key={value} value={value}>{value.toLowerCase()}</option>)}</select></label>
          <label className="form-field">Priority<select value={draft.priority ?? 'MEDIUM'} onChange={event => change({priority:event.target.value as BoardCard['priority']})}>{['LOW','MEDIUM','HIGH','URGENT'].map(value => <option key={value} value={value}>{value.toLowerCase()}</option>)}</select></label>
          <label className="form-field">Assignee<select value={draft.assigneeId ?? ''} onChange={event => change({assigneeId:event.target.value || null})}><option value="">Unassigned</option>{people.map(person => <option key={person.id} value={person.id}>{person.name}</option>)}</select></label>
          <label className="form-field">Epic<select value={draft.epicId ?? ''} onChange={event => change({epicId:event.target.value || null})}><option value="">No epic</option>{epics.map(epic => <option key={epic.id} value={epic.id}>{epic.name}</option>)}</select></label>
          <label className="form-field">Sprint<select value={draft.sprintId ?? ''} onChange={event => change({sprintId:event.target.value || null})}><option value="">Backlog / no sprint</option>{sprints.map(sprint => <option key={sprint.id} value={sprint.id}>{sprint.name}</option>)}</select></label>
          <label className="form-field">Story points<input type="number" min={0} max={1000} step={0.5} value={draft.storyPoints ?? ''} onChange={event => change({storyPoints:event.target.value === '' ? null : Number(event.target.value)})}/></label>
          <label className="form-field">Start date<input type="date" min="1900-01-01" max="9999-12-31" value={draft.startDate ?? ''} onChange={event => change({startDate:event.target.value || null})}/></label>
          <label className="form-field">Due date<input type="date" min={draft.startDate || '1900-01-01'} max="9999-12-31" value={draft.dueDate ?? ''} onChange={event => change({dueDate:event.target.value || null})}/></label>
          <label className="form-field">Completion date<input type="date" disabled={(columns.find(column => column.id === draft.columnId)?.status ?? draft.status) !== 'DONE'} min={draft.startDate || '1900-01-01'} max="9999-12-31" value={draft.completionDate ?? ''} onChange={event => change({completionDate:event.target.value || null})}/></label>
        </div>
        {draft.type === 'SUBTASK' && <label className="form-field">Parent ticket<select required value={draft.parentId ?? ''} onChange={event => change({parentId:event.target.value || null})}><option value="">Choose a parent ticket</option>{cards.filter(item => item.id !== card.id && !item.parentId).map(item => <option key={item.id} value={item.id}>{item.reference} {item.title}</option>)}</select></label>}
        <label className="form-field">Tags<input value={tags} onChange={event => setTags(event.target.value)} placeholder="frontend, customer-request, release"/><small>Comma-separated, up to 20 tags of 40 characters each.</small></label>
        {tagError && <p role="alert">Use up to 20 tags with at most 40 characters each.</p>}
        <fieldset className="project-choices"><legend>Reviewers</legend>{people.map(person => <label key={person.id}><input type="checkbox" checked={draft.reviewerIds?.includes(person.id) ?? false} onChange={event => change({reviewerIds:event.target.checked ? [...(draft.reviewerIds ?? []),person.id] : draft.reviewerIds?.filter(id => id !== person.id)})}/>{person.name}</label>)}</fieldset>
        <label><input type="checkbox" checked={draft.blocked ?? false} onChange={event => change({blocked:event.target.checked})}/> Blocked</label>
      </fieldset>
      {error && <p role="alert">{error}</p>}
      <div className="inline-actions">{!readOnly && <Button type="submit" loading={busy} disabled={!draft.title.trim() || tagError || draft.type === 'SUBTASK' && !draft.parentId}>Save ticket</Button>}<Button type="button" variant="ghost" disabled={busy} onClick={onCancel}>Close</Button>{onRemove && !readOnly && <Button type="button" variant="ghost" disabled={busy || cards.some(item => item.parentId === card.id)} onClick={onRemove}>Delete ticket</Button>}</div>
      <small>Draft fields are kept until you leave this page. Saved tickets receive a permanent reference.</small>
    </form>
    {boardId && card.reference && <CardDiscussion key={card.id} boardId={boardId} cards={[card]} selectedCardId={card.id} locked={busy}/>}
  </Modal>
}
