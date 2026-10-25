import {Bug, Check, ChevronDown, ChevronRight, ChevronUp, Diamond, GitBranch, Plus, SquareCheck, TriangleAlert} from 'lucide-react'
import type {BoardCard, BoardColumn, BoardEpic} from './api'
import type {Person} from '../../../api/projects'
import {TicketPeople} from './TicketPeople'

export function TicketTypeIcon({type}: Readonly<{type: BoardCard['type']}>) {
  if (type === 'BUG') return <Bug size={15} className="kanban-type-icon type-bug" aria-label="Bug"/>
  if (type === 'STORY') return <Diamond size={15} className="kanban-type-icon type-story" aria-label="Story"/>
  if (type === 'SUBTASK') return <GitBranch size={15} className="kanban-type-icon type-subtask" aria-label="Subtask"/>
  return <SquareCheck size={15} className="kanban-type-icon type-task" aria-label="Task"/>
}

export function TicketTile({card, epic, people, subtasks, columns, expanded, onToggle, onOpenCard, onCreateSubtask,
  disabled, draggable, onOpen, onDragStart, onDragEnd, onDrop, onDragOver, dropPosition, onRank, canRankUp, canRankDown, storyPoints=true, tshirtSizes=false}: Readonly<{
  card: BoardCard; epic?: BoardEpic; people: Person[]; subtasks: BoardCard[]; columns: BoardColumn[];
  expanded: boolean; onToggle: () => void; onOpenCard: (card: BoardCard) => void; onCreateSubtask?: () => void;
  disabled: boolean; draggable: boolean; onOpen: () => void; dropPosition?: 'before' | 'after';
  onDragStart: (event: React.DragEvent) => void; onDragEnd: () => void;
  onDrop: (event: React.DragEvent) => void; onDragOver: (event: React.DragEvent) => void;
  onRank: (direction: number) => void; canRankUp: boolean; canRankDown: boolean
  storyPoints?: boolean; tshirtSizes?: boolean
}>) {
  const today = new Date().toLocaleDateString('en-CA')
  const overdue = card.status !== 'DONE' && !!card.dueDate && card.dueDate < today
  const rgb = epic?.color.match(/[a-f0-9]{2}/gi)?.map(value => Number.parseInt(value, 16))
  const epicText = rgb && (rgb[0] * .299 + rgb[1] * .587 + rgb[2] * .114) > 160 ? '#111' : '#fff'
  return <article className={['kanban-ticket', card.blocked ? 'is-blocked' : '', dropPosition ? 'drop-' + dropPosition : ''].join(' ')}
    draggable={draggable} onDragStart={onDragStart} onDragEnd={onDragEnd} onDragEnter={onDragOver} onDragOver={onDragOver} onDrop={onDrop}>
    <button type="button" className="kanban-ticket-open" disabled={disabled} onClick={onOpen} aria-label={`Open ${card.reference || 'ticket'} ${card.title}`}>
      <span className="kanban-ticket-title">{card.title}</span>
      {(epic || !!card.tags?.length) && <span className="kanban-ticket-labels">{epic && <span className="kanban-epic-label" style={{backgroundColor: epic.color, color: epicText}}>{epic.name}</span>}{card.tags?.slice(0, 3).map(tag => <span className="kanban-tag" key={tag}>{tag}</span>)}{(card.tags?.length ?? 0) > 3 && <span className="kanban-tag">+{card.tags!.length - 3}</span>}</span>}
      {card.dueDate && <span className={overdue ? 'kanban-overdue' : 'kanban-due'}>{new Date(card.dueDate + 'T12:00:00').toLocaleDateString(undefined, {month: 'short', day: 'numeric', year: 'numeric'})}{overdue && <TriangleAlert size={13} aria-label="Overdue"/>}</span>}
      {card.blocked && <span className="kanban-overdue">Flagged · Blocked</span>}
      <TicketPeople card={card} people={people} compact/>
      <span className="kanban-ticket-footer"><TicketTypeIcon type={card.type}/><span className="kanban-reference">{card.reference || 'Unsaved'}</span><span className="kanban-ticket-spacer"/>{card.priority && card.priority !== 'MEDIUM' && <span className={'kanban-priority priority-' + card.priority.toLowerCase()}>{card.priority.toLowerCase()}</span>}{tshirtSizes && card.tshirtSize && <span className="kanban-points" aria-label={"T-shirt size " + card.tshirtSize}>{card.tshirtSize}</span>}{storyPoints && card.storyPoints != null && <span className="kanban-points" aria-label={`${card.storyPoints} story points`}>{card.storyPoints}</span>}{card.status === 'DONE' && <Check size={14} className="kanban-done-mark" aria-label="Done"/>}</span>
    </button>
    {!!subtasks.length && <div className="kanban-subtask-footer">
      <button type="button" aria-expanded={expanded} disabled={disabled} onClick={onToggle}><GitBranch size={15}/> Subtasks <span>{subtasks.filter(item => item.status === 'DONE').length}/{subtasks.length}</span>{expanded ? <ChevronDown size={15}/> : <ChevronRight size={15}/>}</button>
      {onCreateSubtask && <button type="button" disabled={disabled} title="Create subtask" aria-label={`Create subtask for ${card.reference}`} onClick={onCreateSubtask}><Plus size={14}/></button>}
    </div>}
    {expanded && <div className="kanban-subtask-list">{subtasks.map(child => <button type="button" key={child.id} disabled={disabled} onClick={() => onOpenCard(child)}><TicketTypeIcon type="SUBTASK"/><span><strong>{child.reference}</strong> {child.title}<small>{columns.find(column => column.id === child.columnId)?.title}</small></span>{child.status === 'DONE' && <Check size={13} className="kanban-done-mark" aria-label="Done"/>}</button>)}{!subtasks.length && <small>No subtasks yet.</small>}</div>}
    {draggable && <div className="kanban-rank-controls"><button type="button" aria-label={`Move ${card.reference} up`} disabled={!canRankUp || disabled} onClick={() => onRank(-1)}><ChevronUp size={13}/></button><button type="button" aria-label={`Move ${card.reference} down`} disabled={!canRankDown || disabled} onClick={() => onRank(1)}><ChevronDown size={13}/></button></div>}
  </article>
}
