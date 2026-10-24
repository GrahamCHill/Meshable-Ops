import {useEffect,useState} from 'react'
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query'
import {boardsApi,type ProjectEstimation} from './api'
import {Button} from '../../../components/ui'
import {getErrorMessage} from '../../../components/admin/adminUtils'

export function ProjectEstimationPanel({projectId}: {projectId:string}) {
  const client=useQueryClient(),current=useQuery({queryKey:['project-estimation',projectId],queryFn:()=>boardsApi.estimation(projectId)})
  const [draft,setDraft]=useState<ProjectEstimation>()
  useEffect(()=>setDraft(current.data),[current.data])
  const save=useMutation({mutationFn:()=>boardsApi.saveEstimation(projectId,draft!),onSuccess:value=>{client.setQueryData(['project-estimation',projectId],value);void client.invalidateQueries({queryKey:['plugin-kanban-board']})}})
  if(current.isLoading)return <p role="status">Loading estimation settings…</p>
  if(current.isError||!draft)return <p role="alert">Unable to load estimation settings. <button type="button" onClick={()=>void current.refetch()}>Retry</button></p>
  return <section className="page-stack"><h3>Project estimates</h3><p className="field-help">Applies to all Kanban boards in this project. Organisation defaults are in Administration → Plugins → Kanban → Configuration. Existing estimates are kept when hidden.</p>
    <fieldset className="page-stack" disabled={!draft.canManage||save.isPending}>
      <label className="checkbox-label"><input type="checkbox" checked={draft.inherit} onChange={event=>setDraft({...draft,inherit:event.target.checked})}/>Use organisation defaults</label>
      <label className="checkbox-label"><input type="checkbox" disabled={draft.inherit} checked={draft.storyPoints} onChange={event=>setDraft({...draft,storyPoints:event.target.checked})}/>Story points</label>
      <label className="checkbox-label"><input type="checkbox" disabled={draft.inherit} checked={draft.tshirtSizes} onChange={event=>setDraft({...draft,tshirtSizes:event.target.checked})}/>T-shirt sizes (XS – XXL)</label>
      {draft.canManage&&<div><Button type="button" variant="secondary" loading={save.isPending} onClick={()=>save.mutate()}>Save estimation settings</Button></div>}
    </fieldset>{save.isError&&<p role="alert">{getErrorMessage(save.error)}</p>}{save.isSuccess&&<p role="status">Estimation settings saved.</p>}
  </section>
}
export default function ProjectEstimationExtension({context}: {context:Record<string,unknown>}) {return typeof context.id==='string'?<ProjectEstimationPanel projectId={context.id}/>:null}
