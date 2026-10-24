import api from './http'
export interface DevelopmentFeatures {repositories:boolean;branches:boolean;pullRequests:boolean;deployments:boolean}
export interface DevelopmentSettings {features:DevelopmentFeatures;revision:number;canManage:boolean}
export interface ProjectRepositoryLink {id:string;providerId:string;repository:string|null;title:string;url:string|null;available:boolean;revision:number;checkedAt:string|null}
export type DevelopmentKind='BRANCH'|'PULL_REQUEST'|'DEPLOYMENT'
export interface DevelopmentLink {id:string;repositoryId:string;kind:DevelopmentKind;reference:string|null;title:string;url:string|null;state:string|null;environment:string|null;ref:string|null;manual:boolean;available:boolean;checkedAt:string|null;revision:number;boardId:string|null;cardId:string|null;boardName:string|null}
export interface DevelopmentInput {repositoryId:string;kind:DevelopmentKind;reference:string;boardId:string|null;cardId:string|null;manualUrl?:string;environment?:string;state?:string}
export interface BoardDevelopmentLink {id:string;boardId:string;boardName:string;cardId:string|null;kind:string;title:string;url:string;state:string}
export const developmentApi={
  settings:(project:string)=>api.get<DevelopmentSettings>(`/projects/${project}/development/settings`).then(r=>r.data),
  saveSettings:(project:string,value:DevelopmentSettings)=>api.put<DevelopmentSettings>(`/projects/${project}/development/settings`,value).then(r=>r.data),
  repositories:(project:string)=>api.get<ProjectRepositoryLink[]>(`/projects/${project}/repositories`).then(r=>r.data),
  links:(project:string)=>api.get<DevelopmentLink[]>(`/projects/${project}/development/links`).then(r=>r.data),
  boardLinks:(project:string)=>api.get<BoardDevelopmentLink[]>(`/projects/${project}/development/board-links`).then(r=>r.data),
  add:(project:string,value:DevelopmentInput)=>api.post<DevelopmentLink>(`/projects/${project}/development/links`,value).then(r=>r.data),
  refresh:(project:string,value:DevelopmentLink)=>api.post<DevelopmentLink>(`/projects/${project}/development/links/${value.id}/refresh`,null,{params:{revision:value.revision}}).then(r=>r.data),
  status:(project:string,value:DevelopmentLink,state:string)=>api.put<DevelopmentLink>(`/projects/${project}/development/links/${value.id}/status`,{revision:value.revision,state}).then(r=>r.data),
  remove:(project:string,value:DevelopmentLink)=>api.delete(`/projects/${project}/development/links/${value.id}`,{params:{revision:value.revision}}),
}
export function developmentKinds(features:DevelopmentFeatures):{id:DevelopmentKind;label:string}[] {
  if(!features.repositories)return []
  return [...(features.branches?[{id:'BRANCH' as const,label:'Branch'}]:[]),...(features.pullRequests?[{id:'PULL_REQUEST' as const,label:'Pull / merge request'}]:[]),...(features.deployments?[{id:'DEPLOYMENT' as const,label:'Deployment'}]:[])]
}
