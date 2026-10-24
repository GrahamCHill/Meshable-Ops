import api from './http'

export interface Event {
  id: string
  type: string
  payload: Record<string, unknown>
  createdAt: string
}

export interface EventHistory { content: Event[]; nextCursor: string | null }

export const eventsApi = {
  history: (cursor?: string, signal?: AbortSignal) =>
    api.get<EventHistory>('/admin/events', { params: { cursor, size: 50 }, signal }).then(r => r.data),
}
