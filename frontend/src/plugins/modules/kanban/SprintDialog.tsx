import {useState} from 'react'
import {Button} from '../../../components/ui'
import {Modal} from '../../../components/ui/Modal'
import {usePageDraft} from '../../../hooks/usePageDraft'
import type {Board, BoardSprint} from './api'

export type SprintDialogAction = {kind: 'create'} | {kind: 'start' | 'complete' | 'edit'; sprint: BoardSprint}

export function SprintDialog({board, action, busy, error, onClose, onCreate, onStart, onComplete, onDelete}: Readonly<{
  board: Board; action: SprintDialogAction; busy: boolean; error?: string; onClose: () => void;
  onCreate: (sprint: BoardSprint) => Promise<void>; onStart: (id: string) => Promise<void>;
  onDelete: (id:string)=>Promise<void>;
  onComplete: (id: string, destination: string | null) => Promise<void>
}>) {
  const [confirmDelete,setConfirmDelete]=useState(false)
  const [destination, setDestination] = useState('')
  const existing = action.kind === 'edit' ? action.sprint : undefined
  const editing = action.kind === 'create' || action.kind === 'edit'
  const [draft, setDraft, clear] = usePageDraft('sprint:' + board.id + ':' + (existing?.id ?? 'new'), () => {
    const start = new Date()
    const end = new Date(start)
    end.setDate(end.getDate() + 13)
    return {name: existing?.name ?? 'Sprint ' + ((board.content.sprints?.length ?? 0) + 1), goal: existing?.goal ?? '', startDate: existing?.startDate ?? start.toLocaleDateString('en-CA'), endDate: existing?.endDate ?? end.toLocaleDateString('en-CA')}
  })
  const cards = action.kind === 'create' ? [] : board.content.cards.filter(card => !card.parentId && card.sprintId === action.sprint.id)
  const complete = cards.filter(card => card.status === 'DONE').length
  const title = action.kind === 'create' ? 'Create sprint' : (action.kind === 'edit' ? 'Edit ' : action.kind === 'start' ? 'Start ' : 'Complete ') + action.sprint.name
  const submit = async () => {
    if (editing) {await onCreate({...draft, id: existing?.id ?? crypto.randomUUID(), status: existing?.status ?? 'PLANNED'}); clear()}
    else if (action.kind === 'start') await onStart(action.sprint.id)
    else await onComplete(action.sprint.id, destination || null)
    onClose()
  }
  return <Modal title={title} onClose={onClose} busy={busy}>
    <form className="page-stack" onSubmit={event => {event.preventDefault(); if (!busy) void submit().catch(() => undefined)}}>
      <fieldset className="page-stack" disabled={busy}>
        {editing ? <>
          <label className="form-field">Sprint name<input autoFocus required maxLength={100} value={draft.name} onChange={event => setDraft({...draft, name: event.target.value})}/></label>
          <label className="form-field">Sprint goal<textarea maxLength={500} rows={3} value={draft.goal} onChange={event => setDraft({...draft, goal: event.target.value})}/></label>
          <div className="project-fields"><label className="form-field">Start date<input type="date" min="1900-01-01" max="9999-12-31" required value={draft.startDate} onChange={event => setDraft({...draft, startDate: event.target.value})}/></label><label className="form-field">End date<input type="date" min={draft.startDate} max="9999-12-31" required value={draft.endDate} onChange={event => setDraft({...draft, endDate: event.target.value})}/></label></div>
        </> : <>
          <p>{action.sprint.startDate} – {action.sprint.endDate}</p>
          {action.sprint.goal && <p>{action.sprint.goal}</p>}
          {action.kind === 'start' ? <p>{cards.length} tickets will appear on the active sprint board. You can keep planning future work in Backlog.</p> : <>
            <div className="kanban-sprint-totals"><span><strong>{complete}</strong> completed</span><span><strong>{cards.length - complete}</strong> unfinished</span></div>
            <p>Completed work stays in this sprint. Unfinished tickets and all their subtasks move together.</p>
            {cards.length > complete && <label className="form-field">Move unfinished work to<select value={destination} onChange={event => setDestination(event.target.value)}><option value="">Backlog</option>{board.content.sprints?.filter(sprint => sprint.id !== action.sprint.id && (sprint.status ?? 'PLANNED') === 'PLANNED').map(sprint => <option key={sprint.id} value={sprint.id}>{sprint.name}</option>)}</select></label>}
          </>}
        </>}
      </fieldset>
      {existing&&(existing.status??'PLANNED')==='PLANNED'&&<div className="page-stack">{confirmDelete?<><p>Delete this planned sprint? Tickets return to the backlog and are retained.</p><div className="inline-actions"><Button type="button" variant="danger" disabled={busy} onClick={()=>void onDelete(existing.id).then(onClose).catch(()=>undefined)}>Confirm sprint deletion</Button><Button type="button" variant="ghost" disabled={busy} onClick={()=>setConfirmDelete(false)}>Keep sprint</Button></div></>:<Button type="button" variant="danger" disabled={busy} onClick={()=>setConfirmDelete(true)}>Delete planned sprint</Button>}</div>}
      {error && <p role="alert">{error}</p>}
      <div className="inline-actions"><Button type="submit" loading={busy} disabled={editing && (!draft.name.trim() || draft.endDate < draft.startDate)}>{action.kind === 'edit' ? 'Save sprint' : action.kind === 'create' ? 'Create sprint' : action.kind === 'start' ? 'Start sprint' : 'Complete sprint'}</Button><Button type="button" variant="ghost" disabled={busy} onClick={onClose}>Cancel</Button></div>
    </form>
  </Modal>
}
