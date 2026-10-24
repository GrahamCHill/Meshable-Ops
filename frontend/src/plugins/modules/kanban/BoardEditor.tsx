import {useEffect, useState} from 'react'
import {useMutation, useQuery} from '@tanstack/react-query'
import {Link,useSearchParams} from 'react-router-dom'
import {CalendarDays, CheckCheck, Plus, Settings2} from 'lucide-react'
import {Button} from '../../../components/ui'
import {getErrorMessage} from '../../../components/admin/adminUtils'
import {usePageDraft, useDiscardPageDraft} from '../../../hooks/usePageDraft'
import {useAuthStore} from '../../../hooks/useAuthStore'
import {boardsApi, type Board, type BoardCard, type BoardInput} from './api'
import {CardEditor} from './CardEditor'
import {BoardConfiguration} from './BoardConfiguration'
import {DevelopmentLinks} from './DevelopmentLinks'
import {BoardToolbar} from './BoardToolbar'
import {BoardSurface} from './BoardSurface'
import {BacklogView} from './BacklogView'
import {SprintDialog, type SprintDialogAction} from './SprintDialog'
import {WorkCharts} from '../../../components/projects/WorkCharts'
import {boardSummary} from './boardSummary'
import {emptyFilters, filterTickets, parentTickets, planTicket, type BoardFilters, type BoardGroup} from './boardView'
import '../../../styles/projects.css'

export function BoardEditor({initial, onSaved, readOnly = false}: Readonly<{initial: Board; onSaved: (board: Board) => void; readOnly?: boolean}>) {
  const [params,setParams]=useSearchParams()
  const [draft, setDraft] = useState(initial)
  const [editing, setEditing] = useState<BoardCard>()
  const [configure, setConfigure] = useState(false)
  const [tab, setTab] = useState('board')
  const [filters, setFilters] = useState<BoardFilters>(emptyFilters)
  const [group, setGroup] = useState<BoardGroup>('none')
  const [sprintDialog, setSprintDialog] = useState<SprintDialogAction>()
  const [fresh, setFresh] = usePageDraft<Record<string, BoardCard>>('new-tickets:' + initial.id, {})
  const linkedCard=params.get('card'),linkedSprint=params.get('sprint')
  useEffect(()=>{if(linkedCard){const found=initial.content.cards.find(card=>card.id===linkedCard);if(found)setEditing(found)}},[initial.id,linkedCard])
  useEffect(()=>{if(linkedSprint){setFilters(value=>({...value,sprint:linkedSprint}));setTab('backlog')}},[initial.id,linkedSprint])
  const discard = useDiscardPageDraft()
  const userId = useAuthStore(state => state.user?.id)
  const members = useQuery({queryKey: ['board-assignees', userId, initial.id], queryFn: () => boardsApi.members(initial.id)})
  const people = members.data ?? []
  const accept = (board: Board) => {setDraft(board); onSaved(board)}
  const save = useMutation({mutationFn: (board: BoardInput) => boardsApi.save(initial.id, board), onSuccess: accept})
  const move = useMutation({mutationFn: ({card, column, before}: {card: string; column: string; before?: string}) => boardsApi.move(initial.id, card, column, draft.revision, before), onSuccess: accept})
  const sprintAction = useMutation({mutationFn: ({kind, id, destination}: {kind: 'start' | 'complete'; id: string; destination?: string | null}) => kind === 'start'
    ? boardsApi.startSprint(initial.id, id, draft.revision)
    : boardsApi.completeSprint(initial.id, id, draft.revision, destination ?? null), onSuccess: accept})
  const resetSave = save.reset, resetMove = move.reset, resetSprint = sprintAction.reset
  useEffect(() => {setDraft(initial); resetSave(); resetMove(); resetSprint()}, [initial, resetSave, resetMove, resetSprint])
  const busy = save.isPending || move.isPending || sprintAction.isPending
  const failure = save.error ?? move.error ?? sprintAction.error
  const error = failure ? getErrorMessage(failure) : undefined
  const canManage = draft.canManage && !readOnly
  const active = draft.content.sprints?.find(sprint => sprint.status === 'ACTIVE')
  const allowed = (card: string, column: string) => !readOnly && !busy && !!draft.moves[card]?.includes(column)
  const doMove = (card: string, column: string, before?: string) => {if (allowed(card, column) && card !== before) {save.reset(); move.mutate({card, column, before})}}
  const open = (card: BoardCard) => {save.reset(); move.reset(); setEditing(card);if(card.reference){const next=new URLSearchParams(params);next.set('card',card.id);next.delete('comment');setParams(next,{replace:true})}}
  const closeTicket=()=>{setEditing(undefined);if(params.has('card')||params.has('comment')){const next=new URLSearchParams(params);next.delete('card');next.delete('comment');setParams(next,{replace:true})}}
  const selectedSprint = () => {
    const selected = draft.content.sprints?.find(sprint => sprint.id === filters.sprint && sprint.status !== 'COMPLETED')
    if (filters.sprint) return selected?.id ?? null
    return active?.id ?? null
  }
  const makeCard = (columnId: string, sprintId: string | null, parent?: BoardCard): BoardCard => ({
    id: crypto.randomUUID(), columnId, title: '', description: '', status: draft.content.columns.find(column => column.id === columnId)?.status ?? 'TODO',
    reviewerIds: [], tags: [], type: parent ? 'SUBTASK' : 'TASK', priority: 'MEDIUM', parentId: parent?.id ?? null,
    sprintId: parent ? parent.sprintId : sprintId, epicId: parent?.epicId ?? (filters.epic !== 'none' ? filters.epic || null : null),
    assigneeId: parent?.assigneeId ?? (filters.assignees.length === 1 && filters.assignees[0] !== 'unassigned' ? filters.assignees[0] : null),
  })
  const add = (columnId: string, sprintId: string | null = selectedSprint(), parent?: BoardCard) => {
    if (readOnly || busy || draft.content.cards.length >= 200) return
    const key = columnId + ':' + (sprintId ?? '') + ':' + (parent?.id ?? '')
    const card = fresh[key] ?? makeCard(columnId, sprintId, parent)
    setFresh({...fresh, [key]: card}); setEditing(card); save.reset()
  }
  const addSubtask = (parent: BoardCard) => add(draft.content.columns.find(column => column.status === 'TODO')?.id ?? draft.content.columns[0].id, parent.sprintId ?? null, parent)
  const saveCard = async (card: BoardCard) => {
    await save.mutateAsync({...draft, content: {...draft.content, cards: draft.content.cards.some(item => item.id === card.id) ? draft.content.cards.map(item => item.id === card.id ? card : item) : [...draft.content.cards, card]}})
    discard('ticket:' + draft.id + ':' + card.id); discard('ticket:' + draft.id + ':' + card.id + ':tags')
    setFresh(Object.fromEntries(Object.entries(fresh).filter(([, item]) => item.id !== card.id))); closeTicket()
  }
  const createInline = async (column: string, title: string, type: BoardCard['type'], groupId: string) => {
    const card = {...makeCard(column, selectedSprint()), title, type}
    if (group === 'assignee' && groupId !== 'all' && groupId !== 'empty') card.assigneeId = groupId === 'none' ? null : groupId
    if (group === 'epic' && groupId !== 'all' && groupId !== 'empty') card.epicId = groupId === 'none' ? null : groupId
    await saveCard(card)
  }
  const reorderColumns = (from: string, target: string) => {
    if (!canManage || busy) return
    if (!from) {save.reset(); setConfigure(true); return}
    if (from === target) return
    const columns = [...draft.content.columns]
    const fromIndex = columns.findIndex(column => column.id === from), toIndex = columns.findIndex(column => column.id === target)
    if (fromIndex < 0 || toIndex < 0) return
    const [column] = columns.splice(fromIndex, 1); columns.splice(toIndex, 0, column)
    save.mutate({...draft, content: {...draft.content, columns}})
  }
  const plan = (card: string, sprint: string | null, before?: string) => {
    if (!readOnly && !busy) save.mutate({...draft, content: {...draft.content, cards: planTicket(draft.content.cards, card, sprint, before)}})
  }
  const openSprint = (action: SprintDialogAction) => {save.reset(); sprintAction.reset(); setSprintDialog(action)}
  const visible = filterTickets(draft.content, filters, tab === 'board')
  const parents = parentTickets(visible, draft.content.cards)
  const canCreate = !readOnly && !busy && draft.content.cards.length < 200
  return <div className="page-stack kanban-workspace">
    <div className="page-heading kanban-board-heading"><div><h1>{draft.name}</h1><div className="kanban-board-context">{draft.projectId && <Link to={'/projects/' + draft.projectId}>Open project</Link>}{active && <span><CalendarDays size={14}/>{active.name} · {active.startDate} – {active.endDate}</span>}</div></div><div className="inline-actions">
      {canManage && active && <Button className="kanban-sprint-complete" disabled={busy} onClick={() => openSprint({kind: 'complete', sprint: active})}><CheckCheck size={15}/> Complete sprint</Button>}
      {canManage && <Button variant="secondary" disabled={busy} onClick={() => {save.reset(); setConfigure(true)}}><Settings2 size={15}/> Configure board</Button>}
      <Button variant={active ? 'secondary' : 'primary'} disabled={!canCreate} onClick={() => add(draft.content.columns[0].id)}><Plus size={15}/> Create ticket</Button>
    </div></div>
    <nav className="workspace-tabs kanban-view-tabs" aria-label="Board views">{[['board', 'Board'], ['backlog', 'Backlog'], ['list', 'List'], ['summary', 'Summary']].map(([key, label]) => <button key={key} aria-current={tab === key ? 'page' : undefined} onClick={() => setTab(key)}>{label}</button>)}</nav>
    {readOnly && <p className="field-help">Read-only board. You can inspect tickets and comments.</p>}
    {tab !== 'summary' && <BoardToolbar content={draft.content} people={people} filters={filters} onChange={setFilters} group={group} onGroup={setGroup} count={parents.length} showGrouping={tab === 'board'}/>}
    {busy && <p role="status" className="kanban-save-state">Saving changes…</p>}
    {error && !editing && !configure && !sprintDialog && <p role="alert">{error} Reload the board if it changed elsewhere. Your form draft is retained.</p>}
    {tab === 'summary' && <WorkCharts value={boardSummary(draft, people)}/>}
    {tab === 'board' && <BoardSurface board={draft} cards={parents} people={people} group={group} busy={busy} readOnly={readOnly} onOpen={open} onMove={doMove} onColumns={reorderColumns} onCreate={createInline} onSubtask={addSubtask}/>}
    {tab === 'backlog' && <BacklogView board={draft} cards={parents} people={people} busy={busy} readOnly={readOnly} onOpen={open} onPlan={plan} onSprint={openSprint} onCreate={sprint => add(draft.content.columns[0].id, sprint)}/>}
    {tab === 'list' && <div className="project-table-wrap"><table className="project-table"><thead><tr><th>Ticket</th><th>Column</th><th>Assignee</th><th>Epic</th><th>Labels</th><th>Due</th></tr></thead><tbody>{visible.map(card => <tr key={card.id}><td><button className="kanban-ticket-link" onClick={() => open(card)}>{card.reference} {card.title}</button></td><td>{draft.content.columns.find(column => column.id === card.columnId)?.title}</td><td>{people.find(person => person.id === card.assigneeId)?.name ?? 'Unassigned'}</td><td>{draft.content.epics?.find(epic => epic.id === card.epicId)?.name ?? '–'}</td><td>{card.tags?.join(', ')}</td><td>{card.dueDate ?? '–'}</td></tr>)}</tbody></table>{!visible.length && <p>No tickets match this view.</p>}</div>}
    {configure && canManage && <BoardConfiguration id={draft.id} board={draft} people={people} busy={busy} error={error} onSave={value => save.mutate(value, {onSuccess: () => {discard('board-config:' + draft.id); setConfigure(false)}})} onClose={() => setConfigure(false)}/>}
    {sprintDialog && canManage && <SprintDialog key={sprintDialog.kind+('sprint' in sprintDialog?sprintDialog.sprint.id:'')} board={draft} action={sprintDialog} busy={busy} error={error} onClose={() => setSprintDialog(undefined)}
      onCreate={async sprint => {const sprints=draft.content.sprints??[];await save.mutateAsync({...draft, content: {...draft.content, sprints: sprints.some(item=>item.id===sprint.id)?sprints.map(item=>item.id===sprint.id?sprint:item):[...sprints,sprint]}})}}
      onStart={async id => {await sprintAction.mutateAsync({kind: 'start', id}); setFilters(value => ({...value, sprint: ''})); setTab('board')}}
      onComplete={async (id, destination) => {await sprintAction.mutateAsync({kind: 'complete', id, destination}); setFilters(value => ({...value, sprint: ''})); setTab('backlog')}}/>}
    {editing && <CardEditor key={editing.id} boardId={draft.id} card={editing} cards={draft.content.cards} columns={draft.content.columns} epics={draft.content.epics} sprints={draft.content.sprints} people={people} allowedColumns={draft.moves[editing.id]} estimation={draft.estimation} busy={busy} readOnly={readOnly} error={error} onCancel={closeTicket} onSave={card => {void saveCard(card).catch(() => undefined)}} onOpenCard={open} onCreateSubtask={canCreate ? addSubtask : undefined}
      onRemove={draft.content.cards.some(card => card.id === editing.id) ? () => save.mutate({...draft, content: {...draft.content, cards: draft.content.cards.filter(card => card.id !== editing.id).map(card=>({...card,links:card.links?.filter(link=>link.targetId!==editing.id)}))}}, {onSuccess: () => {discard('ticket:' + draft.id + ':' + editing.id); closeTicket()}}) : undefined}/>}
    <details className="kanban-development"><summary>Repositories and pull requests</summary><DevelopmentLinks boardId={draft.id} cards={draft.content.cards} locked={readOnly || busy}/></details>
  </div>
}
