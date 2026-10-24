import api from '../../../api/http'
import type { DocumentTemplate } from '../../../vendor/weasycanvas/src/templates'

export type FieldRule = { key: string; type: 'TEXT' | 'MULTILINE' | 'NUMBER' | 'MONEY' | 'DATE' | 'EMAIL' | 'DERIVED'; operation?: 'ADD' | 'SUBTRACT' | 'MULTIPLY' | 'DIVIDE' | 'PERCENT'; inputs: string[] }
export function isFieldRules(value: unknown): value is FieldRule[] {
  return Array.isArray(value) && value.length <= 50 && value.every(rule => rule && typeof rule === 'object' && typeof rule.key === 'string' &&
    ['TEXT','MULTILINE','NUMBER','MONEY','DATE','EMAIL','DERIVED'].includes(rule.type) && Array.isArray(rule.inputs) && rule.inputs.length <= 2 && rule.inputs.every((key: unknown) => typeof key === 'string') &&
    (rule.operation === undefined || ['ADD','SUBTRACT','MULTIPLY','DIVIDE','PERCENT'].includes(rule.operation)))
}
export type AppTemplate = { id: string; name: string; category: string; published: boolean; revision: number; updatedAt: string; template: DocumentTemplate; fieldRules: FieldRule[] }
export type TemplateInput = Omit<AppTemplate, 'id' | 'updatedAt'>
export type GeneratedDocument = { id: string; ownerId: string; templateId: string; templateRevision: number; templateName: string; name: string; filename: string; pageCount: number; byteSize: number; createdAt: string }
export type EmailDocumentInput = { recipient: string; subject: string; text: string; protection: 'PLAIN' | 'PASSWORD'; password?: string }
export const documentsApi = {
  templates: () => api.get<AppTemplate[]>('/documents/templates').then(r => r.data),
  adminTemplates: () => api.get<AppTemplate[]>('/admin/document-templates').then(r => r.data),
  saveTemplate: (id: string | undefined, body: TemplateInput) => (id ? api.put<AppTemplate>(`/admin/document-templates/${id}`, body) : api.post<AppTemplate>('/admin/document-templates', body)).then(r => r.data),
  list: () => api.get<GeneratedDocument[]>('/documents').then(r => r.data),
  generate: (templateId: string, name: string, values: Record<string, string>) => api.post<GeneratedDocument>('/documents', { templateId, name, values }).then(r => r.data),
  download: (id: string) => api.get<Blob>(`/documents/${id}/download`, { responseType: 'blob' }).then(r => r.data),
  page: (id: string, page: number, signal: AbortSignal) => api.get<Blob>(`/documents/${id}/pages/${page}`, { responseType: 'blob', signal }).then(r => r.data),
  email: (id: string, input: EmailDocumentInput) => api.post<{ status: string; message: string }>(`/documents/${id}/email`, input).then(r => r.data),
}
export function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = filename; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000)
}
