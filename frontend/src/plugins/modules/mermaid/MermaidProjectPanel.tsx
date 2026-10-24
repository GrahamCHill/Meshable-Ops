import {useState} from 'react'
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query'
import {Plus,Download} from 'lucide-react'
import {mermaidApi,type ProjectDiagram,type DiagramInput} from './api'
import {MermaidPreview} from './MermaidPreview'
import {Button} from '../../../components/ui'
import {getErrorMessage} from '../../../components/admin/adminUtils'
import {useAuthStore} from '../../../hooks/useAuthStore'
import {usePageDraft} from '../../../hooks/usePageDraft'
import {ProjectPluginScope} from '../../ProjectPluginFrame'
import {downloadProjectBlob} from '../../../api/projects'

const initial:DiagramInput={title:'Project workflow',source:'flowchart LR\n  Idea --> Design\n  Design --> Build\n  Build --> Review\n  Review --> Release',revision:-1}
export function MermaidProjectPanel({projectId,canManage}: {projectId:string;canManage:boolean}) {
  const user=useAuthStore(state=>state.user),client=useQueryClient(),queryKey=['mermaid-project-diagrams',user?.id,projectId]
  const diagrams=useQuery({queryKey,queryFn:()=>mermaidApi.list(projectId)})
  const [editing,setEditing]=useState<ProjectDiagram|'new'>(),[valid,setValid]=useState(false),[expanded,setExpanded]=useState<string[]>([])
  const [drafts,setDrafts]=usePageDraft<Record<string,DiagramInput>>('mermaid-diagrams:'+projectId,{})
  const key=editing==='new'?'new':editing?.id,draft=key?drafts[key]??(editing==='new'?initial:editing!):undefined
  const patch=(fields:Partial<DiagramInput>)=>{if(key&&draft)setDrafts({...drafts,[key]:{...draft,...fields}})}
  const save=useMutation({mutationFn:()=>mermaidApi.save(projectId,editing==='new'?undefined:editing?.id,draft!),onSuccess:()=>{if(key)setDrafts(Object.fromEntries(Object.entries(drafts).filter(([id])=>id!==key)));setEditing(undefined);void client.invalidateQueries({queryKey})}})
  const remove=useMutation({mutationFn:(diagram:ProjectDiagram)=>mermaidApi.remove(projectId,diagram),onSuccess:()=>client.invalidateQueries({queryKey})})
  return <section className="page-stack project-plugin-panel"><div className="page-heading"><p className="field-help">Maintain flowcharts, sequence diagrams and other project diagrams. Previews render locally; diagram source stays in your workspace.</p>{canManage&&<Button type="button" size="sm" variant="secondary" onClick={()=>{save.reset();setEditing('new')}}><Plus size={13}/>New diagram</Button>}</div>
    {diagrams.isLoading&&<p role="status">Loading diagrams…</p>}{diagrams.isError&&<p role="alert">Unable to load diagrams. <button type="button" onClick={()=>void diagrams.refetch()}>Retry</button></p>}
    {editing&&draft&&<div className="page-stack project-plugin-card"><div className="project-mermaid-editor"><div className="page-stack"><label className="form-field">Diagram name<input maxLength={120} disabled={save.isPending} value={draft.title} onChange={event=>patch({title:event.target.value})}/></label><label className="form-field">Mermaid source<textarea rows={16} maxLength={20000} spellCheck={false} disabled={save.isPending} value={draft.source} onChange={event=>patch({source:event.target.value})}/></label></div><MermaidPreview source={draft.source} onValid={setValid}/></div><div className="inline-actions"><Button type="button" loading={save.isPending} disabled={!valid||!draft.title.trim()} onClick={()=>save.mutate()}>Save diagram</Button><Button type="button" variant="ghost" disabled={save.isPending} onClick={()=>setEditing(undefined)}>Close</Button><small className="field-help">Closing keeps the draft on this page.</small></div>{save.isError&&<p role="alert">{getErrorMessage(save.error)}</p>}</div>}
    {diagrams.data?.map(diagram=><details className="project-plugin-card" key={diagram.id} onToggle={event=>{const open=event.currentTarget.open;setExpanded(value=>open?[...new Set([...value,diagram.id])]:value.filter(id=>id!==diagram.id))}}><summary>{diagram.title}</summary><div className="page-stack project-diagram-content">{expanded.includes(diagram.id)&&<MermaidPreview source={diagram.source}/>}<div className="inline-actions">{canManage&&<Button type="button" size="sm" variant="secondary" onClick={()=>{save.reset();setEditing(diagram)}}>Edit diagram</Button>}<Button type="button" size="sm" variant="ghost" onClick={()=>downloadProjectBlob(new Blob([diagram.source],{type:'text/plain;charset=utf-8'}),diagram.title.replace(/[^a-z0-9_-]/gi,'-')+'.mmd')}><Download size={13}/>Download source</Button>{canManage&&<Button type="button" size="sm" variant="ghost" disabled={remove.isPending} onClick={()=>remove.mutate(diagram)}>Remove diagram</Button>}</div></div></details>)}
    {remove.isError&&<p role="alert">{getErrorMessage(remove.error)}</p>}{!diagrams.isLoading&&!diagrams.data?.length&&!editing&&<p>No diagrams have been added.</p>}
  </section>
}
export default function MermaidProjectExtension({context}: {context:Record<string,unknown>}) {return typeof context.id==='string'?<ProjectPluginScope id={context.id}>{(id,canManage)=><MermaidProjectPanel projectId={id} canManage={canManage}/>}</ProjectPluginScope>:null}
