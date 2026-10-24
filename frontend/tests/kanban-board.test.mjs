import {readFile} from 'node:fs/promises'
import assert from 'node:assert/strict'
import test from 'node:test'
import ts from 'typescript'

// Exercise the same pure planning/filtering helpers used by the board, without a browser or API.
const source = await readFile(new URL('../src/plugins/modules/kanban/boardView.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, {compilerOptions: {module: ts.ModuleKind.ES2022, target: ts.ScriptTarget.ES2020}}).outputText
const {emptyFilters, filterTickets, parentTickets, rankBefore, planTicket, ticketGroups} = await import('data:text/javascript;base64,' + Buffer.from(compiled).toString('base64'))

const ticket = (id, fields = {}) => ({id, title: id, description: '', columnId: 'todo', tags: [], ...fields})
const content = {
  columns: [{id: 'todo'}, {id: 'done'}],
  sprints: [{id: 'current', status: 'ACTIVE'}, {id: 'future', status: 'PLANNED'}, {id: 'past', status: 'COMPLETED'}],
  cards: [ticket('current', {sprintId: 'current'}), ticket('future', {sprintId: 'future'}), ticket('backlog'), ticket('past', {sprintId: 'past', status: 'DONE'})],
}

test('the board defaults to the active sprint while the backlog retains future work', () => {
  assert.deepEqual(filterTickets(content, emptyFilters, true).map(card => card.id), ['current'])
  assert.deepEqual(filterTickets(content, emptyFilters).map(card => card.id), ['current', 'future', 'backlog'])
  assert.deepEqual(filterTickets(content, {...emptyFilters, sprint: 'backlog'}, true).map(card => card.id), ['backlog'])
  assert.deepEqual(filterTickets(content, {...emptyFilters, sprint: 'past'}, true).map(card => card.id), ['past'])
})

test('multiple assignees combine with text, label, type, and flagged filters', () => {
  const cards = [ticket('one', {title: 'Fix upload', assigneeId: 'a', tags: ['storage'], type: 'BUG', blocked: true}), ticket('two', {title: 'Fix download', assigneeId: 'b', type: 'BUG'}), ticket('three', {title: 'Other'})]
  assert.deepEqual(filterTickets({...content, cards}, {...emptyFilters, assignees: ['a', 'b'], query: 'fix', type: 'BUG'}).map(card => card.id), ['one', 'two'])
  assert.deepEqual(filterTickets({...content, cards}, {...emptyFilters, assignees: ['a', 'b'], tag: 'storage', blocked: true}).map(card => card.id), ['one'])
})

test('a matching subtask surfaces its parent once instead of duplicating board cards', () => {
  const parent = ticket('parent')
  const child = ticket('child', {parentId: 'parent', type: 'SUBTASK', title: 'Matching text'})
  const cards = [parent, child]
  const matches = filterTickets({...content, cards}, {...emptyFilters, query: 'matching'})
  assert.deepEqual(parentTickets(matches, cards).map(card => card.id), ['parent'])
  assert.deepEqual(parentTickets(cards, cards).map(card => card.id), ['parent'])
})

test('ranking after a card respects column order and skips subtasks and the moving card', () => {
  const cards = [ticket('first'), ticket('child', {parentId: 'first'}), ticket('other-column', {columnId: 'done'}), ticket('middle'), ticket('last')]
  assert.equal(rankBefore(cards, 'last', 'first', false), 'first')
  assert.equal(rankBefore(cards, 'last', 'first', true), 'middle')
  assert.equal(rankBefore(cards, 'first', 'last', true), undefined)
})

test('planning moves a whole ticket family and preserves workflow status and references', () => {
  const cards = [ticket('parent', {reference: 'OPS-1', sprintId: 'current'}), ticket('child', {parentId: 'parent', reference: 'OPS-2', columnId: 'done', status: 'DONE', sprintId: 'current'}), ticket('target')]
  const planned = planTicket(cards, 'parent', 'future', 'target')
  assert.deepEqual(planned.map(card => card.id), ['parent', 'child', 'target'])
  assert.deepEqual(planned.slice(0, 2).map(card => card.sprintId), ['future', 'future'])
  assert.equal(planned[1].status, 'DONE')
  assert.equal(planned[1].reference, 'OPS-2')
  assert.equal(planned[1].columnId, 'done')
  assert.deepEqual(planTicket(cards, 'child', null).slice(-2).map(card => card.sprintId), [null, null])
  assert.equal(cards[0].sprintId, 'current')
})

test('assignee and epic swimlanes keep an explicit unassigned group', () => {
  const cards = [ticket('first', {assigneeId: 'a', epicId: 'release'}), ticket('second')]
  assert.deepEqual(ticketGroups(cards, 'assignee', [{id: 'a', name: 'Alex'}], []).map(group => [group.label, group.cards.length]), [['Alex', 1], ['Unassigned', 1]])
  assert.deepEqual(ticketGroups(cards, 'epic', [], [{id: 'release', name: 'Release'}]).map(group => group.label), ['Release', 'No epic'])
  assert.deepEqual(ticketGroups(cards, 'assignee', [], []).map(group => [group.id, group.cards.length]), [['none', 2]])
})
