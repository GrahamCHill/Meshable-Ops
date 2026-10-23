import api from './http'
import type { PageResponse } from './types'

export interface Event {
  id: string
  type: string
  payload: Record<string, unknown>
  createdAt: string
}

export const eventsApi = {
  list: (page = 0, size = 20) =>
    api.get<PageResponse<Event>>('/events', { params: { page, size } }).then(r => r.data),
}
