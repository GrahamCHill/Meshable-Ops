import {useEffect,useId,useState} from 'react'
import DOMPurify from 'dompurify'
import {diagramSourceError} from './mermaidSource'

let renderQueue=Promise.resolve()
export function MermaidPreview({source,onValid}: {source:string;onValid?:(valid:boolean)=>void}) {
  const id='mermaid-'+useId().replace(/[^a-z0-9]/gi,''),[svg,setSvg]=useState(''),[error,setError]=useState(''),[loading,setLoading]=useState(false)
  useEffect(()=>{
    let cancelled=false
    setLoading(true);onValid?.(false)
    const timer=setTimeout(()=>{
      renderQueue=renderQueue.catch(()=>undefined).then(async()=>{
        if(cancelled)return
        try{
          const invalid=diagramSourceError(source)
          if(invalid)throw new Error(invalid)
          const mermaid=(await import('mermaid')).default
          mermaid.initialize({startOnLoad:false,securityLevel:'strict',theme:'default',htmlLabels:false,maxTextSize:20000,maxEdges:200,suppressErrorRendering:true,flowchart:{htmlLabels:false},secure:['securityLevel','startOnLoad','maxTextSize','maxEdges','suppressErrorRendering','htmlLabels']})
          const rendered=await mermaid.render(id,source)
          const clean=DOMPurify.sanitize(rendered.svg,{USE_PROFILES:{svg:true,svgFilters:true},FORBID_TAGS:['script','foreignObject','iframe','image'],FORBID_ATTR:['href','xlink:href','src']})
          if(!cancelled){setSvg(clean);setError('');onValid?.(true)}
        }catch(failure){if(!cancelled){setSvg('');setError(failure instanceof Error?failure.message:'Unable to render this diagram.');onValid?.(false)}}
        finally{if(!cancelled)setLoading(false)}
      })
    },300)
    return()=>{cancelled=true;clearTimeout(timer)}
  },[source,id,onValid])
  const html='<!doctype html><html><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="default-src \'none\'; style-src \'unsafe-inline\'; img-src data:;"><style>body{margin:12px;background:white;color:#172b4d}svg{max-width:100%;height:auto;display:block;margin:auto}</style></head><body>'+svg+'</body></html>'
  return <div className="project-mermaid-preview">{loading&&<p role="status">Rendering diagram…</p>}{error&&<p role="alert">{error}</p>}{svg&&<iframe title="Mermaid diagram preview" sandbox="" srcDoc={html}/>}</div>
}
