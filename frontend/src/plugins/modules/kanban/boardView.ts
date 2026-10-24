import type {Person} from '../../../api/projects'
import type {BoardCard, BoardInput} from './api'

export type BoardGroup = 'none' | 'assignee' | 'epic'
export interface BoardFilters {
  query: string
  assignees: string[]
  epic: string
  tag: string
  sprint: string
  type: string
  priority: string
  blocked: boolean
}
export const emptyFilters: BoardFilters = {query: '', assignees: [], epic: '', tag: '', sprint: '', type: '', priority: '', blocked: false}

export function filterTickets(content: BoardInput['content'], filters: BoardFilters, activeSprintOnly = false) {
  const query = filters.query.trim().toLowerCase()
  const active = content.sprints?.find(sprint => sprint.status === 'ACTIVE')
  const sprint = filters.sprint || (activeSprintOnly ? active?.id : '')
  return content.cards.filter(card => {
    const sprintState = content.sprints?.find(item => item.id === card.sprintId)?.status
    if (!sprint || sprint === 'all') {
      if (sprintState === 'COMPLETED') return false
    } else if (sprint === 'backlog' ? !!card.sprintId : card.sprintId !== sprint) return false
    return (!query || [card.title, card.reference, card.description, ...(card.tags ?? [])].join(' ').toLowerCase().includes(query))
      && (!filters.assignees.length || filters.assignees.includes(card.assigneeId || 'unassigned'))
      && (!filters.epic || (filters.epic === 'none' ? !card.epicId : card.epicId === filters.epic))
      && (!filters.tag || card.tags?.includes(filters.tag))
      && (!filters.type || (card.type ?? 'TASK') === filters.type)
      && (!filters.priority || (card.priority ?? 'MEDIUM') === filters.priority)
      && (!filters.blocked || card.blocked)
  })
}

export function parentTickets(visible: BoardCard[], all: BoardCard[]) {
  const ids = new Set(visible.map(card => card.parentId || card.id))
  return all.filter(card => !card.parentId && ids.has(card.id))
}

export function ticketGroups(cards: BoardCard[], group: BoardGroup, people: Person[], epics: BoardInput['content']['epics']) {
  if (group === 'none') return [{id: 'all', label: '', cards}]
  const groups = new Map<string, {id: string; label: string; cards: BoardCard[]}>()
  cards.forEach(card => {
    const candidate = group === 'assignee' ? card.assigneeId : card.epicId
    const id = (group === 'assignee' ? people.some(person => person.id === candidate) : epics?.some(epic => epic.id === candidate)) ? candidate! : 'none'
    const label = group === 'assignee'
      ? people.find(person => person.id === id)?.name || 'Unassigned'
      : epics?.find(epic => epic.id === id)?.name || 'No epic'
    if (!groups.has(id)) groups.set(id, {id, label, cards: []})
    groups.get(id)!.cards.push(card)
  })
  return [...groups.values()].sort((a, b) => a.id === 'none' ? 1 : b.id === 'none' ? -1 : a.label.localeCompare(b.label))
}

export function rankBefore(cards: BoardCard[], moving: string, target: string, after: boolean) {
  if (!after) return target
  const remaining = cards.filter(card => card.id !== moving && !card.parentId)
  const index = remaining.findIndex(card => card.id === target)
  const column = remaining[index]?.columnId
  return remaining.slice(index + 1).find(card => card.columnId === column)?.id
}

export function personInitials(name: string) {
  return name.trim().split(/\s+/).slice(0, 2).map(value => value[0] ?? '').join('').toUpperCase() || '?'
}

export function planTicket(cards: BoardCard[], id: string, sprint: string | null, before?: string) {
  const selected = cards.find(card => card.id === id)
  const parent = selected?.parentId || id
  const family = cards.filter(card => card.id === parent || card.parentId === parent).map(card => ({...card, sprintId: sprint}))
  if (!selected || !family.length || before === parent) return cards
  const remaining = cards.filter(card => card.id !== parent && card.parentId !== parent)
  const index = before ? remaining.findIndex(card => card.id === before) : -1
  remaining.splice(index < 0 ? remaining.length : index, 0, ...family)
  return remaining
}

export function filterCount(filters: BoardFilters) {
  return filters.assignees.length + [filters.query.trim(), filters.epic, filters.tag, filters.sprint, filters.type, filters.priority, filters.blocked].filter(Boolean).length
}
