import {useState} from 'react'
import {ChevronDown, ChevronRight, GripVertical, Plus} from 'lucide-react'
import {Button} from '../../../components/ui'
import type {Person} from '../../../api/projects'
import type {Board, BoardCard} from './api'
import {personInitials} from './boardView'
import {TicketTypeIcon} from './TicketTile'
import type {SprintDialogAction} from './SprintDialog'

export function BacklogView({board, cards, people, busy, readOnly, onOpen, onPlan, onSprint, onCreate}: Readonly<{
  board: Board; cards: BoardCard[]; people: Person[]; busy: boolean; readOnly: boolean;
  onOpen: (card: BoardCard) => void; onPlan: (card: string, sprint: string | null, before?: string) => void;
  onSprint: (action: SprintDialogAction) => void; onCreate: (sprint: string | null) => void
}>) {
  const [drag, setDrag] = useState<string>()
  const [over, setOver] = useState<string>()
  const [collapsed, setCollapsed] = useState<string[]>([])
  const active = board.content.sprints?.find(sprint => sprint.status === 'ACTIVE')
  const displayed = board.content.sprints?.filter(sprint => sprint.status !== 'COMPLETED' || cards.some(card => card.sprintId === sprint.id)) ?? []
  const groups = [...displayed.map(sprint => ({id: sprint.id, name: sprint.name, sprint})), {id: '', name: 'Backlog', sprint: undefined}]
  const editable = !readOnly && !busy
  return <div className="kanban-backlog">
    <p className="field-help">Drag tickets between sprints and the backlog to plan work, or use each ticket's sprint selector. Subtasks follow their parent.</p>
    {groups.map(section => {
      const tickets = cards.filter(card => !card.parentId && (card.sprintId || '') === section.id)
      const all = board.content.cards.filter(card => !card.parentId && (card.sprintId || '') === section.id)
      const canPlan = editable && section.sprint?.status !== 'COMPLETED'
      return <section key={section.id} className={'kanban-backlog-section' + (over === section.id ? ' can-drop' : '')}
        onDragEnter={event => {if (canPlan && drag) {event.preventDefault(); setOver(section.id)}}}
        onDragOver={event => {if (canPlan && drag) {event.preventDefault(); setOver(section.id)}}}
        onDrop={event => {if (canPlan && drag) {event.preventDefault(); onPlan(drag, section.id || null); setDrag(undefined); setOver(undefined)}}}>
        <header className="kanban-backlog-heading">
          <button type="button" className="kanban-backlog-toggle" aria-expanded={!collapsed.includes(section.id)} onClick={() => setCollapsed(value => value.includes(section.id) ? value.filter(id => id !== section.id) : [...value, section.id])}>{collapsed.includes(section.id) ? <ChevronRight size={16}/> : <ChevronDown size={16}/>}<strong>{section.name}</strong><span>{tickets.length} tickets</span></button>
          {section.sprint && <><span className={'kanban-sprint-state state-' + (section.sprint.status ?? 'PLANNED').toLowerCase()}>{(section.sprint.status ?? 'PLANNED').toLowerCase()}</span><span className="kanban-sprint-dates">{section.sprint.startDate} – {section.sprint.endDate}</span></>}
          <span className="kanban-points" title="Story points">{tickets.reduce((sum, card) => sum + (card.storyPoints ?? 0), 0)} points</span>
          {board.canManage && !readOnly && section.sprint && section.sprint.status !== 'COMPLETED' && <Button size="sm" variant="secondary" disabled={busy || section.sprint.status !== 'ACTIVE' && (!!active || !all.length)} onClick={() => onSprint({kind: section.sprint!.status === 'ACTIVE' ? 'complete' : 'start', sprint: section.sprint!})}>{section.sprint.status === 'ACTIVE' ? 'Complete sprint' : 'Start sprint'}</Button>}
          {board.canManage&&!readOnly&&section.sprint&&<Button type="button" size="sm" variant="ghost" disabled={busy} onClick={()=>onSprint({kind:'edit',sprint:section.sprint!})}>Edit sprint</Button>}
        </header>
        {!collapsed.includes(section.id) && <>
          {section.sprint?.goal && <p className="kanban-sprint-goal">{section.sprint.goal}</p>}
          <div className="kanban-backlog-rows">{tickets.map(card => <div key={card.id} className="kanban-backlog-row" draggable={canPlan}
            onDragStart={event => {event.dataTransfer.setData('text/plain', card.id); event.dataTransfer.effectAllowed = 'move'; setDrag(card.id)}} onDragEnd={() => {setDrag(undefined); setOver(undefined)}}
            onDragEnter={event => {if (canPlan && drag && drag !== card.id) {event.preventDefault(); event.stopPropagation()}}}
            onDragOver={event => {if (canPlan && drag && drag !== card.id) {event.preventDefault(); event.stopPropagation()}}}
            onDrop={event => {if (canPlan && drag) {event.preventDefault(); event.stopPropagation(); if (drag !== card.id) onPlan(drag, section.id || null, card.id); setDrag(undefined); setOver(undefined)}}}>
            <GripVertical size={13} className="kanban-backlog-grip"/><TicketTypeIcon type={card.type}/>
            <button type="button" className="kanban-backlog-ticket" disabled={busy} onClick={() => onOpen(card)}><span className="kanban-reference">{card.reference}</span><span>{card.title}</span></button>
            <span className="kanban-backlog-status">{board.content.columns.find(column => column.id === card.columnId)?.title}</span>
            <select aria-label={`Plan ${card.reference} in sprint`} disabled={!canPlan} value={card.sprintId || ''} onChange={event => onPlan(card.id, event.target.value || null)}><option value="">Backlog</option>{displayed.map(sprint => <option key={sprint.id} value={sprint.id} disabled={sprint.status === 'COMPLETED'}>{sprint.name}</option>)}</select>
            {card.storyPoints != null && <span className="kanban-points">{card.storyPoints}</span>}
            <span className="kanban-avatar" title={people.find(person => person.id === card.assigneeId)?.name ?? 'Unassigned'}>{personInitials(people.find(person => person.id === card.assigneeId)?.name ?? '–')}</span>
          </div>)}</div>
          {!tickets.length && <p className="kanban-backlog-empty">{section.sprint ? 'Drag tickets here to plan this sprint.' : 'No tickets in the backlog.'}</p>}
          {!readOnly && section.sprint?.status !== 'COMPLETED' && <button type="button" className="kanban-add-ticket" disabled={busy || board.content.cards.length >= 200} onClick={() => onCreate(section.id || null)}><Plus size={15}/> Create ticket</button>}
        </>}
      </section>
    })}
    {board.canManage && !readOnly && <div><Button variant="secondary" disabled={busy || (board.content.sprints?.length ?? 0) >= 100} onClick={() => onSprint({kind: 'create'})}><Plus size={15}/> Create sprint</Button></div>}
  </div>
}
