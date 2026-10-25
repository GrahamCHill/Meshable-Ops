import {useMutation,useQueryClient} from '@tanstack/react-query'
import {useEffect} from 'react'
import {Button} from '../../ui'
import {pluginsApi,type PluginView} from '../../../api/plugins'
import {getErrorMessage} from '../adminUtils'

export type ActivationDraft=Record<string,{enabled:boolean;expectedRevision:number}>
export function PluginActivation({plugins,draft,setDraft,onPending}: {plugins:PluginView[];draft:ActivationDraft;setDraft:(draft:ActivationDraft)=>void;onPending:(busy:boolean)=>void}) {
  const client=useQueryClient()
  const save=useMutation({mutationFn:()=>pluginsApi.activation(Object.entries(draft).map(([id,value])=>({id,...value}))),onSuccess:async values=>{
    setDraft({});client.setQueryData(['admin-plugins'],values)
    await Promise.all(['plugins','my-access','access-policy','admin-notifications','notification-preferences','admin-navigation','workspace-navigation','admin-section-artwork','admin-section-icons'].map(key=>client.invalidateQueries({queryKey:[key]})))
  }})
  useEffect(()=>{onPending(save.isPending);return()=>onPending(false)},[save.isPending,onPending])
  const all=(enabled:boolean)=>setDraft(Object.fromEntries(plugins.filter(plugin=>!plugin.removed&&plugin.enabled!==enabled).map(plugin=>[plugin.manifest.id,{enabled,expectedRevision:plugin.settingsRevision}])))
  const count=Object.keys(draft).length
  return <section className="page-stack" aria-label="Plugin activation"><div className="inline-actions"><Button variant="secondary" disabled={save.isPending||!count} onClick={()=>save.mutate()}>Save activation changes{count?' ('+count+')':''}</Button><Button variant="ghost" disabled={save.isPending} onClick={()=>all(true)}>Select all</Button><Button variant="ghost" disabled={save.isPending} onClick={()=>all(false)}>Deselect all</Button>{count>0&&<Button variant="ghost" disabled={save.isPending} onClick={()=>{setDraft({});save.reset()}}>Discard choices</Button>}</div>
    <p className="field-help">Tick plugins below, then save to apply all activation changes together. Roles, search policies, settings and data are retained. Required supporting plugins and security rules still apply.</p>
    {save.isPending&&<p role="status">Saving activation choices…</p>}{save.isError&&<p role="alert">{getErrorMessage(save.error,'Unable to save activation choices.')} Your choices are retained; reload if another administrator changed these settings.</p>}{save.isSuccess&&!count&&<p role="status">Plugin activation changes saved.</p>}
  </section>
}
