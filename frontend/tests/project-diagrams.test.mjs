import {readFile} from 'node:fs/promises'
import assert from 'node:assert/strict'
import test from 'node:test'
import ts from 'typescript'
const source=await readFile(new URL('../src/plugins/modules/mermaid/mermaidSource.ts',import.meta.url),'utf8')
const compiled=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.ES2022,target:ts.ScriptTarget.ES2020}}).outputText
const {diagramSourceError}=await import('data:text/javascript;base64,'+Buffer.from(compiled).toString('base64'))
test('ordinary diagrams and URL text labels remain supported',()=>{
  assert.equal(diagramSourceError('flowchart LR\n A[Request] --> B[Review]'),undefined)
  assert.equal(diagramSourceError('flowchart LR\n A[https://docs.example.test] --> B[Review]'),undefined)
})
test('runtime settings cannot loosen renderer security and oversized sources are rejected',()=>{
  assert.ok(diagramSourceError('%%{init: {securityLevel: loose}}%%\nflowchart LR\n A --> B'))
  assert.ok(diagramSourceError('---\nconfig:\n securityLevel: loose\n---\nflowchart LR\n A --> B'))
  assert.ok(diagramSourceError('x'.repeat(20001)))
})
test('resource-loading syntax is rejected before Mermaid can measure labels in the host page',()=>{
  for(const source of ['flowchart LR\n A@{img: "https://tracker.example.test/pixel"}', 'flowchart LR\n A["![image](https://tracker.example.test/pixel)"]', 'flowchart LR\n A --> B\n classDef x fill:url(https://tracker.example.test/pixel)', 'flowchart LR\n A["<img src=x>"]'])assert.ok(diagramSourceError(source))
})
