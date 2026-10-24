import {useInfiniteQuery} from '@tanstack/react-query'
import api from '../../../api/http'
import {Button} from '../../ui'
import {getErrorMessage} from '../adminUtils'
interface Page {repositories:string[];page:number;hasMore:boolean}
export function RepositoryScopeSettings({id,values,onChange,ready,revision}:{id:string;values:Record<string,string>;onChange:(key:string,value:string)=>void;ready:boolean;revision:number}) {
  const all=values.repositoryAccess==='ALL', selected=(values.repositories??'').split(',').map(value=>value.trim()).filter(Boolean)
  const query=useInfiniteQuery({queryKey:['repo-discovery',id,revision],initialPageParam:1,enabled:false,retry:false,
    queryFn:({pageParam})=>api.get<Page>(`/admin/plugins/${id}/repository-options`,{params:{page:pageParam}}).then(r=>r.data),getNextPageParam:last=>last.hasMore?last.page+1:undefined})
  const repositories=[...new Set(query.data?.pages.flatMap(page=>page.repositories)??[])].sort((a,b)=>a.localeCompare(b))
  return <section className="page-stack"><h3>Repository access</h3><label className="checkbox-label"><input type="checkbox" checked={all} onChange={e=>onChange('repositoryAccess',e.target.checked?'ALL':'SELECTED')}/>Allow all repositories accessible to this token</label>
    <p>{all?'All enabled members of this plugin can use repositories accessible to the stored token.':'Only the selected repositories below can be linked to projects, boards, and cards.'}</p>
    {!all&&<><div className="inline-actions"><Button type="button" variant="secondary" disabled={!ready||query.isFetching} onClick={()=>void query.refetch()}>Load repositories from provider</Button></div>{!ready&&<p>Save the provider URL and access token before loading repositories.</p>}
      {ready&&query.isError&&<p role="alert">{getErrorMessage(query.error,'Unable to load repositories.')}</p>}
      {!!selected.length&&<div><strong>Selected repositories</strong><ul>{selected.map(repo=><li key={repo}>{repo} <button type="button" onClick={()=>onChange('repositories',selected.filter(value=>value!==repo).join(','))}>Remove</button></li>)}</ul></div>}
      {ready&&!!repositories.length&&<div className="repository-options" role="group" aria-label="Available repositories">{repositories.map(repo=><label key={repo}><input type="checkbox" checked={selected.includes(repo)} disabled={!selected.includes(repo)&&(selected.length>=100||[...selected,repo].join(',').length>4096)} onChange={e=>onChange('repositories',(e.target.checked?[...selected,repo]:selected.filter(value=>value!==repo)).join(','))}/>{repo}</label>)}</div>}
      {ready&&query.hasNextPage&&<Button type="button" variant="ghost" disabled={query.isFetching} onClick={()=>void query.fetchNextPage()}>Load more repositories</Button>}
      {ready&&query.isSuccess&&!repositories.length&&<p>No repositories were returned. Check the token's repository permissions.</p>}
    </>}
  </section>
}
