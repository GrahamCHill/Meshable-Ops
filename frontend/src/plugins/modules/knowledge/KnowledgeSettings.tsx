import {useState} from 'react'
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query'
import {Button} from '../../../components/ui'
import {downloadProjectBlob} from '../../../api/projects'
import {getErrorMessage} from '../../../components/admin/adminUtils'
import {coverImageReferences,readCoverHtml,readCoverImages} from '../../../components/projects/reportCoverFiles'
import {knowledge,type Settings} from './api'

export function KnowledgeSettings() {
  const query=useQuery({queryKey:['knowledge-settings'],queryFn:knowledge.settings})
  if(query.isLoading)return <p role="status">Loading export settings…</p>
  if(query.isError||!query.data)return <p role="alert">Only administrators can open export settings. <button onClick={()=>void query.refetch()}>Retry</button></p>
  return <SettingsForm key={query.data.revision} value={query.data}/>
}

function SettingsForm({value}:{value:Settings}) {
  const client=useQueryClient(),[exporters,setExporters]=useState(value.exporterIds),[filter,setFilter]=useState('')
  const [html,setHtml]=useState(value.coverHtml),[assets,setAssets]=useState(value.coverAssets),[fileError,setFileError]=useState(''),[reading,setReading]=useState(false)
  const update=(saved:Settings)=>{client.setQueryData(['knowledge-settings'],saved);void client.invalidateQueries({queryKey:['knowledge-status']})}
  const permissions=useMutation({mutationFn:()=>knowledge.permissions(exporters,value.revision),onSuccess:update})
  const save=useMutation({mutationFn:()=>knowledge.saveSettings({...value,coverHtml:html,coverAssets:assets}),onSuccess:update})
  const preview=useMutation({mutationFn:()=>knowledge.coverPreview(html,assets),onSuccess:blob=>downloadProjectBlob(blob,'knowledge-cover-preview.pdf')})
  const busy=permissions.isPending||save.isPending||preview.isPending||reading
  const missing=coverImageReferences(html).filter(name=>!Object.keys(assets).includes(name))
  const importHtml=async(file:File)=>{setReading(true);setFileError('');try{setHtml(await readCoverHtml(file))}catch(error){setFileError(error instanceof Error?error.message:'Unable to read HTML.')}finally{setReading(false)}}
  const importImages=async(files:File[])=>{setReading(true);setFileError('');try{setAssets(await readCoverImages(files,assets))}catch(error){setFileError(error instanceof Error?error.message:'Unable to read images.')}finally{setReading(false)}}
  return <div className="page-stack knowledge-settings"><section className="page-stack"><h2>Who can export documentation?</h2><p>Only selected accounts can export PDF books. This permission is separate from reading or editing documents, and administrators must also be selected to export.</p><p className="field-help">No one has export permission by default. Removing an account from this list blocks new exports and downloads still being generated. Permission changes can be saved even when PDF rendering is unavailable.</p>
    <label className="form-field">Find a member<input type="search" value={filter} disabled={busy} onChange={event=>setFilter(event.target.value)} placeholder="Name or email"/></label>
    <div className="knowledge-member-list">{value.members.filter(member=>(member.name+' '+member.email).toLowerCase().includes(filter.toLowerCase())).map(member=><label key={member.id} className="checkbox-label"><input type="checkbox" checked={exporters.includes(member.id)} disabled={busy||member.active===false&&!exporters.includes(member.id)} onChange={event=>setExporters(current=>event.target.checked?[...current,member.id]:current.filter(id=>id!==member.id))}/><span><strong>{member.name}</strong><small>{member.email}{member.active===false?' · inactive or unavailable':''}</small></span></label>)}</div>
    <p>{exporters.length} selected {exporters.length===1?'account':'accounts'}</p><div><Button disabled={busy} loading={permissions.isPending} onClick={()=>permissions.mutate()}>Save export permissions</Button></div>{permissions.isError&&<p role="alert">{getErrorMessage(permissions.error)}</p>}
  </section><section className="page-stack"><h2>PDF book cover</h2><p>Design one A4 cover using HTML and CSS. The saved design applies to future knowledge books.</p><p className="field-help">Text placeholders: <code>{'{{title}} {{documentCount}} {{siteName}} {{companyName}} {{generatedAt}}'}</code>. Add the workspace logo with <code>{'<img src="{{siteLogo}}" alt="Workspace logo">'}</code>. Reference uploaded images by filename, for example <code>{'<img src="banner.png">'}</code>. Keep the bottom 10 mm clear for the page number.</p>
    <div className="inline-actions"><Button variant="secondary" disabled={busy} onClick={()=>downloadProjectBlob(new Blob([html],{type:'text/html;charset=utf-8'}),'knowledge-cover.html')}>Download HTML template</Button><Button variant="ghost" disabled={busy} onClick={()=>setHtml(value.defaultHtml)}>Use default design</Button></div>
    <div className="project-fields"><label className="form-field">Upload cover HTML<input type="file" accept=".html,.htm,text/html" disabled={busy} onChange={event=>{const file=event.currentTarget.files?.[0];event.currentTarget.value='';if(file)void importHtml(file)}}/></label><label className="form-field">Upload cover images<input type="file" multiple accept=".png,.jpg,.jpeg,.webp" disabled={busy} onChange={event=>{const files=Array.from(event.currentTarget.files??[]);event.currentTarget.value='';if(files.length)void importImages(files)}}/></label></div>
    {!!Object.keys(assets).length&&<ul>{Object.keys(assets).map(name=><li key={name}>{name} <Button variant="ghost" disabled={busy} onClick={()=>setAssets(current=>Object.fromEntries(Object.entries(current).filter(([key])=>key!==name)))}>Remove image</Button></li>)}</ul>}
    <label className="form-field">Cover HTML<textarea className="knowledge-source" rows={20} maxLength={30000} spellCheck={false} value={html} disabled={busy} onChange={event=>setHtml(event.target.value)}/></label>
    {!!missing.length&&<p role="alert">Upload matching images: {missing.join(', ')}.</p>}{fileError&&<p role="alert">{fileError}</p>}
    <div className="inline-actions"><Button variant="secondary" disabled={busy||!html.trim()||!!missing.length} loading={preview.isPending} onClick={()=>preview.mutate()}>Preview cover PDF</Button><Button disabled={busy||!html.trim()||!!missing.length} loading={save.isPending} onClick={()=>save.mutate()}>Save cover design</Button></div>
    {save.isError&&<p role="alert">{getErrorMessage(save.error)}</p>}{preview.isError&&<p role="alert">The cover could not render. Check its HTML, images, and one-page A4 layout.</p>}
  </section></div>
}
