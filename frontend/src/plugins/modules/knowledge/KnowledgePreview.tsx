import {useEffect,useState} from 'react'
import {useLocation,useNavigate} from 'react-router-dom'
import DOMPurify from 'dompurify'
import {renderMermaidSvg} from '../mermaid/renderMermaid'
import type {Rendered} from './api'

export function KnowledgePreview({content}:{content:Rendered}) {
  const [html,setHtml]=useState(''),[error,setError]=useState(''),navigate=useNavigate(),location=useLocation()
  useEffect(()=>{
    let cancelled=false
    setHtml('');setError('')
    void (async()=>{
      const document=new DOMParser().parseFromString(DOMPurify.sanitize(content.html),'text/html')
      for(const diagram of content.diagrams) {
        const target=[...document.querySelectorAll('[data-diagram]')].find(element=>element.getAttribute('data-diagram')===diagram.key)
        if(target)target.innerHTML=await renderMermaidSvg(diagram.source)
      }
      if(!cancelled)setHtml(document.body.innerHTML)
    })().catch(()=>{if(!cancelled){setHtml(DOMPurify.sanitize(content.html));setError('A Mermaid diagram could not render. Check its syntax before exporting.')}})
    return()=>{cancelled=true}
  },[content])
  useEffect(()=>{if(html&&location.hash){try{document.getElementById(decodeURIComponent(location.hash.slice(1)))?.scrollIntoView({block:'start'})}catch{/* Ignore malformed incoming fragments. */}}},[html,location.hash])
  return <>{error&&<p role="alert">{error}</p>}{!html&&<p role="status">Rendering document…</p>}<div className="knowledge-content" onClick={event=>{
    const target=(event.target as Element).closest('a')?.getAttribute('href')
    if(target?.startsWith('/plugins/knowledge?')){event.preventDefault();navigate(target)}
  }} dangerouslySetInnerHTML={{__html:html}}/></>
}
