import { useQuery } from '@tanstack/react-query'
import api from './http'
export type DeliveryMethod = { id: string; label: string; pluginId: string | null; recipientKind: 'EMAIL' | 'PHONE' | 'FIXED'; supportsOtp: boolean; supportsHtml: boolean; supportsAttachments: boolean; parameterized: boolean; maxTextLength: number }
export type DeliveryMethodView = { method: DeliveryMethod; enabled: boolean; available: boolean }
export type MessageTemplate = { subject: string; text: string; html: string; parameters: string[]; providerTemplate: string }
export type NotificationRule = { enabled: boolean; allowedMethods: string[]; defaultMethods: string[]; allowMemberChoice: boolean; templates: Record<string, MessageTemplate> }
export type NotificationDefinition = { id: string; label: string; description: string; category: 'GENERAL' | 'WORKFLOW' | 'SECURITY' | 'TRANSACTIONAL'; variables: string[]; subject: string; text: string; enabledByDefault: boolean; pluginId: string | null }
export type NotificationItem = { definition: NotificationDefinition; rule: NotificationRule; eligibleMethods: string[] }
export type NotificationSettings = { revision: number; methods: DeliveryMethodView[]; notifications: NotificationItem[] }
export type NotificationInput = { revision: number; methods: Record<string, boolean>; rules: Record<string, NotificationRule> }
export type NotificationPreview = { subject: string; text: string; html: string; parameters: string[] }
export type NotificationDelivery = { id: string; notificationId: string; methodId: string; targetUserId: string | null; recipientHint: string; status: string; createdAt: string; finishedAt: string | null }
export type PreferenceItem = { id: string; label: string; description: string; enabled: boolean; editable: boolean; defaults: string[]; selected: string[]; usingDefaults: boolean; methods: DeliveryMethodView[] }
export type NotificationPreferences = { revision: number; notifications: PreferenceItem[]; hasPhone: boolean }
export type PreferenceInput = { revision: number; choices: Record<string, string[]> }
export const notificationsApi = {
  settings: () => api.get<NotificationSettings>('/admin/notifications').then(r => r.data),
  save: (input: NotificationInput) => api.put<NotificationSettings>('/admin/notifications', input).then(r => r.data),
  preview: (notificationId: string, methodId: string, template: MessageTemplate) => api.post<NotificationPreview>('/admin/notifications/preview', { notificationId, methodId, template }).then(r => r.data),
  deliveries: () => api.get<NotificationDelivery[]>('/admin/notifications/deliveries').then(r => r.data),
  send: (input: { methodId: string; recipient: string; subject: string; message: string; userId?: string }) => api.post<Record<string, { status: string; message: string }>>('/admin/notifications/send', input, { timeout: 90000 }).then(r => r.data),
  preferences: () => api.get<NotificationPreferences>('/account/notifications').then(r => r.data),
  savePreferences: (input: PreferenceInput) => api.put<NotificationPreferences>('/account/notifications', input).then(r => r.data),
  availability: () => api.get<{ documentsEmail: boolean; workflowEmail: boolean; sharesEmail: boolean }>('/notifications/availability').then(r => r.data),
}
export function notificationInput(settings: NotificationSettings): NotificationInput {
  return { revision: settings.revision, methods: Object.fromEntries(settings.methods.map(value => [value.method.id, value.enabled])), rules: Object.fromEntries(settings.notifications.map(value => [value.definition.id, value.rule])) }
}
export function useNotificationAvailability() { return useQuery({ queryKey: ['notification-availability'], queryFn: notificationsApi.availability, refetchInterval: 30000 }) }
