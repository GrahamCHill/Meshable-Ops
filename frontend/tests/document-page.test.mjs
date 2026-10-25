import { readFile } from 'node:fs/promises'
import assert from 'node:assert/strict'
import test from 'node:test'
import ts from 'typescript'

const compile = source => ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ES2022, target: ts.ScriptTarget.ES2020 } }).outputText
const moduleUrl = source => 'data:text/javascript;base64,' + Buffer.from(source).toString('base64')
const load = async (path, imports = {}) => {
  let code = compile(await readFile(new URL('../src/vendor/weasycanvas/' + path + '.ts', import.meta.url), 'utf8'))
  for (const [specifier, url] of Object.entries(imports)) code = code.replaceAll(JSON.stringify(specifier), JSON.stringify(url))
  return moduleUrl(code)
}
const fieldsUrl = await load('editor/apiFields')
const inlineUrl = await load('editor/inlineApi', { './apiFields': fieldsUrl })
const signatureUrl = await load('src/signatureText')
const { splitSignatureText, replaceClosingSegment } = await import(signatureUrl)
const { templatePageText, updateTemplatePageText } = await import(await load('src/templatePageEditing', { '../editor/inlineApi': inlineUrl, './signatureText': signatureUrl }))

const section = (kind, patch = {}) => ({ kind, label: kind, maxLength: 4000, required: true, locked: false, ...patch })
const fixture = () => ({
  sections: [],
  defaults: {
    title: 'Service agreement', organization: 'Example business', reference: 'DOC-1', recipient: 'Client',
    paragraphs: ['Existing wording'], subparagraphs: [[]],
    closing_text: 'Before\n\n{{signature}}\n\nAfter', text: { sectionHeadings: { terms: 'Terms' } },
  },
  apiFields: [{ key: 'client', label: 'Client', maxLength: 100, required: false }],
})

test('editing rendered text updates the same draft defaults without touching its clauses', () => {
  const original = fixture(), saved = updateTemplatePageText(original, section('title'), 'title', 'Updated agreement')
  assert.equal(saved.defaults.title, 'Updated agreement')
  assert.equal(original.defaults.title, 'Service agreement')
  assert.equal(saved.defaults.paragraphs, original.defaults.paragraphs)
  assert.equal(saved.apiFields, original.apiFields)
})

test('whole-field API preview values cannot overwrite any bound source slot', () => {
  const original = fixture()
  for (const [kind, slot, binding] of [['title', 'title', 'text'], ['header', 'organization', 'text'], ['details', 'reference', 'reference'], ['details', 'recipient', 'recipient'], ['footer', 'footer', 'text'], ['body', 'heading', 'heading'], ['closing', 'closingBefore', 'text']]) {
    assert.equal(updateTemplatePageText(original, section(kind, { bindings: { [binding]: 'client' } }), slot, 'Resolved client'), original)
  }
})

test('edits preserve inline data markers while allowing surrounding wording to change', () => {
  const original = fixture(); original.defaults.title = 'Agreement with {{api:client}}'
  const title = section('title')
  assert.equal(updateTemplatePageText(original, title, 'title', 'Agreement with Jane'), original)
  assert.equal(updateTemplatePageText(original, title, 'title', '{{api:other}}'), original)
  assert.equal(updateTemplatePageText(original, title, 'title', 'New agreement with {{api:client}}').defaults.title, 'New agreement with {{api:client}}')
})

test('text on either side of a signature changes without moving or deleting the marker', () => {
  const original = fixture(), closing = section('closing')
  const before = updateTemplatePageText(original, closing, 'closingBefore', 'Please sign here.\n\n')
  const after = updateTemplatePageText(before, closing, 'closingAfter', '\n\nThank you.')
  assert.equal(after.defaults.closing_text, 'Please sign here.\n\n{{signature}}\n\nThank you.')
  assert.deepEqual(splitSignatureText(after.defaults.closing_text), { before: 'Please sign here.\n\n', marker: '{{signature}}', after: '\n\nThank you.' })
  assert.equal(original.defaults.closing_text, 'Before\n\n{{signature}}\n\nAfter')
  const legacy = 'Opening\nSigned by: ________\nClosing'
  assert.equal(replaceClosingSegment(legacy, 'before', 'New opening\n'), 'New opening\nSigned by: ________\nClosing')
  assert.equal(replaceClosingSegment('Plain closing', 'before', 'New closing'), 'New closing')
})

test('page edits enforce slot limits and the combined closing limit', () => {
  const original = fixture()
  for (const [kind, slot, length] of [['details', 'reference', 101], ['details', 'recipient', 501], ['header', 'organization', 161], ['body', 'heading', 81], ['details', 'referenceLabel', 81]]) {
    assert.equal(updateTemplatePageText(original, section(kind), slot, 'x'.repeat(length)), original)
  }
  assert.equal(updateTemplatePageText(original, section('closing', { maxLength: 30 }), 'closingBefore', 'x'.repeat(20)), original)
  assert.equal(updateTemplatePageText(original, section('title', { maxLength: 5 }), 'title', '123456'), original)
})

test('headings, labels and footers save in their own slots and keep sibling values', () => {
  const original = fixture(), terms = section('body', { id: 'terms' })
  let updated = updateTemplatePageText(original, terms, 'heading', 'Conditions')
  updated = updateTemplatePageText(updated, section('details'), 'referenceLabel', 'Document number:')
  updated = updateTemplatePageText(updated, section('footer'), 'footer', 'Confidential')
  assert.equal(updated.defaults.text.sectionHeadings.terms, 'Conditions')
  assert.equal(updated.defaults.text.referenceLabel, 'Document number:')
  assert.equal(updated.defaults.text.footer, 'Confidential')
  assert.equal(original.defaults.text.sectionHeadings.terms, 'Terms')
  assert.equal(templatePageText(original, section('footer'), 'footer'), 'Example business · DOC-1')
  assert.equal(templatePageText(original, section('details'), 'recipientLabel'), 'Prepared for:')
  assert.equal(templatePageText(original, section('body', { id: 'other', label: 'Other terms' }), 'heading'), '')
})

test('section headings can be cleared without changing the section label or its wording', () => {
  const original = fixture(), terms = section('body', { id: 'terms', label: 'Terms' })
  const updated = updateTemplatePageText(original, terms, 'heading', '')
  assert.equal(templatePageText(updated, terms, 'heading'), '')
  assert.equal(updated.defaults.text.sectionHeadings.terms, '')
  assert.equal(terms.label, 'Terms')
  assert.deepEqual(updated.defaults.paragraphs, original.defaults.paragraphs)
})

test('an automatic footer retains data bindings when its surrounding wording is edited', () => {
  const original = fixture()
  original.sections = [section('header', { bindings: { text: 'client' } }), section('details', { bindings: { reference: 'reference' } })]
  const footer = section('footer')
  assert.equal(templatePageText(original, footer, 'footer'), '{{api:client}} · {{api:reference}}')
  assert.equal(updateTemplatePageText(original, footer, 'footer', 'Preview client · Preview reference'), original)
  const updated = updateTemplatePageText(original, footer, 'footer', 'Prepared for {{api:client}} · {{api:reference}}')
  assert.equal(updated.defaults.text.footer, 'Prepared for {{api:client}} · {{api:reference}}')
  assert.equal(original.defaults.text.footer, undefined)
})
