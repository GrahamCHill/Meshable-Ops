import { useState } from 'react'
import { workflowRunsApi, type AdvanceInput, type WorkflowRun } from '../../api/workflows'
import { WorkflowRunDocuments } from './WorkflowRunDocuments'
import { WorkflowEmailStep } from './WorkflowEmailStep'
import { WorkflowStepForm } from './WorkflowStepForm'
import { getErrorMessage } from '../admin/adminUtils'
import { Button } from '../ui'

export type RunAction = (operation: () => Promise<WorkflowRun>) => Promise<void>
export function WorkflowRunPanel({ run, suggestedDocument, onChange, onReload }: { run: WorkflowRun; suggestedDocument?: string; onChange: (run: WorkflowRun) => void; onReload: () => void }) {
  const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [cancel, setCancel] = useState(false)
  const current = run.current
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
    {current && run.status === 'IN_PROGRESS' && !run.blockedReason && <div className="page-stack">
      <h3>Current step: {current.label}</h3>
      {current.note && current.kind !== 'EMAIL' && <p style={{ whiteSpace: 'pre-wrap' }}>{current.note}</p>}
      {(current.kind === 'DOCUMENT' || current.kind === 'EMAIL') && <WorkflowRunDocuments run={run} suggestedDocument={suggestedDocument} busy={busy} act={act} />}
      {current.kind === 'EMAIL' ? <WorkflowEmailStep run={run} busy={busy} act={act} advance={advance} /> : <WorkflowStepForm run={run} busy={busy} advance={advance} />}
    </div>}
    {run.history.length > 0 && <div><h3>Completed steps</h3><ol>{run.history.map(visit => <li key={visit.nodeId} style={{ marginBottom: 12 }}><strong>{visit.label}</strong> · {visit.outcome.toLowerCase().replace(/_/g, ' ')}<br /><small>{new Date(visit.completedAt).toLocaleString()}</small>{visit.note && <p style={{ whiteSpace: 'pre-wrap' }}>{visit.note}</p>}</li>)}</ol></div>}
    {run.status === 'IN_PROGRESS' && <div className="page-stack"><label className="checkbox-label"><input type="checkbox" checked={cancel} disabled={busy || emailBusy} onChange={e => setCancel(e.target.checked)} />Cancel this run and keep its history</label>{cancel && <Button variant="secondary" disabled={busy || emailBusy} onClick={() => void act(() => workflowRunsApi.cancel(run.id, run.revision))}>Confirm cancellation</Button>}</div>}
  </section>
}
