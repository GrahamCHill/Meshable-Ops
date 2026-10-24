import {createPluginApi} from '../../sdk'
const api=createPluginApi('figma')
export interface FigmaFile {id:string;label:string;url:string;fileKey:string;metadata:{name:string;lastModified:string;version:string;editorType:string;refreshedAt:string}|null;revision:number}
export const figmaApi={
  list:(project:string)=>api.get<FigmaFile[]>(`/projects/${project}/files`),
  link:(project:string,label:string,url:string)=>api.post<FigmaFile>(`/projects/${project}/files`,{label,url}),
  refresh:(project:string,file:FigmaFile)=>api.post<FigmaFile>(`/projects/${project}/files/${file.id}/refresh`,{revision:file.revision}),
  remove:(project:string,file:FigmaFile)=>api.delete(`/projects/${project}/files/${file.id}`,{revision:file.revision}),
}
