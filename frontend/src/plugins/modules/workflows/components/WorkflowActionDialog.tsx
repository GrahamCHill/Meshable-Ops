import {useEffect, useSyncExternalStore} from 'react'
import {useQuery, useQueryClient} from '@tanstack/react-query'
import {workflowActions} from '../../../../lib/workflowActions'
import {useAuthStore} from '../../../../hooks/useAuthStore'
import {Modal} from '../../../../components/ui/Modal'
import {Button, Spinner} from '../../../../components/ui'
import {workflowRunsApi} from '../api'
import {WorkflowRunPanel} from './WorkflowRunPanel'
import '../workflows.css'

export default function WorkflowActionDialog() {
  const actions = useSyncExternalStore(workflowActions.subscribe, workflowActions.snapshot)
  const action = actions[0]
  const userId = useAuthStore(state => state.user?.id)
  const client = useQueryClient()
  useEffect(() => () => workflowActions.cancelAll(), [userId])
  const run = useQuery({queryKey: ['workflow-action-run', userId, action?.runId], queryFn: () => workflowRunsApi.get(action!.runId),
    enabled: !!action, refetchInterval: query => query.state.data?.status === 'IN_PROGRESS' ? 3000 : false})
  if (!action) return null
  return <Modal title="Workflow required" onClose={action.cancel} className="project-dialog workflow-action-dialog">
    <p className="workflow-action-notice">{action.message}. The original action is paused. Completing this process does not send or change anything until you select Continue action.</p>
    {run.isLoading && <Spinner/>}
    {run.isError && <p role="alert">The workflow could not be loaded. <Button variant="ghost" onClick={() => void run.refetch()}>Retry</Button></p>}
    {run.data && <WorkflowRunPanel key={`${run.data.id}-${run.data.revision}`} run={run.data}
      onChange={value => client.setQueryData(['workflow-action-run', userId, value.id], value)} onReload={() => void run.refetch()}/>}
    <div className="workflow-action-footer"><Button variant="secondary" onClick={action.cancel}>Cancel action</Button>
      <Button disabled={run.data?.status !== 'COMPLETED' || !!run.data?.blockedReason} onClick={action.resume}>Continue action</Button></div>
  </Modal>
}
