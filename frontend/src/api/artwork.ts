import api from './http'
export interface Artwork {icon:string;imageVersion:string|null;revision:number}
export interface AdminSectionArtwork {id:string;label:string;defaultIcon:string;artwork:Artwork}
export const artworkApi={
  sections:()=>api.get<AdminSectionArtwork[]>('/admin/navigation/sections').then(r=>r.data),
  sectionIcons:()=>api.get<Record<string,Artwork>>('/navigation/admin-sections').then(r=>r.data),
  current:(root:string)=>api.get<Artwork>(root).then(r=>r.data),
  save:async(root:string,value:Artwork,icon:string,file?:File)=>{
    if(!file)return (await api.put<Artwork>(root,{icon,revision:value.revision})).data
    const data=new FormData();data.append('file',file);data.append('revision',String(value.revision))
    return (await api.post<Artwork>(`${root}/image`,data,{headers:{'Content-Type':'multipart/form-data'}})).data
  },
}
