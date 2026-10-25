import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import test from 'node:test'
import { build } from 'esbuild'

// Render the actual React components in memory; no browser, API, or saved records.
const bundle = await build({
  stdin: {
    contents: `
      import React from 'react';
      import {renderToStaticMarkup} from 'react-dom/server';
      import {TemplateDesigner} from './src/vendor/weasycanvas/src/TemplateDesigner';
      import {defaultTemplate, clone} from './src/vendor/weasycanvas/src/templates';
      export {defaultTemplate};
      export function render(patch = {}, values = {}) {
        const template = {...clone(defaultTemplate), ...patch};
        return renderToStaticMarkup(<TemplateDesigner template={template} apiValues={values}
          onChange={() => {}} onSave={() => {}} onUse={() => {}} onExport={() => {}} onImport={() => {}} />);
      }
    `,
    resolveDir: fileURLToPath(new URL('..', import.meta.url)),
    loader: 'tsx',
  },
  bundle: true, platform: 'node', format: 'cjs', write: false,
  define: { 'process.env.NODE_ENV': '"production"' },
})
const compiledModule = { exports: {} }
new Function('require', 'module', 'exports', bundle.outputFiles[0].text)(createRequire(import.meta.url), compiledModule, compiledModule.exports)
const { render, defaultTemplate } = compiledModule.exports

test('the default rendered page exposes editable document text and term text', () => {
  const html = render()
  for (const label of ['Organisation', 'Document title', 'Reference', 'Recipient', 'Closing text', 'Footer', 'Terms — Term 1', 'Terms — Subterm 1a']) {
    assert.ok(html.includes(`<textarea aria-label="${label}"`), label + ' should be editable on the page')
  }
  assert.ok(html.includes('aria-pressed="true">Edit page</button>'))
  assert.ok(html.includes('>Manage terms</button>'))
  assert.ok(html.includes('template-render-page'))
})

test('the rendered page does not truncate terms after the old preview limit', () => {
  const html = render({ defaults: {
    organization: '', title: 'Long agreement', reference: '', recipient: '', closing_text: '',
    paragraphs: Array.from({ length: 15 }, (_, i) => 'Complete wording for term ' + (i + 1)),
    subparagraphs: Array.from({ length: 15 }, () => []),
  } })
  assert.ok(html.includes('aria-label="Terms — Term 15"'))
  assert.ok(html.includes('Complete wording for term 15</textarea>'))
  assert.ok(!html.includes('more clauses in this section'))
})

test('a bound title stays read-only and displays its preview value', () => {
  const html = render({
    sections: [
      { kind: 'title', label: 'Title', required: true, locked: false, maxLength: 100, bindings: { text: 'client' } },
      { kind: 'body', label: 'Terms', required: true, locked: false, maxLength: 4000 },
    ],
    apiFields: [{ key: 'client', label: 'Client', required: false, maxLength: 100 }],
  }, { client: 'Preview client' })
  assert.match(html, /data-api-field="client"[^>]*aria-readonly="true"[^>]*>Preview client<\/div>/)
  assert.ok(!html.includes('<textarea aria-label="Document title"'))
})

const contentSections = [
  { kind: 'text', id: 'intro', label: 'Introduction', required: true, locked: false, maxLength: 4000 },
  { kind: 'body', id: 'terms', label: 'Terms', required: true, locked: false, maxLength: 4000 },
]
const contentDefaults = {
  ...defaultTemplate.defaults, paragraphs: ['Introductory wording', 'A contractual term'],
  paragraphSectionIds: ['intro', 'terms'], subparagraphs: [[], []],
}

test('multiple required content sections do not force headings from their editor labels', () => {
  for (const sectionHeadings of [undefined, { intro: '', terms: '  \n' }]) {
    const html = render({ sections: contentSections, defaults: { ...contentDefaults, text: { sectionHeadings } } })
    assert.ok(!html.includes('class="template-render-heading"'))
    assert.ok(html.includes('Introductory wording</textarea>'))
    assert.ok(html.includes('A contractual term</textarea>'))
    assert.ok(html.includes('Section heading (optional)'))
  }
})

test('an explicit heading is editable even with a single content section', () => {
  const html = render({ defaults: { ...defaultTemplate.defaults, text: { sectionHeadings: { body: 'Commercial terms' } } } })
  assert.ok(html.includes('class="template-render-heading"'))
  assert.match(html, /<textarea aria-label="Terms heading"[^>]*>Commercial terms<\/textarea>/)
})

test('a single section heading can use protected API values', () => {
  const html = render({
    sections: [{ ...contentSections[1], bindings: { heading: 'heading' } }],
    defaults: { ...defaultTemplate.defaults, paragraphSectionIds: ['terms', 'terms'] },
    apiFields: [{ key: 'heading', label: 'Heading', required: false, maxLength: 80 }],
  }, { heading: 'Payment terms' })
  assert.match(html, /data-api-field="heading"[^>]*aria-readonly="true"[^>]*>Payment terms<\/div>/)
})

test('page terms use compact defaults and respect the saved paragraph gap and line spacing', () => {
  const html = render()
  assert.ok(html.includes('data-depth="0" style="margin-bottom:8px"'))
  assert.ok(html.includes('line-height:1.2'))
  const custom = render({ layout: { ...defaultTemplate.layout, paragraphGap: 4 }, formatting: {
    ...defaultTemplate.formatting, body: { ...defaultTemplate.formatting.body, lineHeight: 1.15 },
  } })
  assert.ok(custom.includes('data-depth="0" style="margin-bottom:4px"'))
  assert.ok(custom.includes('line-height:1.15'))
})
