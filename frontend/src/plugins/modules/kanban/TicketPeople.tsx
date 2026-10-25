import type {Person} from '../../../api/projects'
import type {BoardCard} from './api'
import {personInitials} from './boardView'

export function TicketPeople({card, people, compact = false}: Readonly<{
  card: Pick<BoardCard, 'assigneeId' | 'reviewerIds'>; people: Person[]; compact?: boolean
}>) {
  const owner = card.assigneeId ? people.find(person => person.id === card.assigneeId)?.name || 'Unavailable member' : 'Unassigned'
  const reviewers = [...new Set(card.reviewerIds ?? [])].map(id => people.find(person => person.id === id)?.name || 'Unavailable member')
  const visibleReviewers = compact ? reviewers.slice(0, 2) : reviewers
  return <span className={'kanban-ticket-people' + (compact ? ' is-compact' : '')}>
    <span className="kanban-ticket-person" title={'Owner: ' + owner}>
      <span className="kanban-avatar" aria-hidden="true">{card.assigneeId ? personInitials(owner) : '–'}</span>
      <span className="kanban-ticket-person-details"><span className="kanban-ticket-role">Owner</span><span className="kanban-ticket-person-name">{owner}</span></span>
    </span>
    <span className="kanban-ticket-person" title={'Reviewers: ' + (reviewers.join(', ') || 'None assigned')}>
      <span className="kanban-ticket-person-details"><span className="kanban-ticket-role">Reviewers{reviewers.length > 1 ? ` (${reviewers.length})` : ''}</span><span className="kanban-ticket-person-name">{visibleReviewers.join(', ') || 'None assigned'}{compact && reviewers.length > 2 ? ` · +${reviewers.length - 2} more` : ''}</span></span>
    </span>
  </span>
}
