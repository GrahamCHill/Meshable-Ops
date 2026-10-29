export type RequiredWorkflow = {runId: string; workflowRequestId: string; workflowName: string; message: string}
export type PendingWorkflowAction = RequiredWorkflow & {resume: () => void; cancel: () => void}
let pending: PendingWorkflowAction[] = []
const listeners = new Set<() => void>()
const notify = () => listeners.forEach(listener => listener())
export const workflowActions = {
  subscribe(listener: () => void) { listeners.add(listener); return () => { listeners.delete(listener) } },
  snapshot: () => pending,
  cancelAll() { [...pending].forEach(action => action.cancel()) },
}
// Keep original requests in memory only: request bodies can contain PDF passwords or other private input.
export function completeRequiredWorkflow(detail: RequiredWorkflow, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    let settled = false
    const finish = (approved: boolean) => {
      if (settled) return
      settled = true; signal?.removeEventListener('abort', abort)
      pending = pending.filter(item => item !== action); notify()
      if (approved) resolve(); else reject(new Error('Action cancelled. Workflow progress remains saved.'))
    }
    const abort = () => finish(false)
    const action: PendingWorkflowAction = {...detail, resume: () => finish(true), cancel: () => finish(false)}
    if (signal?.aborted) { reject(new Error('Action cancelled')); return }
    signal?.addEventListener('abort', abort, {once: true})
    pending = [...pending, action]; notify()
  })
}
