import { readFile } from 'node:fs/promises'
import assert from 'node:assert/strict'
import test from 'node:test'
import ts from 'typescript'

const compile = source => ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ES2022, target: ts.ScriptTarget.ES2020 } }).outputText
const moduleUrl = source => 'data:text/javascript;base64,' + Buffer.from(source).toString('base64')
const contentUrl = moduleUrl(compile(await readFile(new URL('../src/vendor/weasycanvas/editor/content.ts', import.meta.url), 'utf8')))
const editingSource = compile(await readFile(new URL('../src/vendor/weasycanvas/src/termEditing.ts', import.meta.url), 'utf8')).replace('"../editor/content"', JSON.stringify(contentUrl))
const { parseTerms, insertSectionTerms } = await import(moduleUrl(editingSource))
const { moveClause, removeClause, setClauseText, updateSubparagraphs, updateSubsubparagraphs } = await import(contentUrl)
const limits = { maxParagraphs: 100, maxTextLength: 4000 }
const fixture = () => ({
  paragraphs: ['Introduction', 'First term', 'Second term', 'Closing'],
  paragraphSectionIds: ['intro', 'terms', 'terms', 'closing'],
  subparagraphs: [[], ['Child'], [], []],
  subsubparagraphs: [[], [['Grandchild']], [], []],
  paragraphStyles: [{}, { bold: true }, { italic: true }, {}],
  subparagraphStyles: [[], [{ underline: true }], [], []],
  subsubparagraphStyles: [[], [[{ color: '#112233' }]], [], []],
})

test('bulk paste handles Windows line endings and skips empty lines', () => {
  assert.deepEqual(parseTerms(' First term\r\n\r\n  \r\nSecond term\rThird term ', 'lines'), ['First term', 'Second term', 'Third term'])
  assert.deepEqual(parseTerms(' \r\n\t', 'lines'), [])
})

test('blank-line mode preserves line breaks within a term and keeps field tokens', () => {
  assert.deepEqual(parseTerms('Payment to {{api:client}}\r\nwithin thirty days.\r\n \r\nNext clause.', 'paragraphs'), ['Payment to {{api:client}}\nwithin thirty days.', 'Next clause.'])
})

test('bulk additions stay inside the selected section without detaching existing descendants or styles', () => {
  const original = fixture(), snapshot = structuredClone(original)
  const result = insertSectionTerms(original, 'terms', ['Third term', 'Fourth term'], limits)
  assert.equal(result.error, undefined)
  assert.equal(result.index, 3)
  assert.deepEqual(result.content.paragraphs, ['Introduction', 'First term', 'Second term', 'Third term', 'Fourth term', 'Closing'])
  assert.deepEqual(result.content.paragraphSectionIds, ['intro', 'terms', 'terms', 'terms', 'terms', 'closing'])
  assert.deepEqual(result.content.subsubparagraphs[1], [['Grandchild']])
  assert.deepEqual(result.content.subsubparagraphStyles[1], [[{ color: '#112233' }]])
  assert.deepEqual(result.content.subparagraphs[3], [])
  assert.deepEqual(result.content.paragraphStyles[3], {})
  assert.deepEqual(original, snapshot)
})

test('insert after creates an empty editable entry between terms while preserving the next family', () => {
  const original = fixture(), result = insertSectionTerms(original, 'terms', [''], limits, 1)
  assert.equal(result.index, 2)
  assert.deepEqual(result.content.paragraphs, ['Introduction', 'First term', '', 'Second term', 'Closing'])
  assert.deepEqual(result.content.paragraphStyles[3], { italic: true })
  assert.equal(insertSectionTerms(original, 'terms', ['Wrong section'], limits, 0).content, original)
})

test('a failed bulk addition is atomic and never truncates wording', () => {
  const original = fixture()
  for (const result of [
    insertSectionTerms(original, 'terms', [], limits),
    insertSectionTerms(original, 'terms', ['Third', 'Fourth'], { ...limits, maxParagraphs: 3 }),
    insertSectionTerms(original, 'terms', ['Valid', 'Too long'], { ...limits, maxTextLength: 5 }),
  ]) {
    assert.ok(result.error)
    assert.equal(result.content, original)
    assert.equal(result.index, undefined)
  }
  const exact = insertSectionTerms(original, 'terms', ['12345'], { maxParagraphs: 3, maxTextLength: 5 })
  assert.equal(exact.error, undefined)
  assert.equal(exact.content.paragraphs[3], '12345')
})

test('the document limit counts other sections as well as the selected section', () => {
  const content = { paragraphs: Array(100).fill('Text'), paragraphSectionIds: ['terms', ...Array(99).fill('other')], subparagraphs: [] }
  const result = insertSectionTerms(content, 'terms', ['New term'], limits)
  assert.ok(result.error)
  assert.equal(result.content, content)
})

test('legacy content gains section membership and an empty section can receive its first term', () => {
  const legacy = insertSectionTerms({ paragraphs: ['Existing'], subparagraphs: [[]] }, 'terms', ['New'], limits)
  assert.deepEqual(legacy.content.paragraphSectionIds, ['terms', 'terms'])
  const empty = insertSectionTerms(fixture(), 'new-section', ['New section text'], limits)
  assert.equal(empty.content.paragraphSectionIds.at(-1), 'new-section')
  assert.equal(empty.content.paragraphs.at(-1), 'New section text')
})

test('editing, reordering and removing a term preserve the remaining clause families', () => {
  const original = fixture()
  const edited = setClauseText(original, { index: 2 }, 'Updated second term')
  const moved = moveClause(edited, { index: 1 }, 1)
  assert.deepEqual(moved.paragraphs, ['Introduction', 'Updated second term', 'First term', 'Closing'])
  assert.deepEqual(moved.subparagraphs[2], ['Child'])
  assert.deepEqual(moved.subsubparagraphs[2], [['Grandchild']])
  assert.deepEqual(moved.paragraphStyles[2], { bold: true })
  assert.deepEqual(moved.subparagraphStyles[2], [{ underline: true }])
  assert.deepEqual(moved.subsubparagraphStyles[2], [[{ color: '#112233' }]])
  const removed = removeClause(moved, { index: 2 })
  assert.deepEqual(removed.paragraphs, ['Introduction', 'Updated second term', 'Closing'])
  assert.deepEqual(removed.paragraphSectionIds, ['intro', 'terms', 'closing'])
  assert.deepEqual(removed.subsubparagraphs, [[], [], []])
  assert.equal(moveClause(original, { index: 1 }, -1), original)
})

test('new subterms and third-level clauses retain their siblings and existing formatting', () => {
  let content = updateSubparagraphs(fixture(), 1, ['Child', 'New child'])
  content = updateSubsubparagraphs(content, 1, 1, ['New grandchild'])
  content = setClauseText(content, { index: 1, subIndex: 1 }, 'Updated child')
  const moved = moveClause(content, { index: 1, subIndex: 1 }, -1)
  assert.deepEqual(moved.subparagraphs[1], ['Updated child', 'Child'])
  assert.deepEqual(moved.subsubparagraphs[1], [['New grandchild'], ['Grandchild']])
  assert.deepEqual(moved.subparagraphStyles[1][1], { underline: true })
  const removed = removeClause(moved, { index: 1, subIndex: 0 })
  assert.deepEqual(removed.subparagraphs[1], ['Child'])
  assert.deepEqual(removed.subsubparagraphs[1], [['Grandchild']])
})
