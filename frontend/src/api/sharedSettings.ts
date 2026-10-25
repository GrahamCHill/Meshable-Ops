import api from './http'
import type {PluginSharedSettingsReference,PluginSharedSettingsSchema} from './plugins'

export interface SharedSchema {schema:PluginSharedSettingsSchema;ownerPluginId?:string;path:string;encryptionConfigured:boolean}
export interface SharedProfile {id:string;schemaId:string;name:string;enabled:boolean;values:Record<string,string>;configuredSecrets:string[];missingRequired:string[];revision:number}
export interface SharedProfileInput {name:string;enabled:boolean;values:Record<string,string>;clearSecrets:string[];revision:number}
export interface SharedBinding {reference:PluginSharedSettingsReference;selectedProfileId?:string;profiles:SharedProfile[];revision:number;settingsPath?:string}
export function sharedSettingsApi(email=false) {
  const base=email?'/admin/email/shared-settings':'/admin/plugins/shared-settings'
  return {
    schema:(id:string)=>api.get<SharedSchema>(`${base}/schemas/${encodeURIComponent(id)}`).then(r=>r.data),
    profiles:(id:string)=>api.get<SharedProfile[]>(`${base}/schemas/${encodeURIComponent(id)}/profiles`).then(r=>r.data),
    save:(schema:string,id:string|undefined,input:SharedProfileInput)=>{
      const url=`${base}/schemas/${encodeURIComponent(schema)}/profiles`
      return (id?api.put<SharedProfile>(`${url}/${encodeURIComponent(id)}`,input):api.post<SharedProfile>(url,input)).then(r=>r.data)
    },
  }
}
export const pluginSharedSettingsApi={
  bindings:(id:string)=>api.get<SharedBinding[]>(`/admin/plugins/${encodeURIComponent(id)}/shared-settings`).then(r=>r.data),
  bind:(id:string,ref:string,profileId:string|null,revision:number)=>api.put<SharedBinding[]>(`/admin/plugins/${encodeURIComponent(id)}/shared-settings/${encodeURIComponent(ref)}`,{profileId,revision}).then(r=>r.data),
}
