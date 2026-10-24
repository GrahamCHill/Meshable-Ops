import {useState} from 'react'
import {useQuery,useQueryClient} from '@tanstack/react-query'
import {artworkApi,type Artwork} from '../../api/artwork'
import {ArtworkIcon} from '../ui/ArtworkIcon'
import {ArtworkPicker} from '../ui/ArtworkPicker'
import {Button} from '../ui'

export function ProjectIcon({id,value,size=40}:{id:string;value?:Artwork;size?:number}) {
  return <ArtworkIcon value={value} size={size} imageUrl={value?.imageVersion?`/api/projects/${id}/icon/image/${value.imageVersion}`:undefined}/>
}
export function ProjectArtwork({id,canManage}:{id:string;canManage:boolean}) {
  const [editing,setEditing]=useState(false),client=useQueryClient(),root=`/projects/${id}/icon`
  const query=useQuery({queryKey:['project-icon',id],queryFn:()=>artworkApi.current(root)})
  return <div className="page-stack"><div className="inline-actions"><ProjectIcon id={id} value={query.data}/>{canManage&&<Button type="button" variant="ghost" size="sm" onClick={()=>setEditing(!editing)}>{editing?'Close icon settings':'Change project icon'}</Button>}</div>
    {editing&&query.data&&<ArtworkPicker label="Project icon" value={query.data} root={root} imageUrl={query.data.imageVersion?`/api/projects/${id}/icon/image/${query.data.imageVersion}`:undefined} onSaved={async value=>{client.setQueryData(['project-icon',id],value);await client.invalidateQueries({queryKey:['projects']})}}/>}
    {query.isError&&<p role="alert">Project icon could not load.</p>}
  </div>
}
