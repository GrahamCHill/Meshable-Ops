import type {BoardCard,BoardEpic} from './api'
import type {Person} from '../../../api/projects'

export function TicketTile({card,epic,person,subtasks,disabled,draggable,onOpen,onDragStart,onDragEnd,onDrop,onDragOver}: {
  card:BoardCard;epic?:BoardEpic;person?:Person;subtasks:BoardCard[];disabled:boolean;draggable:boolean;onOpen:()=>void;
  onDragStart:(event:React.DragEvent)=>void;onDragEnd:()=>void;onDrop:(event:React.DragEvent)=>void;onDragOver:(event:React.DragEvent)=>void
}) {
  const overdue=card.status!=='DONE'&&!!card.dueDate&&card.dueDate<new Date().toISOString().slice(0,10)
  const initials=person?.name.split(/\s+/).slice(0,2).map(value=>value[0]).join('').toUpperCase()??'–'
  return <article className={'kanban-ticket'+(card.blocked?' is-blocked':'')} draggable={draggable} onDragStart={onDragStart} onDragEnd={onDragEnd} onDragOver={onDragOver} onDrop={onDrop}>
    <button type="button" className="kanban-ticket-open" disabled={disabled} onClick={onOpen} aria-label={`Open ${card.reference||'ticket'} ${card.title}`}>
      <span className="kanban-ticket-title">{card.title}</span>
      {(epic||!!card.tags?.length)&&<span className="kanban-ticket-labels">{epic&&<span className="kanban-epic-label" style={{color:epic.color}}>{epic.name}</span>}{card.tags?.map(tag=><span className="kanban-tag" key={tag}>{tag}</span>)}</span>}
      {card.dueDate&&<span className={overdue?'kanban-overdue':'kanban-due'}>{overdue?'Overdue · ':''}{new Date(card.dueDate+'T12:00:00').toLocaleDateString()}</span>}
      {card.blocked&&<span className="kanban-overdue">Blocked</span>}
      <span className="kanban-ticket-footer"><span className={'kanban-ticket-type type-'+(card.type??'TASK').toLowerCase()} title={card.type??'Task'}>{card.type==='BUG'?'B':card.type==='STORY'?'S':card.type==='SUBTASK'?'↳':'T'}</span><span className={card.status==='DONE'?'kanban-reference done':'kanban-reference'}>{card.reference||'Unsaved'}</span><span className="kanban-ticket-spacer"/>{card.priority&&card.priority!=='MEDIUM'&&<span className={'kanban-priority priority-'+card.priority.toLowerCase()}>{card.priority.toLowerCase()}</span>}{card.storyPoints!=null&&<span className="kanban-points" aria-label={`${card.storyPoints} story points`}>{card.storyPoints}</span>}<span className="kanban-avatar" title={person?.name??'Unassigned'} aria-label={person?.name??'Unassigned'}>{initials}</span></span>
      {!!subtasks.length&&<span className="kanban-subtasks">Subtasks {subtasks.filter(item=>item.status==='DONE').length}/{subtasks.length}</span>}
    </button>
  </article>
}
