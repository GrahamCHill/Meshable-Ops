import api from '../../../api/http'
import {createPluginApi} from '../../sdk'

export interface PageSummary {id:string;title:string;parentId:string|null;updatedAt:string;revision:number}
export interface Diagram {key:string;source:string}
export interface Rendered {html:string;diagrams:Diagram[];headings:{id:string;title:string;level:number}[]}
export interface PageInput {title:string;markdown:string;parentId:string|null;revision:number;assets:Record<string,string>}
export interface Page extends PageInput {id:string;rendered:Rendered}
export interface Status {canEdit:boolean;canExport:boolean;canManage:boolean}
export interface Settings {exporterIds:string[];coverHtml:string;coverAssets:Record<string,string>;revision:number;defaultHtml:string;members:{id:string;name:string;email:string;active?:boolean}[]}
export interface Selection {title:string;pages:{id:string;revision:number}[]}
const plugin = createPluginApi('knowledge')
export const knowledge = {
  status:()=>plugin.get<Status>('/status'),
  list:()=>plugin.get<PageSummary[]>('/pages'),
  page:(id:string)=>plugin.get<Page>(`/pages/${id}`),
  save:(id:string|null,input:PageInput)=>id?plugin.put<Page>(`/pages/${id}`,input):plugin.post<Page>('/pages',input),
  remove:(id:string,revision:number)=>plugin.delete(`/pages/${id}`,{revision}),
  preview:(id:string,input:PageInput)=>plugin.post<Rendered>(`/preview/${id}`,input),
  settings:()=>plugin.get<Settings>('/settings'),
  permissions:(exporterIds:string[],revision:number)=>plugin.put<Settings>('/settings/exporters',{exporterIds,revision}),
  saveSettings:(value:Settings)=>plugin.put<Settings>('/settings',{coverHtml:value.coverHtml,coverAssets:value.coverAssets,revision:value.revision}),
  coverPreview:(coverHtml:string,coverAssets:Record<string,string>)=>api.post<Blob>('/plugins/knowledge/settings/cover/preview',{coverHtml,coverAssets},{responseType:'blob'}).then(r=>r.data),
  prepare:(input:Selection)=>plugin.post<{id:string;title:string;revision:number;diagrams:Diagram[]}[]>('/exports/prepare',input),
  export:(input:Selection,diagrams:Record<string,string>)=>api.post<Blob>('/plugins/knowledge/exports',{...input,diagrams},{responseType:'blob'}).then(r=>r.data),
}
