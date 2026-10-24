import {useState} from 'react'
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query'
import {useAvailablePlugins} from '../../api/plugins'
import api from '../../api/http'
import {useAuthStore} from '../../hooks/useAuthStore'
import {RepositorySelect} from './RepositorySelect'
import {Button} from '../ui'
import {getErrorMessage} from '../admin/adminUtils'
import {ExternalLink} from 'lucide-react'
import {useDevelopmentSettings} from './ProjectDevelopment'
interface Link {id:string;providerId:string;repository:string|null;title:string;url:string|null;available:boolean;revision:number;checkedAt:string|null}
export function ProjectRepositories({projectId,canManage}:{projectId:string;canManage:boolean}) {
  const plugins=useAvailablePlugins(),client=useQueryClient(),user=useAuthStore(state=>state.user?.id),root=`/projects/${projectId}/repositories`
  const settings=useDevelopmentSettings(projectId)
  const providers=plugins.data?.filter(plugin=>['github','gitlab','gitea'].includes(plugin.manifest.id))??[]
  const [provider,setProvider]=useState(''),[repository,setRepository]=useState('')
  const query=useQuery({queryKey:['project-repositories',projectId,user,providers.map(item=>item.manifest.id).join(',')],queryFn:()=>api.get<Link[]>(root).then(r=>r.data),enabled:settings.data?.features.repositories===true})
  const action=useMutation({mutationFn:({type,link}:{type:'add'|'refresh'|'remove';link?:Link})=>type==='add'?api.post(root,{providerId:provider,repository}):type==='remove'?api.delete(`${root}/${link!.id}`,{params:{revision:link!.revision}}):api.post(`${root}/${link!.id}/refresh`,null,{params:{revision:link!.revision}}),onSuccess:()=>client.invalidateQueries({queryKey:['project-repositories',projectId]})})
  if(!settings.data?.features.repositories||!providers.length&&!query.data?.length&&!query.isLoading)return null
  return <section className="page-stack"><h2>Repositories</h2><p>Project repository links use the enabled Git provider's access rules. Provider changes hide saved metadata until a manager refreshes it.</p>
    {query.isLoading&&<p role="status">Loading repositories…</p>}{query.isError&&<p role="alert">Project repositories could not load.</p>}
    {query.data?.map(link=><article key={link.id}><div>{link.url?<a href={link.url} target="_blank" rel="noopener noreferrer" title="Open repository in a new window">{link.title} <ExternalLink size={13}/></a>:link.title} · {link.providerId}</div>{canManage&&<div className="inline-actions"><Button variant="ghost" size="sm" disabled={action.isPending||!providers.some(plugin=>plugin.manifest.id===link.providerId)} onClick={()=>action.mutate({type:'refresh',link})}>Refresh</Button><Button variant="ghost" size="sm" disabled={action.isPending} onClick={()=>action.mutate({type:'remove',link})}>Remove link</Button></div>}</article>)}
    {canManage&&!!providers.length&&<details><summary>Link a repository</summary><fieldset className="page-stack" disabled={action.isPending}><label className="form-field">Git provider<select value={provider} onChange={e=>{setProvider(e.target.value);setRepository('')}}><option value="">Choose provider</option>{providers.map(plugin=><option key={plugin.manifest.id} value={plugin.manifest.id}>{plugin.manifest.name}</option>)}</select></label>{provider&&<RepositorySelect key={provider} provider={provider} value={repository} onChange={setRepository}/>}<div><Button disabled={!provider||!repository||action.isPending} onClick={()=>action.mutate({type:'add'})}>Link repository to project</Button></div></fieldset></details>}
    {action.isError&&<p role="alert">{getErrorMessage(action.error)}</p>}
  </section>
}
