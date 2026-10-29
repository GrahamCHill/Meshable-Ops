import api from '../../../api/http'
import type { GeneratedDocument } from '../documents/api'
export type BlockKind = 'START' | 'DOCUMENT' | 'REVIEW' | 'CONDITION' | 'CODE' | 'AUDIT' | 'EMAIL' | 'END'
export type WorkflowBlock = { id: string; kind: BlockKind; label: string; x: number; y: number; documentId?: string; note: string; recipient: string; subject: string; protection: 'PLAIN' | 'PASSWORD'; field: string; operator: 'EQUALS' | 'NOT_EQUALS' | 'CONTAINS' | 'GREATER_THAN' | 'IS_SET'; expected: string; allowInitiatorReview?: boolean; reviewerIds?: string[]; auditRequired?: boolean; conditionSource?: 'INPUT' | 'VALUE'; code?: string; endOutcome?: 'ALLOW' | 'DENY'; actionSelector?: string; contextField?: string; contextEquals?: string }
export type WorkflowEdge = { id: string; source: string; target: string; branch: 'NEXT' | 'TRUE' | 'FALSE' }
export type WorkflowInput = { name: string; state: 'DRAFT' | 'READY' | 'ARCHIVED'; published: boolean; revision: number; graph: { nodes: WorkflowBlock[]; edges: WorkflowEdge[] }; documentIds: string[] }
export type Workflow = WorkflowInput & { id: string; ownerId: string; updatedAt: string }
export const workflowsApi = {
  list: () => api.get<Workflow[]>('/admin/workflows').then(r => r.data),
  save: (id: string | undefined, input: WorkflowInput) => (id ? api.put<Workflow>(`/admin/workflows/${id}`, input) : api.post<Workflow>('/admin/workflows', input)).then(r => r.data),
  reviewers: () => api.get<{id: string; name: string; email: string}[]>('/admin/workflows/reviewers').then(r => r.data),
  actions: () => api.get<WorkflowActionDescriptor[]>('/admin/workflows/actions').then(r => r.data),
  bindings: () => api.get<WorkflowBinding[]>('/admin/workflows/bindings').then(r => r.data),
  saveBinding: (id: string | undefined, input: WorkflowBindingInput) => (id ? api.put<WorkflowBinding>(`/admin/workflows/bindings/${id}`, input) : api.post<WorkflowBinding>('/admin/workflows/bindings', input)).then(r => r.data),
}
export type WorkflowActionDescriptor = {id: string; label: string; pluginId: string; external: boolean; method: string; route: string; fields: string[]; outputs?: string[]}
export type WorkflowBindingInput = {selector: string; workflowId: string; enabled: boolean; contextField: string; contextEquals: string; revision: number}
export type WorkflowBinding = WorkflowBindingInput & {id: string; diagramManaged?: boolean}
export type RunnableWorkflow = { id: string; name: string; revision: number; published: boolean; stepCount: number; blockedReason?: string }
export type RunStatus = 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED' | 'REJECTED'
export type WorkflowRunSummary = { id: string; name: string; workflowId: string; status: RunStatus; updatedAt: string }
export type WorkflowVisit = { nodeId: string; label: string; kind: BlockKind; outcome: string; note: string; completedAt: string; actorId?: string; audited?: boolean }
export type WorkflowRun = { id: string; name: string; workflowId: string; workflowRevision: number; status: RunStatus; revision: number; current: WorkflowBlock | null; history: WorkflowVisit[]; values: Record<string, string>; documents: GeneratedDocument[]; blockedReason: string | null; emailState: 'NONE' | 'SENDING' | 'UNCERTAIN'; emailAttemptAt: string | null; canAdvance?: boolean; isOwner?: boolean }
export type AdvanceInput = { revision: number; nodeId: string; action: 'CONTINUE' | 'REVIEWED' | 'REJECT' | 'EVALUATE' | 'CONFIRM_EMAIL'; value?: string; note?: string }
export const workflowRunsApi = {
  available: () => api.get<RunnableWorkflow[]>('/workflows').then(r => r.data),
  list: () => api.get<WorkflowRunSummary[]>('/workflows/runs').then(r => r.data),
  reviews: () => api.get<WorkflowRunSummary[]>('/workflows/reviews').then(r => r.data),
  start: (workflowId: string) => api.post<WorkflowRun>('/workflows/runs', { workflowId }).then(r => r.data),
  get: (id: string) => api.get<WorkflowRun>(`/workflows/runs/${id}`).then(r => r.data),
  advance: (id: string, input: AdvanceInput) => api.post<WorkflowRun>(`/workflows/runs/${id}/advance`, input).then(r => r.data),
  useDocument: (id: string, revision: number, nodeId: string, documentId: string) => api.put<WorkflowRun>(`/workflows/runs/${id}/document`, { revision, nodeId, documentId }).then(r => r.data),
  send: (id: string, revision: number, nodeId: string, password: string, note = '') => api.post<WorkflowRun>(`/workflows/runs/${id}/email`, { revision, nodeId, password, note }).then(r => r.data),
  cancel: (id: string, revision: number) => api.post<WorkflowRun>(`/workflows/runs/${id}/cancel`, { revision }).then(r => r.data),
  download: (id: string, documentId: string) => api.get<Blob>(`/workflows/runs/${id}/documents/${documentId}/download`, { responseType: 'blob' }).then(r => r.data),
  page: (id: string, documentId: string, page: number, signal: AbortSignal) => api.get<Blob>(`/workflows/runs/${id}/documents/${documentId}/pages/${page}`, { responseType: 'blob', signal }).then(r => r.data),
}
export const blockLabels: Record<BlockKind, string> = { START: 'Start', DOCUMENT: 'Document', REVIEW: 'Approval', CONDITION: 'Condition', CODE: 'Code', AUDIT: 'Audit', EMAIL: 'Email', END: 'End' }
export function newBlock(kind: BlockKind, index: number): WorkflowBlock {
  return { id: `block-${crypto.randomUUID()}`, kind, label: blockLabels[kind], x: 100 + index % 3 * 250, y: 60 + Math.floor(index / 3) * 180, note: '', recipient: '', subject: '', protection: 'PLAIN', field: '', operator: 'EQUALS', expected: '', allowInitiatorReview: true, reviewerIds: [], auditRequired: kind === 'AUDIT', conditionSource: 'INPUT', code: kind === 'CODE' ? 'def run(context):\n    return {"approved": True}' : '' }
}
