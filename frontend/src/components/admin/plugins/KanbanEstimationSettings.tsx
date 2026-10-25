import {useEffect,useState} from 'react'
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query'
import {boardsApi,type ProjectEstimation} from '../../../plugins/modules/kanban/api'
import {projectsApi} from '../../../api/projects'
import {Button} from '../../ui'
import {getErrorMessage} from '../adminUtils'

export function KanbanEstimationSettings() {
  const [projectId,setProjectId]=useState('')
  const projects=useQuery({queryKey:['admin-kanban-projects'],queryFn:()=>projectsApi.list()})
  return <section className="page-stack"><h2>Project estimates</h2>
    <p className="field-help">Choose a project to override the organisation defaults above. Settings apply to all Kanban boards in that project. Existing estimates are kept when hidden.</p>
    {projects.isLoading?<p role="status">Loading projects…</p>:projects.isError?<p role="alert">Unable to load projects. <button type="button" onClick={()=>void projects.refetch()}>Retry</button></p>:projects.data?.length?<label className="form-field">Project<select value={projectId} onChange={event=>setProjectId(event.target.value)}><option value="">Choose a project</option>{projects.data.map(project=><option key={project.id} value={project.id}>{project.name}</option>)}</select></label>:<p>No projects available.</p>}
    {projectId&&<ProjectEstimationPanel key={projectId} projectId={projectId}/>}
  </section>
}

function ProjectEstimationPanel({projectId}: {projectId:string}) {
  const client=useQueryClient(),current=useQuery({queryKey:['project-estimation',projectId],queryFn:()=>boardsApi.estimation(projectId)})
  const [draft,setDraft]=useState<ProjectEstimation>()
  useEffect(()=>setDraft(current.data),[current.data])
  const save=useMutation({mutationFn:()=>boardsApi.saveEstimation(projectId,draft!),onSuccess:value=>{client.setQueryData(['project-estimation',projectId],value);void client.invalidateQueries({queryKey:['plugin-kanban-board']})}})
  if(current.isLoading)return <p role="status">Loading estimation settings…</p>
  if(current.isError||!draft)return <p role="alert">Unable to load estimation settings. <button type="button" onClick={()=>void current.refetch()}>Retry</button></p>
  return <div className="page-stack">
    <fieldset className="page-stack" disabled={!draft.canManage||save.isPending}>
      <label className="checkbox-label"><input type="checkbox" checked={draft.inherit} onChange={event=>setDraft({...draft,inherit:event.target.checked})}/>Use organisation defaults</label>
      <label className="checkbox-label"><input type="checkbox" disabled={draft.inherit} checked={draft.storyPoints} onChange={event=>setDraft({...draft,storyPoints:event.target.checked})}/>Story points</label>
      <label className="checkbox-label"><input type="checkbox" disabled={draft.inherit} checked={draft.tshirtSizes} onChange={event=>setDraft({...draft,tshirtSizes:event.target.checked})}/>T-shirt sizes (XS – XXL)</label>
      {draft.canManage&&<div><Button type="button" variant="secondary" loading={save.isPending} onClick={()=>save.mutate()}>Save estimation settings</Button></div>}
    </fieldset>{save.isError&&<p role="alert">{getErrorMessage(save.error)}</p>}{save.isSuccess&&<p role="status">Estimation settings saved.</p>}
  </div>
}
