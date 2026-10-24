import {useQuery,useQueryClient} from '@tanstack/react-query'
import {artworkApi} from '../../api/artwork'
import {ArtworkIcon} from '../ui/ArtworkIcon'
import {ArtworkPicker} from '../ui/ArtworkPicker'

const defaults:Record<string,string>={workspace:'layout','people-security':'users','storage-sharing':'cloud',communications:'mail','documents-workflows':'workflow','analytics-search':'chart',operations:'activity'}
export function AdminSectionIcon({id}:{id:string}) {
  const query=useQuery({queryKey:['admin-section-icons'],queryFn:artworkApi.sectionIcons})
  const value=query.data?.[id]??{icon:defaults[id]??'plug',imageVersion:null}
  return <ArtworkIcon value={value} imageUrl={value.imageVersion?`/api/navigation/admin-sections/${encodeURIComponent(id)}/image/${value.imageVersion}`:undefined}/>
}
export function AdminSectionIcons() {
  const client=useQueryClient(),query=useQuery({queryKey:['admin-section-artwork'],queryFn:artworkApi.sections})
  return <section className="page-stack"><h2>Administration section icons</h2><p>Choose a simple icon or upload a WebP for each registered section. Plugin categories appear here automatically.</p>
    {query.isLoading&&<p role="status">Loading section icons…</p>}{query.isError&&<p role="alert">Unable to load section icons. <button onClick={()=>void query.refetch()}>Retry</button></p>}
    {query.data?.map(section=><ArtworkPicker key={section.id} label={section.label} value={section.artwork} defaultIcon={section.defaultIcon} root={`/admin/navigation/sections/${encodeURIComponent(section.id)}`}
      imageUrl={section.artwork.imageVersion?`/api/navigation/admin-sections/${encodeURIComponent(section.id)}/image/${section.artwork.imageVersion}`:undefined}
      onSaved={async value=>{client.setQueryData(['admin-section-artwork'],query.data?.map(item=>item.id===section.id?{...item,artwork:value}:item));await client.invalidateQueries({queryKey:['admin-section-icons']})}}/>)}</section>
}
