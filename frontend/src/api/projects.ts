import api from './http'
import type {Artwork} from './artwork'

export type ProjectCategory = string
export interface ProjectSection {id:string;name:string}
export type ProjectRole = 'MEMBER' | 'TEAM_LEAD' | 'MANAGER'
export interface Person {id: string; name: string; email: string}
export interface WorkBreakdown {id:string;label:string;count:number;completed?:number}
export interface WorkSummary {total:number;completed:number;overdue:number;storyPoints:number;stages:WorkBreakdown[];assignees:WorkBreakdown[];epics:WorkBreakdown[];tags:WorkBreakdown[]}
export interface Contact {id: string; name: string; email: string; phone: string; position: string; role: string}
export interface WebLink {label: string; url: string}
export interface StorageLink {id: string; folderId: string; path: string; directory: boolean; label: string}
export interface ProjectDetails {description: string; members: {userId: string; role: ProjectRole}[]; groupIds: string[]; tagIds: string[]; contacts: Contact[]; contactSource: 'CLIENT' | 'PROJECT'; links: WebLink[]; storage: StorageLink[]}
export interface ProjectInput {name: string; leadId: string; clientId: string | null; category: ProjectCategory; details: ProjectDetails; revision: number}
export interface Project extends ProjectInput {id: string; clientName: string; categoryName: string; inheritedContacts: Contact[]; effectiveContacts: Contact[]; canManage: boolean; updatedAt: string}
export interface ProjectSummary {id: string; name: string; clientId: string | null; clientName: string; category: ProjectCategory; categoryName: string; tagIds: string[]; updatedAt: string; artwork:Artwork}
export interface ClientInput {name: string; details: {description: string; contacts: Contact[]; links: WebLink[]}; revision: number}
export interface Client extends ClientInput {id: string; canManage: boolean; logo: Artwork}
export interface ProjectTag {id: string; name: string; description: string; groupIds: string[]}
export interface WorkGroup {id: string; name: string; description: string; kind: 'TEAM' | 'DEPARTMENT'; projectRole: ProjectRole; details: {memberIds: string[]; tagIds: string[]}; revision: number}
export interface ProjectSettings {settings: {tags: ProjectTag[]; sections: ProjectSection[]; defaultSectionId: string}; revision: number}
export interface ProjectOptions {canCreateProjects: boolean; canCreateClients: boolean; sections: ProjectSection[]; defaultSectionId: string; tags: ProjectTag[]; groups: Omit<WorkGroup, 'details' | 'revision'>[]; identityName: string}
export interface ContextExtension {pluginId: string; label: string; href: string; panels: {title: string; text: string}[]}
export interface ProjectReport {id: string; kind: string; from: string; to: string; createdAt: string; deliveries: Record<string, {status: string; message: string}>}
export interface ReportInput {kind: 'SPRINT' | 'MONTHLY' | 'PERIOD'; from: string; to: string; sprintId: string | null; boardId: string | null; sendEmail: boolean; recipientIds: string[]}
export interface DigestInput {from: string; to: string; timeZone: string; includeAi: boolean; recipientIds: string[]}
export interface DigestPreview {recipientId: string; text: string; aiSummary: string | null; calendarAvailable: boolean}
export interface Risk {userId: string; name: string; score: number; overdue: number; blocked: number; inProgress: number; dueSoon: number; explanation: string}
export interface ScheduleInput {enabled: boolean; kind: 'REPORT' | 'DIGEST'; cadence: 'DAILY' | 'WEEKLY' | 'MONTHLY'; time: string; timeZone: string; dayOfWeek: number; dayOfMonth: number; reportKind: 'SPRINT' | 'MONTHLY' | 'PERIOD'; recipientIds: string[]; includeAi: boolean; revision: number}
export interface WorkSchedule {id: string; projectId: string | null; configuration: ScheduleInput; nextRunAt: string | null; lastRunAt: string | null; lastStatus: string | null}
export const emptyDetails = (): ProjectDetails => ({description: '', members: [], groupIds: [], tagIds: [], contacts: [], contactSource: 'CLIENT', links: [], storage: []})
export const projectsApi = {
  workSummary: (id:string) => api.get<WorkSummary>(`/projects/${id}/work-summary`).then(r=>r.data),
  list: (query = '', category?: string, tags: string[] = []) => api.get<ProjectSummary[]>('/projects', {params: {query, category: category || undefined, tags: tags.length ? tags.join(',') : undefined}}).then(r => r.data),
  get: (id: string) => api.get<Project>(`/projects/${id}`).then(r => r.data),
  save: (id: string | undefined, input: ProjectInput) => (id ? api.put<Project>(`/projects/${id}`, input) : api.post<Project>('/projects', input)).then(r => r.data),
  people: () => api.get<Person[]>('/projects/people').then(r => r.data),
  options: () => api.get<ProjectOptions>('/projects/options').then(r => r.data),
  members: (id: string) => api.get<Person[]>(`/projects/${id}/members`).then(r => r.data),
  clients: () => api.get<Client[]>('/projects/clients').then(r => r.data),
  client: (id: string) => api.get<Client>(`/projects/clients/${id}`).then(r => r.data),
  saveClient: (id: string | undefined, input: ClientInput) => (id ? api.put<Client>(`/projects/clients/${id}`, input) : api.post<Client>('/projects/clients', input)).then(r => r.data),
  extensions: (kind: 'PROJECT' | 'CLIENT', id: string) => api.get<ContextExtension[]>(`/projects/${kind === 'CLIENT' ? 'clients/' : ''}${id}/extensions`).then(r => r.data),
  settings: () => api.get<ProjectSettings>('/admin/projects').then(r => r.data),
  saveSettings: (input: ProjectSettings) => api.put<ProjectSettings>('/admin/projects', input).then(r => r.data),
  groups: () => api.get<WorkGroup[]>('/admin/projects/groups').then(r => r.data),
  adminPeople: () => api.get<Person[]>('/admin/projects/people').then(r => r.data),
  saveGroup: (id: string | undefined, input: Omit<WorkGroup, 'id'>) => (id ? api.put<WorkGroup>(`/admin/projects/groups/${id}`, input) : api.post<WorkGroup>('/admin/projects/groups', input)).then(r => r.data),
  risks: (id: string) => api.get<Risk[]>(`/projects/${id}/risks`).then(r => r.data),
  sprints: (id: string) => api.get<{boardId: string; boardName: string; sprint: {id: string; name: string; startDate: string; endDate: string}}[]>(`/projects/${id}/sprints`).then(r => r.data),
  reports: (id: string) => api.get<ProjectReport[]>(`/projects/${id}/reports`).then(r => r.data),
  report: (id: string, input: ReportInput) => api.post<ProjectReport>(`/projects/${id}/reports`, input, {timeout: 180000}).then(r => r.data),
  pdf: (project: string, report: string) => api.get<Blob>(`/projects/${project}/reports/${report}/pdf`, {responseType: 'blob'}).then(r => r.data),
  previewDigest: (input: DigestInput) => api.post<DigestPreview>('/projects/digests/preview', input, {timeout: 90000}).then(r => r.data),
  sendDigest: (project: string | undefined, input: DigestInput) => api.post(project ? `/projects/${project}/digests` : '/projects/digests/send', input, {timeout: 600000}).then(r => r.data),
  schedules: (project?: string) => api.get<WorkSchedule[]>(`/projects/${project ? project + '/' : ''}schedules`).then(r => r.data),
  saveSchedule: (project: string | undefined, id: string | undefined, input: ScheduleInput) => (id ? api.put<WorkSchedule>(`/projects/${project ? project + '/' : ''}schedules/${id}`, input) : api.post<WorkSchedule>(`/projects/${project ? project + '/' : ''}schedules`, input)).then(r => r.data),
}
export function downloadProjectBlob(blob: Blob, filename: string) { const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = filename; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000) }
