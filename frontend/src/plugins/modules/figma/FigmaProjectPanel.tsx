import {useState} from 'react'
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query'
import {ExternalLink,RefreshCw} from 'lucide-react'
import {figmaApi,type FigmaFile} from './api'
import {Button} from '../../../components/ui'
import {getErrorMessage} from '../../../components/admin/adminUtils'
import {useAuthStore} from '../../../hooks/useAuthStore'
import {ProjectPluginScope} from '../../ProjectPluginFrame'

export function FigmaProjectPanel({projectId,canManage}: {projectId:string;canManage:boolean}) {
  const user=useAuthStore(state=>state.user),client=useQueryClient(),queryKey=['figma-project-files',user?.id,projectId]
  const files=useQuery({queryKey,queryFn:()=>figmaApi.list(projectId)})
  const [label,setLabel]=useState(''),[url,setUrl]=useState('')
  const add=useMutation({mutationFn:()=>figmaApi.link(projectId,label,url),onSuccess:()=>{setLabel('');setUrl('');void client.invalidateQueries({queryKey})}})
  const update=useMutation({mutationFn:({kind,file}:{kind:'refresh'|'remove';file:FigmaFile})=>kind==='refresh'?figmaApi.refresh(projectId,file):figmaApi.remove(projectId,file),onSuccess:()=>client.invalidateQueries({queryKey})})
  return <section className="page-stack project-plugin-panel"><p className="field-help">Link design files, prototypes and boards. Access to the original stays in Figma. Metadata refresh requires an approved file and a configured Figma token.</p>
    {files.isLoading&&<p role="status">Loading designs…</p>}{files.isError&&<p role="alert">Unable to load Figma links. <button type="button" onClick={()=>void files.refetch()}>Retry</button></p>}
    <div className="project-plugin-cards">{files.data?.map(file=><article className="project-plugin-card page-stack" key={file.id}><h3>{file.label}</h3><a href={file.url} target="_blank" rel="noreferrer">Open in Figma <ExternalLink size={13}/></a>{file.metadata&&<><strong>{file.metadata.name}</strong><small>{file.metadata.editorType} · Updated {new Date(file.metadata.lastModified).toLocaleString()}</small><small className="field-help">Version {file.metadata.version}</small></>}{canManage&&<div className="inline-actions"><Button type="button" size="sm" variant="secondary" disabled={update.isPending} onClick={()=>update.mutate({kind:'refresh',file})}><RefreshCw size={13}/>Refresh metadata</Button><Button type="button" size="sm" variant="ghost" disabled={update.isPending} onClick={()=>update.mutate({kind:'remove',file})}>Unlink</Button></div>}</article>)}</div>
    {update.isError&&<p role="alert">{getErrorMessage(update.error)}</p>}
    {canManage&&<form className="project-plugin-add" onSubmit={event=>{event.preventDefault();add.mutate()}}><label className="form-field">Design name<input required maxLength={120} value={label} onChange={event=>setLabel(event.target.value)}/></label><label className="form-field">Figma URL<input type="url" required maxLength={2000} placeholder="https://www.figma.com/design/…" value={url} onChange={event=>setUrl(event.target.value)}/></label><Button type="submit" loading={add.isPending} disabled={!label.trim()||!url.trim()}>Link design</Button>{add.isError&&<p role="alert">{getErrorMessage(add.error)}</p>}</form>}
    {!canManage&&!files.data?.length&&<p>No designs are linked.</p>}
  </section>
}
export default function FigmaProjectExtension({context}: {context:Record<string,unknown>}) {return typeof context.id==='string'?<ProjectPluginScope id={context.id}>{(id,canManage)=><FigmaProjectPanel projectId={id} canManage={canManage}/>}</ProjectPluginScope>:null}
