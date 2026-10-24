import {useState} from 'react'
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query'
import api from '../../api/http'
import {downloadProjectBlob} from '../../api/projects'
import {usePageDraft} from '../../hooks/usePageDraft'
import {Button} from '../ui'
import {getErrorMessage} from '../admin/adminUtils'

interface CoverDesign {html:string;revision:number;canManage:boolean;defaultHtml:string}
export function ReportCoverEditor({projectId,title,period}:{projectId:string;title:string;period:string}) {
  const [open,setOpen]=useState(false)
  return <section className="page-stack"><div><Button type="button" variant="secondary" onClick={()=>setOpen(value=>!value)}>{open?'Close cover designer':'Design report cover'}</Button></div>{open&&<CoverLoader projectId={projectId} title={title} period={period}/>}</section>
}
function CoverLoader({projectId,title,period}:{projectId:string;title:string;period:string}) {
  const query=useQuery({queryKey:['project-report-cover',projectId],queryFn:()=>api.get<CoverDesign>(`/projects/${projectId}/reports/design`).then(r=>r.data)})
  if(query.isLoading)return <p role="status">Loading cover design…</p>
  if(query.isError||!query.data)return <p role="alert">Cover design could not load. <button type="button" onClick={()=>void query.refetch()}>Retry</button></p>
  return <CoverForm projectId={projectId} value={query.data} title={title} period={period}/>
}
function CoverForm({projectId,value,title,period}:{projectId:string;value:CoverDesign;title:string;period:string}) {
  const client=useQueryClient(),[draft,setDraft]=usePageDraft('report-cover:'+projectId,()=>({html:value.html,revision:value.revision}))
  const root=`/projects/${projectId}/reports/design`
  const save=useMutation({mutationFn:()=>api.put<CoverDesign>(root,draft).then(r=>r.data),onSuccess:saved=>{setDraft({html:saved.html,revision:saved.revision});client.setQueryData(['project-report-cover',projectId],saved)}})
  const preview=useMutation({mutationFn:()=>api.post<Blob>(root+'/preview',{html:draft.html,title,period},{responseType:'blob'}).then(r=>r.data),onSuccess:blob=>downloadProjectBlob(blob,'report-cover-preview.pdf')})
  const busy=save.isPending||preview.isPending
  return <div className="page-stack project-report-cover-editor"><h3>HTML report cover</h3><p>Design one A4 cover page with HTML and CSS. WeasyPrint renders this separately from the report. The cover and index come before numbered page 1.</p><p className="field-help">Use visible-text placeholders: <code>{'{{organization}} {{title}} {{project}} {{period}} {{ticketCount}} {{generatedAt}}'}</code>. Embedded PNG/JPEG/WebP images are supported; external resources and active content are excluded. Preview downloads the actual rendered cover PDF.</p>
    <label className="form-field">Cover HTML<textarea className="project-report-cover-source" rows={20} maxLength={30000} spellCheck={false} disabled={busy||!value.canManage} value={draft.html} onChange={event=>setDraft({...draft,html:event.target.value})}/></label><small>{draft.html.length}/30,000 characters</small>
    <div className="inline-actions"><Button type="button" variant="secondary" loading={preview.isPending} disabled={busy||!draft.html.trim()} onClick={()=>preview.mutate()}>Preview cover PDF</Button>{value.canManage&&<><Button type="button" loading={save.isPending} disabled={busy||!draft.html.trim()} onClick={()=>save.mutate()}>Save cover design</Button><Button type="button" variant="ghost" disabled={busy} onClick={()=>setDraft({...draft,html:value.defaultHtml})}>Use default design</Button><Button type="button" variant="ghost" disabled={busy} onClick={()=>setDraft({html:value.html,revision:value.revision})}>Discard draft</Button></>}</div>
    {save.isError&&<p role="alert">{getErrorMessage(save.error)}</p>}{preview.isError&&<p role="alert">The cover could not render. Use supported HTML/CSS and fit the design on one A4 page.</p>}{save.isSuccess&&<p role="status">Cover design saved for this project's future reports.</p>}
  </div>
}
