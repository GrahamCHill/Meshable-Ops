import {useState} from 'react'
import {ChevronDown, ChevronRight, GripVertical, Plus} from 'lucide-react'
import type {Person} from '../../../api/projects'
import type {Board, BoardCard} from './api'
import {rankBefore, ticketGroups, type BoardGroup} from './boardView'
import {TicketTile} from './TicketTile'
import {QuickCreate} from './QuickCreate'

export function BoardSurface({board, cards, people, group, busy, readOnly, onOpen, onMove, onColumns, onCreate, onSubtask}: Readonly<{
  board: Board; cards: BoardCard[]; people: Person[]; group: BoardGroup; busy: boolean; readOnly: boolean;
  onOpen: (card: BoardCard) => void; onMove: (card: string, column: string, before?: string) => void;
  onColumns: (from: string, target: string) => void;
  onCreate: (column: string, title: string, type: BoardCard['type'], groupId: string) => Promise<void>;
  onSubtask: (parent: BoardCard) => void
}>) {
  const [drag, setDrag] = useState<{kind: 'ticket' | 'column'; id: string}>()
  const [drop, setDrop] = useState<{id: string; after: boolean}>()
  const [expanded, setExpanded] = useState<string[]>([])
  const [collapsed, setCollapsed] = useState<string[]>([])
  const [creating, setCreating] = useState('')
  const groups = ticketGroups(cards, group, people, board.content.epics)
  const lanes = groups.length ? groups : [{id: 'empty', label: 'No matching tickets', cards: []}]
  const canManage = board.canManage && !readOnly
  const allowed = (id: string, column: string) => !readOnly && !busy && !!board.moves[id]?.includes(column)
  const stopDrag = () => {setDrag(undefined); setDrop(undefined)}
  const toggle = (id: string) => setExpanded(value => value.includes(id) ? value.filter(item => item !== id) : [...value, id])
  return <div className="kanban-board-surface">
    <p className="kanban-sr-only">Drag tickets between columns and above or below other tickets. You can also use each ticket's move up and down buttons, or open it to change column. Managers can drag column headings.</p>
    {lanes.map(lane => <section key={lane.id} className="kanban-swimlane" aria-label={lane.label || 'Board tickets'}>
      {group !== 'none' && <button type="button" className="kanban-lane-heading" aria-expanded={!collapsed.includes(lane.id)} onClick={() => setCollapsed(value => value.includes(lane.id) ? value.filter(id => id !== lane.id) : [...value, lane.id])}>{collapsed.includes(lane.id) ? <ChevronRight size={16}/> : <ChevronDown size={16}/>} {lane.label} <span>{lane.cards.length}</span></button>}
      {!collapsed.includes(lane.id) && <div className="kanban-columns">
        {board.content.columns.map(column => {
          const sameLane = drag?.kind === 'ticket' && lane.cards.some(card => card.id === drag.id)
          const canDrop = drag?.kind === 'column' ? canManage && !busy : !!drag && sameLane && allowed(drag.id, column.id)
          const columnCards = lane.cards.filter(card => card.columnId === column.id)
          const key = lane.id + ':' + column.id
          return <section key={column.id} className={'kanban-column' + (canDrop ? ' can-drop' : '')} aria-label={column.title}
            onDragEnter={event => {if (canDrop) {event.preventDefault(); event.dataTransfer.dropEffect = 'move'}}}
            onDragOver={event => {if (canDrop) {event.preventDefault(); event.dataTransfer.dropEffect = 'move'}}}
            onDrop={event => {event.preventDefault(); if (!canDrop || !drag) return; if (drag.kind === 'column') onColumns(drag.id, column.id); else onMove(drag.id, column.id); stopDrag()}}>
            <header className="kanban-column-heading" draggable={canManage && !busy} onDragStart={event => {event.dataTransfer.setData('text/plain', column.id); event.dataTransfer.effectAllowed = 'move'; setDrag({kind: 'column', id: column.id})}} onDragEnd={stopDrag}>
              <h2>{column.title}{column.startsWork&&<small className="kanban-completed-column" title="Entering this column records a missing start date">Work started</small>}{column.status==='DONE'&&<small className="kanban-completed-column" title="Tickets in this column are automatically completed">Completed</small>}</h2><span aria-label="Ticket count">{columnCards.length}</span>{canManage && <GripVertical size={13} className="kanban-column-grip" aria-hidden="true"/>}
            </header>
            <div className="kanban-column-tickets">
              {columnCards.map((card, index) => <TicketTile key={card.id} card={card} columns={board.content.columns} epic={board.content.epics?.find(epic => epic.id === card.epicId)} people={people}
                storyPoints={board.estimation?.storyPoints??true} tshirtSizes={board.estimation?.tshirtSizes??false}
                subtasks={board.content.cards.filter(item => item.parentId === card.id)} expanded={expanded.includes(card.id)} onToggle={() => toggle(card.id)} onOpenCard={onOpen}
                onCreateSubtask={board.canCreateTickets && !readOnly && board.content.cards.length < 200 ? () => {setExpanded(value => [...new Set([...value, card.id])]); onSubtask(card)} : undefined}
                disabled={busy} draggable={!readOnly && !busy && !!board.moves[card.id]?.length} onOpen={() => onOpen(card)}
                onDragStart={event => {event.stopPropagation(); event.dataTransfer.setData('text/plain', card.id); event.dataTransfer.effectAllowed = 'move'; setDrag({kind: 'ticket', id: card.id})}} onDragEnd={stopDrag}
                dropPosition={drop?.id === card.id && drag?.id !== card.id ? (drop.after ? 'after' : 'before') : undefined}
                onDragOver={event => {if (drag?.kind === 'ticket' && sameLane && allowed(drag.id, column.id) && drag.id !== card.id) {event.preventDefault(); event.stopPropagation(); const rect = event.currentTarget.getBoundingClientRect(); setDrop({id: card.id, after: event.clientY > rect.top + rect.height / 2})}}}
                onDrop={event => {if (drag?.kind === 'ticket' && sameLane && allowed(drag.id, column.id)) {event.preventDefault(); event.stopPropagation(); if (drag.id !== card.id) onMove(drag.id, column.id, rankBefore(board.content.cards, drag.id, card.id, !!(drop?.id === card.id && drop.after))); stopDrag()}}}
                onRank={direction => {const target = columnCards[index + direction]; if (target && allowed(card.id, column.id)) onMove(card.id, column.id, rankBefore(board.content.cards, card.id, target.id, direction > 0))}}
                canRankUp={index > 0 && allowed(card.id, column.id)} canRankDown={index < columnCards.length - 1 && allowed(card.id, column.id)}
              />)}
              {creating === key ? <QuickCreate draftKey={'quick-ticket:' + board.id + ':' + key} busy={busy} onCancel={() => setCreating('')} onCreate={async (title, type) => {await onCreate(column.id, title, type, lane.id); setCreating('')}}/>
                : board.canCreateTickets && !readOnly && <button type="button" className="kanban-add-ticket" disabled={busy || board.content.cards.length >= 200} onClick={() => setCreating(key)}><Plus size={15}/> Create ticket</button>}
            </div>
          </section>
        })}
        {canManage && <button type="button" className="kanban-add-column" disabled={busy} aria-label="Add column" onClick={() => onColumns('', '')}><Plus size={19}/></button>}
      </div>}
    </section>)}
  </div>
}
