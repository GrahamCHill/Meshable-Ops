import { createPluginApi } from '../../sdk'
import type {PluginSettingField} from '../../../api/plugins'
export interface CalendarDocumentLink {label:string;url:string}
export interface CalendarParticipant {name:string;email:string;response:string}
export interface CalendarEvent {id: string; title: string; description: string; start: string; end: string; allDay: boolean; source: string; href?: string; revision: number; syncEnabled: boolean;documents:CalendarDocumentLink[];participants:CalendarParticipant[];calendarName?:string}
export interface EventInput {title: string; description: string; start: string; end: string; allDay: boolean; revision: number; syncEnabled: boolean;documents:CalendarDocumentLink[];participants:CalendarParticipant[];calendarName?:string}
export interface CalendarWindow {events: CalendarEvent[]; truncated: boolean; notice?: string}
export interface PersonalConnection {providerId: string; enabled: boolean; revision: number; values: Record<string,string>; configuredSecrets: string[]; missingRequired: string[]; lastSyncAt?: string; status: string}
export interface PersonalSchema {id: string; name: string; fields: PluginSettingField[]}
export interface PersonalSettings {connection?: PersonalConnection; providers: PersonalSchema[]; canWrite: boolean}
export interface PersonalInput {providerId: string; values: Record<string,string>; clearSecrets: string[]; enabled: boolean; revision: number}
const api = createPluginApi('calendar')
export const calendarApi = {
  window:(from: string,until: string,provider: string) => api.get<CalendarWindow>(provider ? `/providers/${provider}/events` : '/events',{from,until,zone:Intl.DateTimeFormat().resolvedOptions().timeZone}),
  save:(id: string | undefined,event: EventInput) => id ? api.put<CalendarEvent>(`/events/${id}`,event) : api.post<CalendarEvent>('/events',event),
  remove:(event: CalendarEvent) => api.delete(`/events/${event.id}`,{revision:event.revision}),
  publish:(id: string,provider: string) => api.post<{message: string}>(`/events/${id}/publish/${provider}`,{}),
  export:(from: string,until: string) => api.get<string>('/export',{from,until,zone:Intl.DateTimeFormat().resolvedOptions().timeZone}),
  personal:() => api.get<PersonalSettings>('/personal'),
  savePersonal:(input: PersonalInput) => api.put<PersonalConnection>('/personal',input),
  disconnect:(revision: number) => api.delete('/personal',{revision}),
  sync:() => api.post<PersonalSettings>('/personal/sync',{}),
}
