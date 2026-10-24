import {useInfiniteQuery} from '@tanstack/react-query'
import api from '../../api/http'
import {Button} from '../ui'
import {getErrorMessage} from '../admin/adminUtils'
import {useAuthStore} from '../../hooks/useAuthStore'
interface Page {repositories:string[];page:number;hasMore:boolean}
export function RepositorySelect({provider,value,onChange,disabled=false}:{provider:string;value:string;onChange:(value:string)=>void;disabled?:boolean}) {
  const user=useAuthStore(state=>state.user?.id)
  const query=useInfiniteQuery({queryKey:['repository-options',user,provider],initialPageParam:1,enabled:!!provider,retry:false,
    queryFn:({pageParam})=>api.get<Page>(`/plugins/${provider}/repository-options`,{params:{page:pageParam}}).then(r=>r.data),getNextPageParam:last=>last.hasMore?last.page+1:undefined})
  const repos=[...new Set(query.data?.pages.flatMap(page=>page.repositories)??[])].sort((a,b)=>a.localeCompare(b))
  return <div className="page-stack"><label className="form-field">Repository<select value={value} disabled={disabled||query.isLoading} onChange={e=>onChange(e.target.value)}><option value="">Choose repository</option>{repos.map(repo=><option key={repo}>{repo}</option>)}</select></label>
    {query.isError&&<p role="alert">{getErrorMessage(query.error,'Repositories could not load.')} <button type="button" onClick={()=>void query.refetch()}>Retry</button></p>}
    {query.hasNextPage&&<Button type="button" variant="ghost" size="sm" disabled={disabled||query.isFetching} onClick={()=>void query.fetchNextPage()}>Load more repositories</Button>}
  </div>
}
