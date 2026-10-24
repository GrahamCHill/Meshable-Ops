import { createPluginApi } from '../../sdk'

export type CardStatus = 'TODO' | 'IN_PROGRESS' | 'DONE'
export type MoveMode = 'EDITORS' | 'ASSIGNEE' | 'REVIEWERS' | 'SELECTED_MEMBERS' | 'MANAGERS'
export interface ColumnPermission {mode: MoveMode; userIds: string[]}
export interface BoardColumn { id: string; title: string; status?: CardStatus | null; enter?: ColumnPermission; leave?: ColumnPermission }
export interface BoardEpic {id: string; name: string; description: string; color: string}
export interface BoardSprint {id: string; name: string; startDate: string; endDate: string}
export interface BoardCard { id: string; columnId: string; title: string; description: string; dueDate?: string | null; startDate?: string | null; completionDate?: string | null; assigneeId?: string | null; reviewerIds?: string[]; sprintId?: string | null; storyPoints?: number | null; status?: CardStatus; reference?: string; blocked?: boolean; tags?: string[]; epicId?: string | null; parentId?: string | null; type?: 'TASK' | 'STORY' | 'BUG' | 'SUBTASK'; priority?: 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT' }
export interface BoardInput { name: string; revision: number; projectId?: string | null; teamIds?: string[]; content: { columns: BoardColumn[]; cards: BoardCard[]; sprints?: BoardSprint[]; epics?: BoardEpic[] } }
export interface BoardSummary { id: string; ownerId: string; name: string; updatedAt: string; projectId: string | null; ticketCount?: number; completedCount?: number }
export interface Board extends Omit<BoardInput, 'projectId'>, BoardSummary {canManage: boolean; moves: Record<string,string[]>}
const api = createPluginApi('kanban')
export const boardsApi = {
  list: (project?: string) => api.get<BoardSummary[]>('/boards', project ? {projectId: project} : undefined),
  get: (id: string) => api.get<Board>(`/boards/${id}`),
  save: (id: string | undefined, board: BoardInput) => id ? api.put<Board>(`/boards/${id}`, board) : api.post<Board>('/boards', board),
  members: (id: string) => api.get<{id: string; name: string; email: string}[]>(`/boards/${id}/members`),
  move: (board: string, card: string, columnId: string, revision: number, beforeId?: string) => api.post<Board>(`/boards/${board}/cards/${card}/move`, {columnId, revision, beforeId: beforeId ?? null}),
  comments: (board: string, card: string) => api.get<BoardComment[]>(`/boards/${board}/cards/${card}/comments`),
  comment: (board: string, card: string, text: string, mentionIds: string[]) => api.post<BoardComment>(`/boards/${board}/cards/${card}/comments`, {text, mentionIds}),
}
export interface BoardComment {id: string; authorId: string; authorName: string; text: string; mentionIds: string[]; createdAt: string}
export function blankBoard(): BoardInput {
  return { name: 'New board', revision: 0, projectId: null, teamIds: [], content: { columns: [{id: crypto.randomUUID(), title: 'To do', status: 'TODO'}, {id: crypto.randomUUID(), title: 'In progress', status: 'IN_PROGRESS'}, {id: crypto.randomUUID(), title: 'Done', status: 'DONE'}], cards: [], sprints: [] } }
}
