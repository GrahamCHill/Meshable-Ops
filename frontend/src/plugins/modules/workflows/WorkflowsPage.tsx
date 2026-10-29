import { useAccess } from '../../../api/access'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { workflowRunsApi, type WorkflowRun } from './api'
import { useAuthStore } from '../../../hooks/useAuthStore'
import { WorkflowRunPanel } from './components/WorkflowRunPanel'
import { getErrorMessage } from '../../../components/admin/adminUtils'
import { Button, Spinner } from '../../../components/ui/index'

export default function WorkflowsPage() {
  const access = useAccess()
  const user = useAuthStore(state => state.user)
  const client = useQueryClient(); const [params, setParams] = useSearchParams()
  const [busy, setBusy] = useState(false); const [error, setError] = useState('')
  const catalogue = useQuery({ queryKey: ['workflow-catalogue', user?.id, user?.role], queryFn: workflowRunsApi.available })
  const runs = useQuery({ queryKey: ['workflow-runs', user?.id], queryFn: workflowRunsApi.list })
  const reviews = useQuery({queryKey: ['workflow-reviews', user?.id], queryFn: workflowRunsApi.reviews, refetchInterval: 15000})
  const id = params.get('run')
  const run = useQuery({ queryKey: ['workflow-run', user?.id, id], queryFn: () => workflowRunsApi.get(id!), enabled: !!id, refetchOnWindowFocus: true })
  const update = (value: WorkflowRun) => { client.setQueryData(['workflow-run', user?.id, value.id], value); void client.invalidateQueries({ queryKey: ['workflow-runs', user?.id] }); void client.invalidateQueries({queryKey: ['workflow-reviews']}) }
  const select = (value?: string) => { setError(''); setParams(current => { const next = new URLSearchParams(current); if (value) next.set('run', value); else next.delete('run'); return next }) }
  const start = async (workflowId: string) => {
    setBusy(true); setError('')
    try { const value = await workflowRunsApi.start(workflowId); update(value); select(value.id) }
    catch (failure) { setError(getErrorMessage(failure, 'Unable to start this workflow.')) }
    finally { setBusy(false) }
  }
  return <div className="page-stack" style={{ maxWidth: 1000 }}>
    <div className="page-heading"><div><h1>Workflows</h1><p>Follow a process one step at a time. Your progress is saved to your account.</p></div>{access.can('admin.workflows') && <Link className="button button-secondary button-sm" to="/admin/workflows">Workflow builder</Link>}</div>
    {params.has('attach') && <p role="status">Choose a workflow, then select your PDF when you reach a document or email step.</p>}
    {error && <p role="alert">{error}</p>}
    {id ? <>
      <div className="inline-actions"><Button variant="ghost" onClick={() => select()}>Back to workflows</Button><Button variant="ghost" disabled={run.isFetching} onClick={() => void run.refetch()}>Reload progress</Button></div>
      {run.isLoading && <Spinner />}{run.isError && <p role="alert">Unable to load this run. It may belong to another account.</p>}
      {run.data && <WorkflowRunPanel key={`${run.data.id}-${run.data.revision}`} run={run.data} suggestedDocument={params.get('attach') ?? undefined} onChange={update} onReload={() => { void run.refetch(); void client.invalidateQueries({ queryKey: ['workflow-runs', user?.id] }) }} />}
    </> : <>
      <section className="workflow-review-inbox page-stack"><div className="page-heading"><h2>Your review inbox</h2><span className="workflow-status">{reviews.data?.length ?? 0} awaiting approval</span></div>
        {reviews.isError && <p role="alert">Unable to load assigned reviews.</p>}
        {reviews.data?.length === 0 && <p className="field-help">No assigned reviews are waiting.</p>}
        {reviews.data?.map(review => <div className="inline-actions" key={review.id}><div style={{flex: 1}}><h3>{review.name}</h3><small>Approval requested · {new Date(review.updatedAt).toLocaleString()}</small></div><Button variant="secondary" onClick={() => select(review.id)}>Review</Button></div>)}
      </section>
      <h2>Start a workflow</h2>
      {catalogue.isLoading && <Spinner />}{catalogue.isError && <p role="alert">Unable to load available workflows.</p>}{catalogue.data?.length === 0 && <p>No workflows have been published yet.</p>}
      {catalogue.data?.map(workflow => <section key={workflow.id} className="page-stack"><div className="inline-actions"><div style={{ flex: 1 }}><h3>{workflow.name}</h3><p className="field-help">{workflow.stepCount} steps in this design{!workflow.published && ' · Private admin preview'}</p>{workflow.blockedReason&&<p role="status">{workflow.blockedReason}</p>}</div><Button disabled={busy||!!workflow.blockedReason} onClick={() => void start(workflow.id)}>Start workflow</Button></div></section>)}
      <h2>Your runs</h2><p className="field-help">Your latest 100 runs. Resume a process or look back at its completed steps.</p>
      {runs.isLoading && <Spinner />}{runs.isError && <p role="alert">Unable to load your workflow runs.</p>}{runs.data?.length === 0 && <p>You have not started a workflow yet.</p>}
      {runs.data?.map(value => <div key={value.id} className="inline-actions"><div style={{ flex: 1 }}><h3>{value.name}</h3><small>{value.status.toLowerCase().replace(/_/g, ' ')} · {new Date(value.updatedAt).toLocaleString()}</small></div><Button variant="secondary" onClick={() => select(value.id)}>{value.status === 'IN_PROGRESS' ? 'Resume' : 'View progress'}</Button></div>)}
    </>}
  </div>
}
