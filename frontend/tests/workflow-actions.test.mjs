import {readFile} from 'node:fs/promises'
import assert from 'node:assert/strict'
import test from 'node:test'
import ts from 'typescript'
const source=await readFile(new URL('../src/lib/workflowActions.ts',import.meta.url),'utf8')
const compiled=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.ES2022,target:ts.ScriptTarget.ES2020}}).outputText
const {workflowActions,completeRequiredWorkflow}=await import('data:text/javascript;base64,'+Buffer.from(compiled).toString('base64'))
const detail={runId:'run',workflowRequestId:'approval',workflowName:'Review',message:'Review required'}

test('parallel pending actions require separate explicit resumption even for the same prerequisite run',async()=>{
  let resolved=0
  const first=completeRequiredWorkflow(detail).then(()=>resolved++)
  const second=completeRequiredWorkflow(detail).then(()=>resolved++)
  const [one,two]=workflowActions.snapshot()
  assert.equal(resolved,0)
  one.resume();one.resume();one.cancel()
  await first
  assert.equal(resolved,1)
  assert.deepEqual(workflowActions.snapshot(),[two])
  two.resume();await second
  assert.equal(resolved,2);assert.deepEqual(workflowActions.snapshot(),[])
})

test('aborting one request and logout cancellation cannot leave a resumable request behind',async()=>{
  const abort=new AbortController()
  const first=completeRequiredWorkflow(detail,abort.signal).catch(error=>error.message)
  const second=completeRequiredWorkflow(detail).catch(error=>error.message)
  const original=workflowActions.snapshot()
  abort.abort()
  assert.equal(workflowActions.snapshot().length,1)
  assert.match(await first,/cancelled/)
  workflowActions.cancelAll()
  assert.match(await second,/cancelled/)
  original.forEach(action=>action.resume())
  assert.deepEqual(workflowActions.snapshot(),[])
})

test('an already aborted request never enters the action dialog',async()=>{
  const abort=new AbortController();abort.abort()
  await assert.rejects(completeRequiredWorkflow(detail,abort.signal),/cancelled/)
  assert.deepEqual(workflowActions.snapshot(),[])
})
