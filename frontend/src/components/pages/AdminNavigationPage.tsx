import {useEffect,useState} from 'react'
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query'
import {navigationApi,type NavigationPreferences} from '../../api/navigation'
import {AdminPageHeader} from '../admin/AdminPageHeader'
import {NavigationOrderList} from '../admin/NavigationOrderList'
import {getErrorMessage} from '../admin/adminUtils'
import {Button} from '../ui'
import {useSearchParams} from 'react-router-dom'
import {AdminSectionIcons} from '../admin/AdminSectionIcons'
import '../../styles/navigation-settings.css'

export default function AdminNavigationPage() {
  const [params,setParams]=useSearchParams(),icons=params.get('tab')==='icons'
  const client=useQueryClient(),query=useQuery({queryKey:['admin-navigation'],queryFn:navigationApi.admin})
  const [draft,setDraft]=useState<NavigationPreferences>(),[dirty,setDirty]=useState(false)
  useEffect(()=>{if(query.data&&!dirty)setDraft(query.data.preferences)},[query.data,dirty])
  const save=useMutation({mutationFn:()=>navigationApi.save(draft!),onSuccess:async data=>{
    client.setQueryData(['admin-navigation'],data);setDraft(data.preferences);setDirty(false)
    await client.invalidateQueries({queryKey:['workspace-navigation']})
  }})
  const update=(next:NavigationPreferences)=>{setDraft(next);setDirty(true);save.reset()}
  return <div className="page-stack" style={{maxWidth:900}}><AdminPageHeader title="Navigation & section icons" description="Arrange workspace links and customize administration sections."/>
    <nav className="workspace-tabs" aria-label="Navigation settings"><button aria-current={!icons?'page':undefined} onClick={()=>setParams({})}>Menu ordering</button><button aria-current={icons?'page':undefined} onClick={()=>setParams({tab:'icons'})}>Section icons</button></nav>
    {icons?<AdminSectionIcons/>:<>
    <p>Use the arrows to change the order. Permissions still determine which links each member sees. Administration stays pinned at the bottom. Disabled plugins retain their position, and new items appear after saved entries.</p>
    {query.isLoading&&<p role="status">Loading menu settings…</p>}{query.isError&&<p role="alert">Unable to load menu settings. <button onClick={()=>void query.refetch()}>Retry</button></p>}
    {draft&&query.data&&<form className="page-stack" onSubmit={e=>{e.preventDefault();save.mutate()}}>
      <h2>Main menu</h2><NavigationOrderList label="Main menu order" items={query.data.items} order={draft.order} disabled={save.isPending} onChange={order=>update({...draft,order})}/>
      {query.data.items.filter(item=>item.children.length>1).map(item=><section className="page-stack" key={item.id}><h2>{item.label} sections</h2><NavigationOrderList label={`${item.label} submenu order`} items={item.children} order={draft.submenus[item.id]??[]} disabled={save.isPending} onChange={order=>update({...draft,submenus:{...draft.submenus,[item.id]:order}})}/></section>)}
      {dirty&&query.data.preferences.revision!==draft.revision&&<p role="alert">Someone changed the saved order. Reload it before saving.</p>}
      <div className="inline-actions"><Button type="submit" disabled={!dirty||save.isPending||query.data.preferences.revision!==draft.revision}>Save menu order</Button><Button type="button" variant="ghost" disabled={save.isPending} onClick={()=>update({...draft,order:[],submenus:{}})}>Restore default order</Button><Button type="button" variant="ghost" disabled={save.isPending} onClick={async()=>{const result=await query.refetch();if(result.data){setDraft(result.data.preferences);setDirty(false);save.reset()}}}>Reload saved order</Button></div>
    </form>}
    {save.isError&&<p role="alert">{getErrorMessage(save.error)}</p>}{save.isSuccess&&<p role="status">Menu order saved.</p>}</>}
  </div>
}
