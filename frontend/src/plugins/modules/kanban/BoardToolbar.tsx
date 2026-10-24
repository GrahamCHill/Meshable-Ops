import {useState} from 'react'
import {Filter, Layers, Search, UserRound} from 'lucide-react'
import {Button} from '../../../components/ui'
import type {Person} from '../../../api/projects'
import type {BoardInput} from './api'
import {filterCount, personInitials, type BoardFilters, type BoardGroup} from './boardView'

export function BoardToolbar({content, people, filters, onChange, group, onGroup, count, showGrouping = true}: Readonly<{
  content: BoardInput['content']; people: Person[]; filters: BoardFilters; onChange: (value: BoardFilters) => void;
  group: BoardGroup; onGroup: (value: BoardGroup) => void; count: number; showGrouping?: boolean
}>) {
  const [expanded, setExpanded] = useState(false)
  const patch = (value: Partial<BoardFilters>) => onChange({...filters, ...value})
  const togglePerson = (id: string) => patch({assignees: filters.assignees.includes(id) ? filters.assignees.filter(item => item !== id) : [...filters.assignees, id]})
  const total = filterCount(filters)
  const active = content.sprints?.find(sprint => sprint.status === 'ACTIVE')
  return <div className="kanban-toolbar-wrap">
    <div className="kanban-toolbar">
      <label className="kanban-search"><Search size={16}/><input type="search" aria-label="Search board" placeholder="Search board" value={filters.query} onChange={event => patch({query: event.target.value})}/></label>
      <div className="kanban-assignee-filters" role="group" aria-label="Filter by assignee">
        <button type="button" className="kanban-person-filter unassigned" aria-label="Filter unassigned tickets" title="Unassigned" aria-pressed={filters.assignees.includes('unassigned')} onClick={() => togglePerson('unassigned')}><UserRound size={17}/></button>
        {people.map((person, index) => <button type="button" key={person.id} className={'kanban-person-filter avatar-color-' + index % 6} title={person.name} aria-label={'Filter tickets assigned to ' + person.name} aria-pressed={filters.assignees.includes(person.id)} onClick={() => togglePerson(person.id)}>{personInitials(person.name)}</button>)}
      </div>
      <Button variant="secondary" className={total ? 'kanban-filter-active' : ''} aria-expanded={expanded} onClick={() => setExpanded(value => !value)}><Filter size={15}/> Filter {total > 0 && <span className="kanban-filter-count">{total}</span>}</Button>
      {total > 0 && <Button variant="ghost" onClick={() => onChange({query: '', assignees: [], epic: '', tag: '', sprint: '', type: '', priority: '', blocked: false})}>Clear filters</Button>}
      {showGrouping && <label className="kanban-group-select"><Layers size={15}/><span className="kanban-sr-only">Group tickets</span><select aria-label="Group tickets" value={group} onChange={event => onGroup(event.target.value as BoardGroup)}><option value="none">Group: None</option><option value="assignee">Group: Assignee</option><option value="epic">Group: Epic</option></select></label>}
      <span className="kanban-visible-count" role="status">{count} tickets</span>
    </div>
    {expanded && <div className="kanban-filter-panel" onKeyDown={event => {if (event.key === 'Escape') {event.stopPropagation(); setExpanded(false)}}}>
      <label className="form-field">Epic<select value={filters.epic} onChange={event => patch({epic: event.target.value})}><option value="">All epics</option><option value="none">No epic</option>{content.epics?.map(epic => <option key={epic.id} value={epic.id}>{epic.name}</option>)}</select></label>
      <label className="form-field">Label<select value={filters.tag} onChange={event => patch({tag: event.target.value})}><option value="">All labels</option>{[...new Set(content.cards.flatMap(card => card.tags ?? []))].sort().map(tag => <option key={tag}>{tag}</option>)}</select></label>
      <label className="form-field">Sprint<select value={filters.sprint} onChange={event => patch({sprint: event.target.value})}><option value="">{showGrouping && active ? 'Current sprint · ' + active.name : 'All open work'}</option><option value="all">All open work</option><option value="backlog">Backlog</option>{content.sprints?.map(sprint => <option key={sprint.id} value={sprint.id}>{sprint.name}{sprint.status === 'COMPLETED' ? ' · completed' : ''}</option>)}</select></label>
      <label className="form-field">Type<select value={filters.type} onChange={event => patch({type: event.target.value})}><option value="">All types</option>{['TASK', 'STORY', 'BUG', 'SUBTASK'].map(type => <option key={type} value={type}>{type.toLowerCase()}</option>)}</select></label>
      <label className="form-field">Priority<select value={filters.priority} onChange={event => patch({priority: event.target.value})}><option value="">All priorities</option>{['LOW', 'MEDIUM', 'HIGH', 'URGENT'].map(priority => <option key={priority} value={priority}>{priority.toLowerCase()}</option>)}</select></label>
      <label className="kanban-flag-filter"><input type="checkbox" checked={filters.blocked} onChange={event => patch({blocked: event.target.checked})}/> Flagged only</label>
    </div>}
  </div>
}
