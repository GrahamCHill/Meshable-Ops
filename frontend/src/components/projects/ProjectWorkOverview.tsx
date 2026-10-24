import {useQuery} from '@tanstack/react-query'
import {projectsApi} from '../../api/projects'
import {WorkCharts} from './WorkCharts'

export function ProjectWorkOverview({projectId}: {projectId:string}) {
  const summary=useQuery({queryKey:['project-work-summary',projectId],queryFn:()=>projectsApi.workSummary(projectId)})
  return <section className="page-stack"><h2>Delivery overview</h2><p className="field-help">Current tickets from project boards you can access.</p>
    {summary.isLoading&&<p role="status">Loading delivery overview…</p>}{summary.isError&&<p role="alert">Unable to load delivery overview. <button onClick={()=>void summary.refetch()}>Retry</button></p>}
    {summary.data&&<WorkCharts value={summary.data}/>}
  </section>
}
