import { useState } from 'react'
import { workflowRunsApi, type AdvanceInput, type WorkflowRun } from '../api'
import { WorkflowRunDocuments } from './WorkflowRunDocuments'
import { WorkflowEmailStep } from './WorkflowEmailStep'
import { WorkflowStepForm } from './WorkflowStepForm'
import { getErrorMessage } from '../../../../components/admin/adminUtils'
import { Button } from '../../../../components/ui/index'

export type RunAction = (operation: () => Promise<WorkflowRun>) => Promise<void>
export function WorkflowRunPanel({ run, suggestedDocument, onChange, onReload }: { run: WorkflowRun; suggestedDocument?: string; onChange: (run: WorkflowRun) => void; onReload: () => void }) {
  const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [cancel, setCancel] = useState(false)
  const [selected,setSelected]=useState(run.current?.id ?? '')
  const ready=run.readyNodes?.length ? run.readyNodes : run.current && run.current.kind!=='WEBHOOK' ? [run.current] : []
  const current=ready.find(block => block.id===selected) ?? ready[0] ?? run.current
  const canAdvance=run.advanceableNodeIds ? !!current && run.advanceableNodeIds.includes(current.id) : run.canAdvance!==false
  const focusedRun={...run,current,canAdvance}
  const act: RunAction = async operation => {
    setBusy(true); setError('')
    try { onChange(await operation()) }
    catch (failure) { setError(getErrorMessage(failure, 'Unable to save progress. Reload and try again.')); onReload() }
    finally { setBusy(false) }
  }
  const advance = (input: Omit<AdvanceInput, 'revision' | 'nodeId'>) => act(() => workflowRunsApi.advance(run.id, { ...input, revision: run.revision, nodeId: current!.id }))
  const emailBusy = run.emailState === 'SENDING' && (!run.emailAttemptAt || Date.now() - Date.parse(run.emailAttemptAt) < 120000)
  return <section className="page-stack" aria-label="Workflow progress">
    <div><h2>{run.name}</h2><p className="field-help">Design revision {run.workflowRevision} · {run.status.toLowerCase().replace(/_/g, ' ')} · {run.history.length} steps completed</p></div>
    {error && <p role="alert">{error}</p>}
    {run.blockedReason && <p role="alert">{run.blockedReason} Your saved history is retained.</p>}
    {run.status === 'COMPLETED' && <p role="status">Workflow complete.</p>}
    {run.status === 'CANCELLED' && <p role="status">This run was cancelled.</p>}
    {run.status === 'REJECTED' && <p role="alert">This workflow rejected the action. It cannot continue.</p>}
    {Object.keys(run.values).some(key => key.startsWith('export.')) && <details open className="workflow-trigger-context"><summary>Proposed export options</summary><dl>{Object.entries(run.values).filter(([key]) => key.startsWith('export.')).map(([key,value]) => <div key={key}><dt>{key}</dt><dd>{value || '—'}</dd></div>)}</dl></details>}
    {Object.keys(run.values).some(key => key.startsWith('trigger.')) && <details className="workflow-trigger-context"><summary>Action context</summary><dl>{Object.entries(run.values).filter(([key]) => key.startsWith('trigger.')).map(([key,value]) => <div key={key}><dt>{key.replace('trigger.','')}</dt><dd>{value || '—'}</dd></div>)}</dl></details>}
    {run.isOwner!==false && <p className="field-help">Run ID: <code>{run.id}</code>{run.reference && <> · Business reference: <code>{run.reference}</code></>}</p>}
    {ready.length>1 && <label className="form-field">Ready branches<select value={current?.id ?? ''} disabled={busy || emailBusy} onChange={event => setSelected(event.target.value)}>{ready.map(block => <option key={block.id} value={block.id}>{block.label}</option>)}</select><small>All branches remain part of this run. Complete each ready step or wait for its assigned reviewer.</small></label>}
    {!!run.waitingNodes?.length && <section className="workflow-waiting page-stack"><h3>Waiting for inputs</h3>{run.waitingNodes.map(node => <article key={node.id}><strong>{node.label}</strong>{node.kind==='WEBHOOK' ? <><p>Waiting for authenticated webhook event <code>{node.event}</code>.</p><small>Endpoint: <code>{node.endpointId}</code>. Send <code>data.runId = {run.id}</code>{run.reference && <> or <code>data.workflowId = {run.workflowId}</code> with <code>data.reference = {run.reference}</code></>}.</small></> : <p>{node.received} of {node.required} inputs received. Waiting for: {node.missing.join(', ')}.</p>}</article>)}</section>}
    {!canAdvance && current?.kind === 'REVIEW' && <p role="status" className="workflow-action-notice">Waiting for an assigned reviewer. This step requires someone else to approve; your progress is saved.</p>}
    {current && run.status === 'IN_PROGRESS' && !run.blockedReason && <div className="page-stack">
      <h3>Current step: {current.label}</h3>
      {current.note && current.kind !== 'EMAIL' && <p style={{ whiteSpace: 'pre-wrap' }}>{current.note}</p>}
      {(current.kind === 'DOCUMENT' || current.kind === 'EMAIL') && <WorkflowRunDocuments key={current.id} run={focusedRun} suggestedDocument={suggestedDocument} busy={busy} act={act} />}
      {canAdvance && current.kind!=='WEBHOOK' && (current.kind === 'EMAIL' ? <WorkflowEmailStep key={current.id} run={focusedRun} busy={busy} act={act} advance={advance} /> : <WorkflowStepForm key={current.id} run={focusedRun} busy={busy} advance={advance} />)}
    </div>}
    {run.history.length > 0 && <div><h3>Completed steps</h3><ol>{run.history.map(visit => <li key={visit.nodeId} style={{ marginBottom: 12 }}><strong>{visit.label}</strong> · {visit.outcome.toLowerCase().replace(/_/g, ' ')}<br /><small>{new Date(visit.completedAt).toLocaleString()}{visit.audited && ' · Audited'}{visit.actorId && ` · Actor ${visit.actorId}`}</small>{visit.note && <p style={{ whiteSpace: 'pre-wrap' }}>{visit.note}</p>}</li>)}</ol></div>}
    {run.status === 'IN_PROGRESS' && run.isOwner !== false && <div className="page-stack"><label className="checkbox-label"><input type="checkbox" checked={cancel} disabled={busy || emailBusy} onChange={e => setCancel(e.target.checked)} />Cancel this run and keep its history</label>{cancel && <Button variant="secondary" disabled={busy || emailBusy} onClick={() => void act(() => workflowRunsApi.cancel(run.id, run.revision))}>Confirm cancellation</Button>}</div>}
  </section>
}
