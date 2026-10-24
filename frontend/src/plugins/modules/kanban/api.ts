import { createPluginApi } from '../../sdk'

export type CardStatus = 'TODO' | 'IN_PROGRESS' | 'DONE'
export interface BoardColumn { id: string; title: string; status?: CardStatus | null }
export interface BoardSprint {id: string; name: string; startDate: string; endDate: string}
export interface BoardCard { id: string; columnId: string; title: string; description: string; dueDate?: string | null; startDate?: string | null; completionDate?: string | null; assigneeId?: string | null; reviewerIds?: string[]; sprintId?: string | null; storyPoints?: number | null; status?: CardStatus; reference?: string; blocked?: boolean }
export interface BoardInput { name: string; revision: number; projectId?: string | null; teamIds?: string[]; content: { columns: BoardColumn[]; cards: BoardCard[]; sprints?: BoardSprint[] } }
export interface BoardSummary { id: string; ownerId: string; name: string; updatedAt: string; projectId: string | null }
export interface Board extends Omit<BoardInput, 'projectId'>, BoardSummary {}
const api = createPluginApi('kanban')
export const boardsApi = {
  list: (project?: string) => api.get<BoardSummary[]>(`/boards${project ? '?projectId=' + project : ''}`),
  get: (id: string) => api.get<Board>(`/boards/${id}`),
  save: (id: string | undefined, board: BoardInput) => id ? api.put<Board>(`/boards/${id}`, board) : api.post<Board>('/boards', board),
  members: (id: string) => api.get<{id: string; name: string; email: string}[]>(`/boards/${id}/members`),
  comments: (board: string, card: string) => api.get<BoardComment[]>(`/boards/${board}/cards/${card}/comments`),
  comment: (board: string, card: string, text: string, mentionIds: string[]) => api.post<BoardComment>(`/boards/${board}/cards/${card}/comments`, {text, mentionIds}),
}
export interface BoardComment {id: string; authorId: string; authorName: string; text: string; mentionIds: string[]; createdAt: string}
export function blankBoard(): BoardInput {
  return { name: 'New board', revision: 0, projectId: null, teamIds: [], content: { columns: [{id: crypto.randomUUID(), title: 'To do', status: 'TODO'}, {id: crypto.randomUUID(), title: 'In progress', status: 'IN_PROGRESS'}, {id: crypto.randomUUID(), title: 'Done', status: 'DONE'}], cards: [], sprints: [] } }
}
