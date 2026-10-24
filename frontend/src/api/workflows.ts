import api from './http'
import type { GeneratedDocument } from './documents'
export type BlockKind = 'START' | 'DOCUMENT' | 'REVIEW' | 'CONDITION' | 'EMAIL' | 'END'
export type WorkflowBlock = { id: string; kind: BlockKind; label: string; x: number; y: number; documentId?: string; note: string; recipient: string; subject: string; protection: 'PLAIN' | 'PASSWORD'; field: string; operator: 'EQUALS' | 'NOT_EQUALS' | 'CONTAINS' | 'GREATER_THAN' | 'IS_SET'; expected: string }
export type WorkflowEdge = { id: string; source: string; target: string; branch: 'NEXT' | 'TRUE' | 'FALSE' }
export type WorkflowInput = { name: string; state: 'DRAFT' | 'READY' | 'ARCHIVED'; published: boolean; revision: number; graph: { nodes: WorkflowBlock[]; edges: WorkflowEdge[] }; documentIds: string[] }
export type Workflow = WorkflowInput & { id: string; ownerId: string; updatedAt: string }
export const workflowsApi = {
  list: () => api.get<Workflow[]>('/admin/workflows').then(r => r.data),
  save: (id: string | undefined, input: WorkflowInput) => (id ? api.put<Workflow>(`/admin/workflows/${id}`, input) : api.post<Workflow>('/admin/workflows', input)).then(r => r.data),
}
export type RunnableWorkflow = { id: string; name: string; revision: number; published: boolean; stepCount: number }
export type RunStatus = 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
export type WorkflowRunSummary = { id: string; name: string; workflowId: string; status: RunStatus; updatedAt: string }
export type WorkflowVisit = { nodeId: string; label: string; kind: BlockKind; outcome: string; note: string; completedAt: string }
export type WorkflowRun = { id: string; name: string; workflowId: string; workflowRevision: number; status: RunStatus; revision: number; current: WorkflowBlock | null; history: WorkflowVisit[]; values: Record<string, string>; documents: GeneratedDocument[]; blockedReason: string | null; emailState: 'NONE' | 'SENDING' | 'UNCERTAIN'; emailAttemptAt: string | null }
export type AdvanceInput = { revision: number; nodeId: string; action: 'CONTINUE' | 'REVIEWED' | 'EVALUATE' | 'CONFIRM_EMAIL'; value?: string; note?: string }
export const workflowRunsApi = {
  available: () => api.get<RunnableWorkflow[]>('/workflows').then(r => r.data),
  list: () => api.get<WorkflowRunSummary[]>('/workflows/runs').then(r => r.data),
  start: (workflowId: string) => api.post<WorkflowRun>('/workflows/runs', { workflowId }).then(r => r.data),
  get: (id: string) => api.get<WorkflowRun>(`/workflows/runs/${id}`).then(r => r.data),
  advance: (id: string, input: AdvanceInput) => api.post<WorkflowRun>(`/workflows/runs/${id}/advance`, input).then(r => r.data),
  useDocument: (id: string, revision: number, nodeId: string, documentId: string) => api.put<WorkflowRun>(`/workflows/runs/${id}/document`, { revision, nodeId, documentId }).then(r => r.data),
  send: (id: string, revision: number, nodeId: string, password: string) => api.post<WorkflowRun>(`/workflows/runs/${id}/email`, { revision, nodeId, password }).then(r => r.data),
  cancel: (id: string, revision: number) => api.post<WorkflowRun>(`/workflows/runs/${id}/cancel`, { revision }).then(r => r.data),
  download: (id: string, documentId: string) => api.get<Blob>(`/workflows/runs/${id}/documents/${documentId}/download`, { responseType: 'blob' }).then(r => r.data),
  page: (id: string, documentId: string, page: number, signal: AbortSignal) => api.get<Blob>(`/workflows/runs/${id}/documents/${documentId}/pages/${page}`, { responseType: 'blob', signal }).then(r => r.data),
}
export const blockLabels: Record<BlockKind, string> = { START: 'Start', DOCUMENT: 'Document', REVIEW: 'Review', CONDITION: 'Condition', EMAIL: 'Email', END: 'End' }
export function newBlock(kind: BlockKind, index: number): WorkflowBlock {
  return { id: `block-${crypto.randomUUID()}`, kind, label: blockLabels[kind], x: 100 + index % 3 * 250, y: 60 + Math.floor(index / 3) * 180, note: '', recipient: '', subject: '', protection: 'PLAIN', field: '', operator: 'EQUALS', expected: '' }
}
