import {useState} from 'react'
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query'
import api from '../../api/http'
import {downloadProjectBlob,projectsApi} from '../../api/projects'
import {usePageDraft} from '../../hooks/usePageDraft'
import {Button} from '../ui'
import {getErrorMessage} from './adminUtils'
import {coverImageBlob,coverImageReferences,readCoverHtml,readCoverImages} from '../projects/reportCoverFiles'
import {WorkSchedulesPanel} from '../projects/WorkSchedulesPanel'

interface CoverDesign {html:string;revision:number;canManage:boolean;defaultHtml:string;assets:Record<string,string>;aiInsightsEnabled:boolean}
export function ReportCoverEditor() {
  const query=useQuery({queryKey:['shared-report-cover'],queryFn:()=>api.get<CoverDesign>('/admin/projects/report-cover').then(r=>r.data)})
  if(query.isLoading)return <p role="status">Loading cover design…</p>
  if(query.isError||!query.data)return <p role="alert">Cover design could not load. <button type="button" onClick={()=>void query.refetch()}>Retry</button></p>
  return <><CoverForm value={query.data}/><ReportDeliverySettings/></>
}
function CoverForm({value}:{value:CoverDesign}) {
  const client=useQueryClient(),[draft,setDraft]=usePageDraft('report-cover:shared',()=>({html:value.html,revision:value.revision,assets:value.assets??{},aiInsightsEnabled:value.aiInsightsEnabled}))
  const [previewProject,setPreviewProject]=useState('')
  const projects=useQuery({queryKey:['report-admin-projects'],queryFn:()=>projectsApi.list()})
  const [fileError,setFileError]=useState(''),[fileNotice,setFileNotice]=useState(''),[loadingFiles,setLoadingFiles]=useState(false)
  const assets=draft.assets??{},missing=coverImageReferences(draft.html).filter(name=>!Object.keys(assets).some(key=>key.toLowerCase()===name))
  const root='/admin/projects/report-cover'
  const save=useMutation({mutationFn:()=>api.put<CoverDesign>(root,{...draft,assets}).then(r=>r.data),onSuccess:saved=>{setDraft({html:saved.html,revision:saved.revision,assets:saved.assets??{},aiInsightsEnabled:saved.aiInsightsEnabled});client.setQueryData(['shared-report-cover'],saved)}})
  const preview=useMutation({mutationFn:()=>api.post<Blob>(root+'/preview',{html:draft.html,projectId:previewProject||null,assets},{responseType:'blob'}).then(r=>r.data),onSuccess:blob=>downloadProjectBlob(blob,'report-cover-preview.pdf')})
  const busy=save.isPending||preview.isPending||loadingFiles
  const importFile=async(file:File)=>{setLoadingFiles(true);setFileError('');setFileNotice('');try {const html=await readCoverHtml(file);setDraft(current=>({...current,html}));setFileNotice('HTML loaded into the draft. Upload matching images, preview, then save.')}catch(error){setFileError(error instanceof Error?error.message:'Unable to read the HTML file.')}finally{setLoadingFiles(false)}}
  const importImages=async(files:File[])=>{setLoadingFiles(true);setFileError('');setFileNotice('');try {const images=await readCoverImages(files,assets);setDraft(current=>({...current,assets:images}));setFileNotice('Images matched by filename. Preview and save to keep them with this cover.')}catch(error){setFileError(error instanceof Error?error.message:'Unable to read the images.')}finally{setLoadingFiles(false)}}
  return <div className="page-stack project-report-cover-editor"><h2>Shared report cover</h2><p>This template applies to all project reports. Each report fills in its own project name and the logo uploaded for its client. Design one A4 cover page with HTML and CSS.</p>
    <label className="checkbox-label"><input type="checkbox" checked={draft.aiInsightsEnabled??false} disabled={busy} onChange={event=>setDraft({...draft,aiInsightsEnabled:event.target.checked})}/>Include LLM-summarised insights as the first page after the index</label><p className="field-help">Uses the configured App Intelligence chat model and this report's project facts. Reports still generate and send if the model is unavailable. Save report settings to apply this choice.</p>
    <p className="field-help">Text placeholders: <code>{'{{siteName}} {{companyName}} {{organization}} {{title}} {{projectName}} {{project}} {{period}} {{ticketCount}} {{generatedAt}}'}</code>. Use <code>{'<img src="{{clientLogo}}" alt="Client logo">'}</code> for the project's client logo and <code>{'<img src="{{siteLogo}}" alt="Site logo">'}</code> for the workspace logo. Missing logos render transparently.</p>
    <label className="form-field">Preview project<select value={previewProject} disabled={busy} onChange={event=>setPreviewProject(event.target.value)}><option value="">Example project</option>{projects.data?.map(project=><option key={project.id} value={project.id}>{project.name}</option>)}</select><small>The preview selection does not change which projects use this shared template.</small></label>{projects.isError&&<p role="alert">Projects could not load. <button type="button" onClick={()=>void projects.refetch()}>Retry</button></p>}
    <div className="inline-actions"><Button type="button" variant="secondary" disabled={busy} onClick={()=>downloadProjectBlob(new Blob([draft.html],{type:'text/html;charset=utf-8'}),'report-cover.html')}>Download HTML template</Button></div>
    {value.canManage&&<div className="project-fields"><label className="form-field">Upload edited HTML template<input type="file" accept=".html,.htm,text/html" disabled={busy} onChange={event=>{const file=event.currentTarget.files?.[0];event.currentTarget.value='';if(file)void importFile(file)}}/></label><label className="form-field">Upload template images<input type="file" multiple accept=".png,.jpg,.jpeg,.webp,image/png,image/jpeg,image/webp" disabled={busy} onChange={event=>{const files=Array.from(event.currentTarget.files??[]);event.currentTarget.value='';if(files.length)void importImages(files)}}/></label></div>}
    <p className="field-help">Reference images by filename, for example <code>{'<img src="images/banner.png">'}</code> or <code>{'background-image: url("banner.png")'}</code>. Upload matching PNG/JPEG/WebP files; matching ignores letter case and folders. Up to eight images, 1 MiB total. Uploading an existing filename replaces its draft image. External resources and active content are excluded.</p>
    {!!Object.keys(assets).length&&<ul>{Object.entries(assets).map(([name,image])=><li key={name}><span>{name}</span> <Button type="button" variant="ghost" disabled={busy} onClick={()=>downloadProjectBlob(coverImageBlob(image),name)}>Download image</Button>{value.canManage&&<Button type="button" variant="ghost" disabled={busy} onClick={()=>setDraft(current=>({...current,assets:Object.fromEntries(Object.entries(current.assets??{}).filter(([key])=>key!==name))}))}>Remove {name}</Button>}</li>)}</ul>}
    {!!missing.length&&<p role="alert">Upload matching images before previewing or saving: {missing.join(', ')}.</p>}{fileError&&<p role="alert">{fileError}</p>}{fileNotice&&<p role="status">{fileNotice}</p>}
    <label className="form-field">Cover HTML<textarea className="project-report-cover-source" rows={20} maxLength={30000} spellCheck={false} disabled={busy||!value.canManage} value={draft.html} onChange={event=>setDraft({...draft,html:event.target.value})}/></label><small>{draft.html.length}/30,000 characters</small>
    <div className="inline-actions"><Button type="button" variant="secondary" loading={preview.isPending} disabled={busy||!draft.html.trim()||!!missing.length} onClick={()=>preview.mutate()}>Preview cover PDF</Button>{value.canManage&&<><Button type="button" loading={save.isPending} disabled={busy||!draft.html.trim()||!!missing.length} onClick={()=>save.mutate()}>Save report settings</Button><Button type="button" variant="ghost" disabled={busy} onClick={()=>setDraft({...draft,html:value.defaultHtml})}>Use default design</Button><Button type="button" variant="ghost" disabled={busy} onClick={()=>setDraft({html:value.html,revision:value.revision,assets:value.assets??{},aiInsightsEnabled:value.aiInsightsEnabled})}>Discard draft</Button></>}</div>
    {save.isError&&<p role="alert">{getErrorMessage(save.error)}</p>}{preview.isError&&<p role="alert">The cover could not render. Use supported HTML/CSS and fit the design on one A4 page.</p>}{save.isSuccess&&<p role="status">Report settings saved for all future project reports.</p>}
  </div>
}

function ReportDeliverySettings() {
  const [projectId,setProjectId]=useState('')
  const projects=useQuery({queryKey:['report-admin-projects'],queryFn:()=>projectsApi.list()})
  return <section className="page-stack"><h2>Scheduled project reports</h2><p>Choose a project to set up monthly delivery, delivery after each completed sprint, or both. Each email contains only the selected project's report.</p>
    <label className="form-field">Project for delivery settings<select value={projectId} onChange={event=>setProjectId(event.target.value)}><option value="">Choose a project</option>{projects.data?.map(project=><option key={project.id} value={project.id}>{project.name}</option>)}</select></label>
    {projectId&&<WorkSchedulesPanel key={projectId} projectId={projectId} reportsOnly/>}
  </section>
}
