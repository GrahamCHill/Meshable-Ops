import {createPluginApi} from '../sdk'
export type AnalyticsContext = 'PROJECT' | 'CLIENT'
export type AnalyticsFilter = 'ALL' | 'CAMPAIGN' | 'PAGE'
export interface AnalyticsSelection {resourceId:string;filter:AnalyticsFilter;selector:string}
export interface AnalyticsMetric {key:string;label:string;value:number|null;unit:'NUMBER'|'PERCENT'|'CURRENCY'|'BYTES'}
export interface AnalyticsSnapshot {from:string;to:string;metrics:AnalyticsMetric[];currency:string|null;notes:string[];syncedAt:string}
export interface AnalyticsLink {id:string;label:string;selection:AnalyticsSelection;snapshot:AnalyticsSnapshot|null;revision:number;url:string}
export interface AnalyticsOptions {resourceLabel:string;resources:string[];filters:AnalyticsFilter[];configured:boolean}
export function analyticsApi(plugin:string,kind:AnalyticsContext,context:string) {
  const api=createPluginApi(plugin),base=`/contexts/${kind}/${context}/links`
  return {
    list:()=>api.get<AnalyticsLink[]>(base),options:()=>api.get<AnalyticsOptions>(base+'/options'),
    link:(label:string,selection:AnalyticsSelection)=>api.post<AnalyticsLink>(base,{label,selection}),
    sync:(link:AnalyticsLink,from:string,to:string)=>api.post<AnalyticsLink>(`${base}/${link.id}/sync`,{revision:link.revision,from,to}),
    remove:(link:AnalyticsLink)=>api.delete(`${base}/${link.id}`,{revision:link.revision}),
  }
}
