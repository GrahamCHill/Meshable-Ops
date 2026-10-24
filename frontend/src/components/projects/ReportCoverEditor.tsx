import {useState} from 'react'
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query'
import api from '../../api/http'
import {downloadProjectBlob} from '../../api/projects'
import {usePageDraft} from '../../hooks/usePageDraft'
import {Button} from '../ui'
import {getErrorMessage} from '../admin/adminUtils'
import {coverImageBlob,coverImageReferences,readCoverHtml,readCoverImages} from './reportCoverFiles'

interface CoverDesign {html:string;revision:number;canManage:boolean;defaultHtml:string;assets:Record<string,string>}
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
  const client=useQueryClient(),[draft,setDraft]=usePageDraft('report-cover:'+projectId,()=>({html:value.html,revision:value.revision,assets:value.assets??{}}))
  const [fileError,setFileError]=useState(''),[fileNotice,setFileNotice]=useState(''),[loadingFiles,setLoadingFiles]=useState(false)
  const assets=draft.assets??{},missing=coverImageReferences(draft.html).filter(name=>!Object.keys(assets).some(key=>key.toLowerCase()===name))
  const root=`/projects/${projectId}/reports/design`
  const save=useMutation({mutationFn:()=>api.put<CoverDesign>(root,{...draft,assets}).then(r=>r.data),onSuccess:saved=>{setDraft({html:saved.html,revision:saved.revision,assets:saved.assets??{}});client.setQueryData(['project-report-cover',projectId],saved)}})
  const preview=useMutation({mutationFn:()=>api.post<Blob>(root+'/preview',{html:draft.html,title,period,assets},{responseType:'blob'}).then(r=>r.data),onSuccess:blob=>downloadProjectBlob(blob,'report-cover-preview.pdf')})
  const busy=save.isPending||preview.isPending||loadingFiles
  const importFile=async(file:File)=>{setLoadingFiles(true);setFileError('');setFileNotice('');try {const html=await readCoverHtml(file);setDraft(current=>({...current,html}));setFileNotice('HTML loaded into the draft. Upload matching images, preview, then save.')}catch(error){setFileError(error instanceof Error?error.message:'Unable to read the HTML file.')}finally{setLoadingFiles(false)}}
  const importImages=async(files:File[])=>{setLoadingFiles(true);setFileError('');setFileNotice('');try {const images=await readCoverImages(files,assets);setDraft(current=>({...current,assets:images}));setFileNotice('Images matched by filename. Preview and save to keep them with this cover.')}catch(error){setFileError(error instanceof Error?error.message:'Unable to read the images.')}finally{setLoadingFiles(false)}}
  return <div className="page-stack project-report-cover-editor"><h3>HTML report cover</h3><p>Design one A4 cover page with HTML and CSS. WeasyPrint renders this separately from the report. The cover and index come before numbered page 1.</p><p className="field-help">Text placeholders: <code>{'{{siteName}} {{companyName}} {{organization}} {{title}} {{project}} {{period}} {{ticketCount}} {{generatedAt}}'}</code>. Use <code>{'<img src="{{siteLogo}}" alt="Site logo">'}</code> for the current site logo. Site and company names come from Administration → Branding → Business identity. Preview downloads the actual rendered cover PDF.</p>
    <div className="inline-actions"><Button type="button" variant="secondary" disabled={busy} onClick={()=>downloadProjectBlob(new Blob([draft.html],{type:'text/html;charset=utf-8'}),'report-cover.html')}>Download HTML template</Button></div>
    {value.canManage&&<div className="project-fields"><label className="form-field">Upload edited HTML template<input type="file" accept=".html,.htm,text/html" disabled={busy} onChange={event=>{const file=event.currentTarget.files?.[0];event.currentTarget.value='';if(file)void importFile(file)}}/></label><label className="form-field">Upload template images<input type="file" multiple accept=".png,.jpg,.jpeg,.webp,image/png,image/jpeg,image/webp" disabled={busy} onChange={event=>{const files=Array.from(event.currentTarget.files??[]);event.currentTarget.value='';if(files.length)void importImages(files)}}/></label></div>}
    <p className="field-help">Reference images by filename, for example <code>{'<img src="images/banner.png">'}</code> or <code>{'background-image: url("banner.png")'}</code>. Upload matching PNG/JPEG/WebP files; matching ignores letter case and folders. Up to eight images, 1 MiB total. Uploading an existing filename replaces its draft image. External resources and active content are excluded.</p>
    {!!Object.keys(assets).length&&<ul>{Object.entries(assets).map(([name,image])=><li key={name}><span>{name}</span> <Button type="button" variant="ghost" disabled={busy} onClick={()=>downloadProjectBlob(coverImageBlob(image),name)}>Download image</Button>{value.canManage&&<Button type="button" variant="ghost" disabled={busy} onClick={()=>setDraft(current=>({...current,assets:Object.fromEntries(Object.entries(current.assets??{}).filter(([key])=>key!==name))}))}>Remove {name}</Button>}</li>)}</ul>}
    {!!missing.length&&<p role="alert">Upload matching images before previewing or saving: {missing.join(', ')}.</p>}{fileError&&<p role="alert">{fileError}</p>}{fileNotice&&<p role="status">{fileNotice}</p>}
    <label className="form-field">Cover HTML<textarea className="project-report-cover-source" rows={20} maxLength={30000} spellCheck={false} disabled={busy||!value.canManage} value={draft.html} onChange={event=>setDraft({...draft,html:event.target.value})}/></label><small>{draft.html.length}/30,000 characters</small>
    <div className="inline-actions"><Button type="button" variant="secondary" loading={preview.isPending} disabled={busy||!draft.html.trim()||!!missing.length} onClick={()=>preview.mutate()}>Preview cover PDF</Button>{value.canManage&&<><Button type="button" loading={save.isPending} disabled={busy||!draft.html.trim()||!!missing.length} onClick={()=>save.mutate()}>Save cover design</Button><Button type="button" variant="ghost" disabled={busy} onClick={()=>setDraft({...draft,html:value.defaultHtml})}>Use default design</Button><Button type="button" variant="ghost" disabled={busy} onClick={()=>setDraft({html:value.html,revision:value.revision,assets:value.assets??{}})}>Discard draft</Button></>}</div>
    {save.isError&&<p role="alert">{getErrorMessage(save.error)}</p>}{preview.isError&&<p role="alert">The cover could not render. Use supported HTML/CSS and fit the design on one A4 page.</p>}{save.isSuccess&&<p role="status">Cover design saved for this project's future reports.</p>}
  </div>
}
