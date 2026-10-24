import api from './http'

export type Webhook = { id: string; name: string; direction: 'INCOMING' | 'OUTGOING'; enabled: boolean; url: string; authentication: 'HMAC' | 'BEARER'; secretConfigured: boolean }
export type WebhookInput = Omit<Webhook, 'id' | 'secretConfigured'> & { secret?: string; rotateSecret?: boolean }
export type WebhookDelivery = { id: string; createdAt: string; deliveryKey: string; status: string; httpStatus?: number; payload?: string }
export const webhooksApi = {
  list: () => api.get<Webhook[]>('/admin/webhooks').then(r => r.data),
  save: (id: string | undefined, body: WebhookInput) => (id ? api.put<{ endpoint: Webhook; secret?: string }>(`/admin/webhooks/${id}`, body) : api.post<{ endpoint: Webhook; secret?: string }>('/admin/webhooks', body)).then(r => r.data),
  history: (id: string) => api.get<WebhookDelivery[]>(`/admin/webhooks/${id}/deliveries`).then(r => r.data),
  send: (id: string, event: string, data: unknown) => api.post<WebhookDelivery>(`/admin/webhooks/${id}/send`, { event, data }).then(r => r.data),
}
