import {useState} from 'react'
import {Link,useSearchParams} from 'react-router-dom'
import {useQuery} from '@tanstack/react-query'
import type {PluginView} from '../../api/plugins'
import {projectsApi} from '../../api/projects'
import {useAuthStore} from '../../hooks/useAuthStore'
import {AnalyticsScope} from './AnalyticsScope'
import {AnalyticsPanel} from './AnalyticsPanel'
import type {AnalyticsContext} from './api'
import '../project-plugins.css'

export default function AnalyticsPage({plugin}:{plugin:PluginView}) {
  const [params,setParams]=useSearchParams(),[pickerKind,setPickerKind]=useState<AnalyticsContext>('PROJECT'),user=useAuthStore(state=>state.user)
  const kind:AnalyticsContext=params.get('project')?'PROJECT':params.get('client')?'CLIENT':pickerKind
  const selected=params.get(kind.toLowerCase())??''
  const options=useQuery({queryKey:['analytics-context-options',user?.id,kind],queryFn:async()=>kind==='CLIENT'?projectsApi.clients():projectsApi.list(),enabled:!selected})
  return <div className="page-stack project-plugin-workspace"><div className="page-heading"><div><h1>{plugin.manifest.name}</h1><p>Link reporting metrics to the work and clients they support.</p></div>{selected&&<Link to={kind==='CLIENT'?`/projects/clients/${selected}`:`/projects/${selected}`}>Open {kind.toLowerCase()}</Link>}</div>
    {selected?<><button className="analytics-text-button" onClick={()=>setParams({})}>Choose another client or project</button><AnalyticsScope kind={kind} id={selected}>{canManage=><AnalyticsPanel plugin={plugin} kind={kind} contextId={selected} canManage={canManage}/>}</AnalyticsScope></>:
      <><div className="project-fields"><label className="form-field">Link metrics to<select value={pickerKind} onChange={event=>setPickerKind(event.target.value as AnalyticsContext)}><option value="PROJECT">Project</option><option value="CLIENT">Client</option></select></label>
        <label className="form-field">{kind==='CLIENT'?'Client':'Project'}<select value="" disabled={options.isLoading||options.isError} onChange={event=>setParams({[kind.toLowerCase()]:event.target.value})}><option value="">Choose a {kind.toLowerCase()}</option>{options.data?.map(item=><option key={item.id} value={item.id}>{item.name}</option>)}</select></label></div>
        {options.isError&&<p role="alert">Unable to load {kind.toLowerCase()} options. <button onClick={()=>void options.refetch()}>Retry</button></p>}{options.data?.length===0&&<p>No accessible {kind==='CLIENT'?'clients':'projects'} are available.</p>}</>}
  </div>
}
