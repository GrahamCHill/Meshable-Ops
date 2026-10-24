import api from './http'
export type BlockKind = 'START' | 'DOCUMENT' | 'REVIEW' | 'CONDITION' | 'EMAIL' | 'END'
export type WorkflowBlock = { id: string; kind: BlockKind; label: string; x: number; y: number; documentId?: string; note: string; recipient: string; subject: string; protection: 'PLAIN' | 'PASSWORD'; field: string; operator: 'EQUALS' | 'NOT_EQUALS' | 'CONTAINS' | 'GREATER_THAN' | 'IS_SET'; expected: string }
export type WorkflowEdge = { id: string; source: string; target: string; branch: 'NEXT' | 'TRUE' | 'FALSE' }
export type WorkflowInput = { name: string; state: 'DRAFT' | 'READY' | 'ARCHIVED'; revision: number; graph: { nodes: WorkflowBlock[]; edges: WorkflowEdge[] }; documentIds: string[] }
export type Workflow = WorkflowInput & { id: string; ownerId: string; updatedAt: string }
export const workflowsApi = {
  list: () => api.get<Workflow[]>('/workflows').then(r => r.data),
  save: (id: string | undefined, input: WorkflowInput) => (id ? api.put<Workflow>(`/workflows/${id}`, input) : api.post<Workflow>('/workflows', input)).then(r => r.data),
}
export const blockLabels: Record<BlockKind, string> = { START: 'Start', DOCUMENT: 'Document', REVIEW: 'Review', CONDITION: 'Condition', EMAIL: 'Email', END: 'End' }
export function newBlock(kind: BlockKind, index: number): WorkflowBlock {
  return { id: `block-${crypto.randomUUID()}`, kind, label: blockLabels[kind], x: 100 + index % 3 * 250, y: 60 + Math.floor(index / 3) * 180, note: '', recipient: '', subject: '', protection: 'PLAIN', field: '', operator: 'EQUALS', expected: '' }
}
