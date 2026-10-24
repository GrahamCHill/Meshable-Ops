import {useState} from 'react'
import {useQuery,useQueryClient} from '@tanstack/react-query'
import {artworkApi,type Artwork} from '../../api/artwork'
import {ArtworkIcon} from '../ui/ArtworkIcon'
import {ArtworkPicker} from '../ui/ArtworkPicker'
import {Button} from '../ui'

export function ClientLogoImage({id,value,size=40}:{id:string;value?:Artwork;size?:number}) {
  return <ArtworkIcon value={value??{icon:'building',imageVersion:null,revision:-1}} size={size} imageUrl={value?.imageVersion?`/api/projects/clients/${id}/logo/image/${value.imageVersion}`:undefined}/>
}
export function ClientLogo({id,canManage}:{id:string;canManage:boolean}) {
  const [editing,setEditing]=useState(false),client=useQueryClient(),root=`/projects/clients/${id}/logo`
  const query=useQuery({queryKey:['client-logo',id],queryFn:()=>artworkApi.current(root)})
  return <section className="page-stack"><div className="inline-actions"><ClientLogoImage id={id} value={query.data} size={64}/>{canManage&&<Button type="button" variant="ghost" onClick={()=>setEditing(!editing)}>{editing?'Close logo settings':'Change client logo'}</Button>}</div>
    {editing&&query.data&&<ArtworkPicker label="Client logo" value={query.data} root={root} imageUrl={query.data.imageVersion?`/api/projects/clients/${id}/logo/image/${query.data.imageVersion}`:undefined} onSaved={async value=>{
      client.setQueryData(['client-logo',id],value)
      await Promise.all([client.invalidateQueries({queryKey:['project-clients']}),client.invalidateQueries({queryKey:['project-client',id]})])
    }}/>} {query.isError&&<p role="alert">Client logo could not load.</p>}
  </section>
}
