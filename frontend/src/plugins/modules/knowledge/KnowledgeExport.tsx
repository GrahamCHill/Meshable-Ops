import {useEffect,useId,useRef,useState} from 'react'
import {useMutation} from '@tanstack/react-query'
import {Button} from '../../../components/ui'
import {downloadProjectBlob} from '../../../api/projects'
import {getErrorMessage} from '../../../components/admin/adminUtils'
import {mermaidPng} from '../mermaid/renderMermaid'
import {knowledge,type PageSummary} from './api'

export function KnowledgeExport({pages,currentId,onClose}:{pages:PageSummary[];currentId:string;onClose:()=>void}) {
  const dialog=useRef<HTMLDialogElement>(null),label=useId()
  const [title,setTitle]=useState('Knowledge book'),[selected,setSelected]=useState<string[]>(()=>pages.some(page=>page.id===currentId)?[currentId]:[]),[filter,setFilter]=useState(''),[progress,setProgress]=useState('')
  useEffect(()=>{dialog.current?.showModal()},[])
  const exporting=useMutation({mutationFn:async()=>{
    const input={title,pages:selected.map(id=>{const page=pages.find(item=>item.id===id)!;return {id,revision:page.revision}})}
    setProgress('Checking export permission and document revisions…')
    const prepared=await knowledge.prepare(input),diagrams:Record<string,string>={}
    const blocks=prepared.flatMap(page=>page.diagrams)
    if(blocks.length>32)throw new Error('Choose documents with at most 32 Mermaid diagrams per book.')
    for(const [index,diagram] of blocks.entries()){setProgress(`Rendering diagram ${index+1} of ${blocks.length}…`);diagrams[diagram.key]=await mermaidPng(diagram.source)}
    setProgress('Building cover, contents and numbered PDF pages…')
    return knowledge.export(input,diagrams)
  },onSuccess:blob=>{downloadProjectBlob(blob,'knowledge-book.pdf');onClose()},onSettled:()=>setProgress('')})
  const move=(index:number,direction:number)=>setSelected(current=>{const result=[...current];[result[index],result[index+direction]]=[result[index+direction],result[index]];return result})
  return <dialog ref={dialog} aria-labelledby={label} className="knowledge-export-dialog" onCancel={event=>{event.preventDefault();if(!exporting.isPending)onClose()}}><div className="page-stack"><h2 id={label}>Export a PDF book</h2><p>Choose the documents and their order. Your book includes a cover, linked contents, images, diagrams, numbered pages and PDF bookmarks.</p>
    <label className="form-field">Book title<input autoFocus maxLength={180} value={title} disabled={exporting.isPending} onChange={event=>setTitle(event.target.value)}/></label>
    <div className="knowledge-export-columns"><section className="page-stack"><h3>Available documents</h3><label className="form-field">Filter documents<input type="search" value={filter} disabled={exporting.isPending} onChange={event=>setFilter(event.target.value)}/></label><div className="inline-actions"><Button variant="ghost" disabled={exporting.isPending||pages.length>50} onClick={()=>setSelected(pages.map(page=>page.id))}>Select all</Button><Button variant="ghost" disabled={exporting.isPending} onClick={()=>setSelected([])}>Clear selection</Button></div><div className="knowledge-export-choices">{pages.filter(page=>page.title.toLowerCase().includes(filter.toLowerCase())).map(page=><label className="checkbox-label" key={page.id}><input type="checkbox" checked={selected.includes(page.id)} disabled={exporting.isPending||selected.length>=50&&!selected.includes(page.id)} onChange={event=>setSelected(current=>event.target.checked?[...current,page.id]:current.filter(id=>id!==page.id))}/>{page.title}</label>)}</div></section>
      <section className="page-stack"><h3>Export order ({selected.length}/50)</h3><ol className="knowledge-export-order">{selected.map((id,index)=><li key={id}><span>{pages.find(page=>page.id===id)?.title}</span><div className="inline-actions"><Button variant="ghost" disabled={exporting.isPending||index===0} aria-label={`Move ${pages.find(page=>page.id===id)?.title} up`} onClick={()=>move(index,-1)}>↑</Button><Button variant="ghost" disabled={exporting.isPending||index===selected.length-1} aria-label={`Move ${pages.find(page=>page.id===id)?.title} down`} onClick={()=>move(index,1)}>↓</Button></div></li>)}</ol>{!selected.length&&<p>Select at least one document.</p>}</section></div>
    <p className="field-help">Child documents are selected individually. Links between included documents become PDF links; links to omitted documents have no PDF destination.</p>
    {progress&&<p role="status">{progress}</p>}{exporting.isError&&<p role="alert">{getErrorMessage(exporting.error)} Refresh document data if a selected page changed.</p>}
    <div className="inline-actions"><Button loading={exporting.isPending} disabled={exporting.isPending||!selected.length||!title.trim()} onClick={()=>exporting.mutate()}>Download PDF book</Button><Button variant="ghost" disabled={exporting.isPending} onClick={onClose}>Cancel</Button></div>
  </div></dialog>
}
