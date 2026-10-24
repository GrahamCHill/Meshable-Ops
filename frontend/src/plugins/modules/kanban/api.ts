import { createPluginApi } from '../../sdk'

export interface BoardColumn { id: string; title: string }
export interface BoardCard { id: string; columnId: string; title: string; description: string }
export interface BoardInput { name: string; revision: number; content: { columns: BoardColumn[]; cards: BoardCard[] } }
export interface BoardSummary { id: string; ownerId: string; name: string; updatedAt: string }
export interface Board extends BoardInput, BoardSummary {}
const api = createPluginApi('kanban')
export const boardsApi = {
  list: () => api.get<BoardSummary[]>('/boards'),
  get: (id: string) => api.get<Board>(`/boards/${id}`),
  save: (id: string | undefined, board: BoardInput) => id ? api.put<Board>(`/boards/${id}`, board) : api.post<Board>('/boards', board),
}
export function blankBoard(): BoardInput {
  return { name: 'New board', revision: 0, content: { columns: ['To do', 'In progress', 'Done'].map(title => ({ id: crypto.randomUUID(), title })), cards: [] } }
}
