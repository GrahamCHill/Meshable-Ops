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
const { render } = compiledModule.exports

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
