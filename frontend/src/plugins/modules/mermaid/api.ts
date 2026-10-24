import {createPluginApi} from '../../sdk'
const api=createPluginApi('mermaid')
export interface ProjectDiagram {id:string;title:string;source:string;revision:number}
export type DiagramInput=Omit<ProjectDiagram,'id'>
export const mermaidApi={
  list:(project:string)=>api.get<ProjectDiagram[]>(`/projects/${project}/diagrams`),
  save:(project:string,id:string|undefined,value:DiagramInput)=>id?api.put<ProjectDiagram>(`/projects/${project}/diagrams/${id}`,value):api.post<ProjectDiagram>(`/projects/${project}/diagrams`,value),
  remove:(project:string,diagram:ProjectDiagram)=>api.delete(`/projects/${project}/diagrams/${diagram.id}`,{revision:diagram.revision}),
}
