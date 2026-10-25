import {useState} from 'react'
import {Link,useParams,useSearchParams} from 'react-router-dom'
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query'
import type {PluginView} from '../../../api/plugins'
import {Button} from '../../../components/ui'
import {getErrorMessage} from '../../../components/admin/adminUtils'
import {knowledge,type PageSummary,type Status} from './api'
import {KnowledgePreview} from './KnowledgePreview'
import {KnowledgeEditor} from './KnowledgeEditor'
import {KnowledgeExport} from './KnowledgeExport'
import {KnowledgeSettings} from './KnowledgeSettings'
import './knowledge.css'

export default function KnowledgePage({plugin}:{plugin:PluginView}) {
  const {'*':section=''}=useParams(),[params,setParams]=useSearchParams(),client=useQueryClient()
  const [filter,setFilter]=useState(''),[editing,setEditing]=useState(false),[creating,setCreating]=useState(false),[exporting,setExporting]=useState(false)
  const pages=useQuery({queryKey:['knowledge-pages'],queryFn:knowledge.list})
  const status=useQuery({queryKey:['knowledge-status'],queryFn:knowledge.status,refetchInterval:30000})
  const id=params.get('page')??''
  const page=useQuery({queryKey:['knowledge-page',id],queryFn:()=>knowledge.page(id),enabled:!!id&&section!=='settings'})
  const open=(next:string)=>{setParams({page:next});setEditing(false);setCreating(false)}
  const saved=async(next:string)=>{await client.invalidateQueries({queryKey:['knowledge-pages']});await client.invalidateQueries({queryKey:['knowledge-page',next]});open(next)}
  return <div className="page-stack knowledge-workspace"><header className="knowledge-header"><div><p className="knowledge-eyebrow">WORKSPACE DOCUMENTATION</p><h1>{plugin.manifest.name}</h1><p>Write, connect and share your team's knowledge.</p></div><div className="inline-actions"><Link to="/plugins/knowledge">Documents</Link>{status.data?.canManage&&<Link to="/plugins/knowledge/settings">Export permissions &amp; cover</Link>}</div></header>
    {status.isError&&<p role="alert">Permissions could not load. <button onClick={()=>void status.refetch()}>Retry</button></p>}
    {section==='settings'?<KnowledgeSettings/>:<div className="knowledge-layout"><aside className="knowledge-sidebar"><div className="knowledge-sidebar-actions">{status.data?.canEdit&&<Button onClick={()=>{setCreating(true);setEditing(false)}}>New document</Button>}{status.data?.canExport&&<Button variant="secondary" onClick={()=>setExporting(true)} disabled={!pages.data?.length}>Export PDF book</Button>}</div>
      <label className="form-field">Find a document<input type="search" value={filter} onChange={event=>setFilter(event.target.value)} placeholder="Filter by title"/></label>
      {pages.isLoading&&<p role="status">Loading documents…</p>}{pages.isError&&<p role="alert">Documents could not load. <button onClick={()=>void pages.refetch()}>Retry</button></p>}
      <nav aria-label="Knowledge documents"><ul className="knowledge-tree">{tree(pages.data??[]).filter(item=>item.page.title.toLowerCase().includes(filter.toLowerCase())).map(({page:entry,depth})=><li key={entry.id} style={{paddingLeft:`${Math.min(depth,8)*12}px`}}><button className={entry.id===id&&!creating?'active':''} onClick={()=>open(entry.id)} title={entry.title}>{entry.title}</button></li>)}</ul></nav>
      {!status.data?.canExport&&status.data&&<p className="field-help">PDF export is available to members selected by an administrator.</p>}
    </aside><main className="knowledge-main">{creating?<KnowledgeEditor key="new" pages={pages.data??[]} parentId={id||null} onSaved={saved} onCancel={()=>setCreating(false)}/>:id?<>
      {page.isLoading&&<p role="status">Loading document…</p>}{page.isError&&<p role="alert">This document could not load. <button onClick={()=>void page.refetch()}>Retry</button></p>}
      {page.data&&(editing?<KnowledgeEditor key={`${id}:${page.data.revision}`} value={page.data} pages={pages.data??[]} onSaved={saved} onCancel={()=>setEditing(false)}/>:<Document page={page.data} status={status.data} onEdit={()=>setEditing(true)} onDeleted={()=>{setParams({});void client.invalidateQueries({queryKey:['knowledge-pages']})}}/>)}
    </>:<section className="knowledge-empty"><h2>A home for your team's knowledge</h2><p>Create Markdown documents, organize them into a page tree, and link ideas together. Build a PDF book from exactly the documents you need.</p>{status.data?.canEdit&&<Button onClick={()=>setCreating(true)}>Create a document</Button>}</section>}</main></div>}
    {exporting&&<KnowledgeExport pages={pages.data??[]} currentId={id} onClose={()=>setExporting(false)}/>}
  </div>
}

function Document({page,status,onEdit,onDeleted}:{page:Awaited<ReturnType<typeof knowledge.page>>;status?:Status;onEdit:()=>void;onDeleted:()=>void}) {
  const [confirm,setConfirm]=useState(false),[copied,setCopied]=useState(false),[copyError,setCopyError]=useState('')
  const remove=useMutation({mutationFn:()=>knowledge.remove(page.id,page.revision),onSuccess:onDeleted})
  const copy=async()=>{try{await navigator.clipboard.writeText(`[${page.title.replace(/[\[\]]/g,'')}](wiki:${page.id})`);setCopied(true)}catch{setCopyError(`Copy this page link: wiki:${page.id}`)}}
  return <article className="page-stack"><header className="knowledge-document-heading"><div><h2>{page.title}</h2><small>Revision {page.revision+1}</small></div><div className="inline-actions"><Button variant="secondary" onClick={()=>void copy()}>{copied?'Link copied':'Copy Markdown link'}</Button>{status?.canEdit&&<><Button onClick={onEdit}>Edit document</Button><Button variant="ghost" onClick={()=>setConfirm(!confirm)}>Delete</Button></>}</div></header>
    {copyError&&<p role="status">{copyError}</p>}{confirm&&<div className="feedback"><p>Delete “{page.title}”? Move any child documents first.</p><div className="inline-actions"><Button loading={remove.isPending} onClick={()=>remove.mutate()}>Delete document</Button><Button variant="ghost" onClick={()=>setConfirm(false)}>Keep document</Button></div></div>}{remove.isError&&<p role="alert">{getErrorMessage(remove.error)}</p>}
    {!!page.rendered.headings.length&&<details className="knowledge-outline"><summary>On this page</summary><ul>{page.rendered.headings.map(heading=><li key={heading.id} style={{marginLeft:`${Math.min(heading.level-1,3)*12}px`}}><a href={`#${heading.id}`}>{heading.title}</a></li>)}</ul></details>}
    <KnowledgePreview content={page.rendered}/>
  </article>
}

export function tree(pages:PageSummary[]):{page:PageSummary;depth:number}[] {
  const result:{page:PageSummary;depth:number}[]=[],visited=new Set<string>()
  const walk=(parent:string|null,depth:number)=>pages.filter(page=>page.parentId===parent).forEach(page=>{if(!visited.has(page.id)){visited.add(page.id);result.push({page,depth});walk(page.id,depth+1)}})
  walk(null,0)
  pages.forEach(page=>{if(!visited.has(page.id))result.push({page,depth:0})})
  return result
}
