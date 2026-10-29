import {useMutation} from '@tanstack/react-query'
import {Download} from 'lucide-react'
import {Button} from '../../components/ui'
import {getErrorMessage} from '../../components/admin/adminUtils'
import {analyticsApi,type AnalyticsContext} from './api'

export function AnalyticsExports({plugin,kind,contextId,linkId,disabled=false}:{plugin:string;kind:AnalyticsContext;contextId:string;linkId?:string;disabled?:boolean}) {
  const download=useMutation({mutationFn:async(format:'csv'|'pdf')=>{
    let blob:Blob
    try {blob=await analyticsApi(plugin,kind,contextId).export(format,linkId)} catch(error) {
      const body=(error as {response?:{data?:unknown}})?.response?.data
      if(body instanceof Blob) {const value=await body.text();try {const parsed=JSON.parse(value);if(typeof parsed.message==='string')throw new Error(parsed.message)} catch(parsedError) {if(parsedError instanceof Error&&!(parsedError instanceof SyntaxError))throw parsedError}}
      throw error
    }
    const url=URL.createObjectURL(blob),anchor=document.createElement('a')
    const actualFormat=blob.type.toLowerCase().startsWith('application/pdf')?'pdf':blob.type.toLowerCase().startsWith('text/csv')?'csv':format
    anchor.href=url;anchor.download=`${plugin}-${kind.toLowerCase()}-insights${linkId?'-'+linkId:''}.${actualFormat}`
    anchor.click();setTimeout(()=>URL.revokeObjectURL(url),1000)
  }})
  return <div className="analytics-exports"><div className="inline-actions"><Button type="button" size="sm" variant="secondary" disabled={disabled||download.isPending} onClick={()=>download.mutate('csv')}><Download size={13}/>{linkId?'CSV':'Export CSV'}</Button><Button type="button" size="sm" variant="secondary" disabled={disabled||download.isPending} onClick={()=>download.mutate('pdf')}><Download size={13}/>{linkId?'PDF':'Export PDF'}</Button></div>{download.isError&&<p role="alert">{getErrorMessage(download.error)}</p>}{download.isSuccess&&<span role="status">Export downloaded.</span>}</div>
}
