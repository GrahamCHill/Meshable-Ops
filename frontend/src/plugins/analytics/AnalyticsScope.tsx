import type {ReactNode} from 'react'
import {useQuery} from '@tanstack/react-query'
import {projectsApi} from '../../api/projects'
import {useAuthStore} from '../../hooks/useAuthStore'
import type {AnalyticsContext} from './api'

export function AnalyticsScope({kind,id,children}:{kind:AnalyticsContext;id:string;children:(canManage:boolean)=>ReactNode}) {
  const user=useAuthStore(state=>state.user)
  const scope=useQuery({queryKey:['analytics-scope',user?.id,kind,id],queryFn:async()=>kind==='CLIENT'?projectsApi.client(id):projectsApi.get(id)})
  if(scope.isLoading)return <p role="status">Loading {kind.toLowerCase()}…</p>
  if(scope.isError||!scope.data)return <p role="alert">This {kind.toLowerCase()} is unavailable.</p>
  return <>{children(scope.data.canManage)}</>
}
