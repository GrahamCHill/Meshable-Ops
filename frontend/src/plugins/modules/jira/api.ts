import {createPluginApi} from '../../sdk'
const api=createPluginApi('jira')
export interface JiraMetrics {total:number;completed:number;inProgress:number;todo:number;overdue:number;storyPoints:number|null;statuses:Record<string,number>;types:Record<string,number>;completedLast30Days:number;partial:boolean;syncedAt:string}
export interface JiraProjectLink {id:string;key:string;label:string;url:string;metrics:JiraMetrics|null;revision:number;boardId:number|null;boardUrl:string|null}
export interface JiraBoard {id:number;name:string;type:string}
export const jiraApi={
  list:(project:string)=>api.get<JiraProjectLink[]>(`/projects/${project}/links`),
  link:(project:string,key:string,label:string)=>api.post<JiraProjectLink>(`/projects/${project}/links`,{key,label}),
  sync:(project:string,link:JiraProjectLink)=>api.post<JiraProjectLink>(`/projects/${project}/links/${link.id}/sync`,{revision:link.revision}),
  boards:(project:string,link:JiraProjectLink)=>api.get<JiraBoard[]>(`/projects/${project}/links/${link.id}/boards`),
  setBoard:(project:string,link:JiraProjectLink,boardId:number|null)=>api.put<JiraProjectLink>(`/projects/${project}/links/${link.id}/board`,{revision:link.revision,boardId}),
  remove:(project:string,link:JiraProjectLink)=>api.delete(`/projects/${project}/links/${link.id}`,{revision:link.revision}),
}
