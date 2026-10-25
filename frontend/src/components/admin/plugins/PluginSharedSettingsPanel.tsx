import {useEffect,useState} from 'react'
import {useQuery,useQueryClient} from '@tanstack/react-query'
import {Link} from 'react-router-dom'
import {pluginSharedSettingsApi,type SharedBinding} from '../../../api/sharedSettings'
import {getErrorMessage} from '../adminUtils'
import {Button} from '../../ui'

export function PluginSharedSettingsPanel({pluginId,sectionId}:{pluginId:string;sectionId:string}) {
  const query=useQuery({queryKey:['plugin-shared-settings',pluginId],queryFn:()=>pluginSharedSettingsApi.bindings(pluginId)})
  if(query.isLoading)return <p role="status">Loading shared connections…</p>
  if(query.isError)return <p role="alert">Unable to load shared connections. <button onClick={()=>void query.refetch()}>Retry</button></p>
  return <>{query.data?.filter(b=>b.reference.sectionId===sectionId).map(binding=><Binding key={binding.reference.id} pluginId={pluginId} binding={binding}/>)}</>
}
function Binding({pluginId,binding}:{pluginId:string;binding:SharedBinding}) {
  const client=useQueryClient(),[selected,setSelected]=useState(binding.selectedProfileId??''),[saving,setSaving]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('')
  useEffect(()=>setSelected(binding.selectedProfileId??''),[binding.selectedProfileId])
  const save=async()=>{
    setSaving(true);setError('');setNotice('')
    try{await pluginSharedSettingsApi.bind(pluginId,binding.reference.id,selected||null,binding.revision);await Promise.all([client.invalidateQueries({queryKey:['plugin-shared-settings',pluginId]}),client.invalidateQueries({queryKey:['plugin-configuration',pluginId]})]);setNotice('Shared connection saved.')}
    catch(failure){setError(getErrorMessage(failure))}finally{setSaving(false)}
  }
  return <section className="page-stack">
    <h2>{binding.reference.label}</h2>
    <p className="field-help">Select a reusable connection. This plugin can use only the fields it declares; saved credentials remain on the server. Changing a shared connection affects every plugin linked to it.</p>
    <label className="form-field">Connection<select value={selected} disabled={saving||!binding.settingsPath} onChange={e=>{setSelected(e.target.value);setNotice('')}}><option value="">No connection</option>{binding.selectedProfileId&&!binding.profiles.some(p=>p.id===binding.selectedProfileId)&&<option value={binding.selectedProfileId}>Previously selected connection (unavailable)</option>}{binding.profiles.map(p=><option key={p.id} value={p.id}>{p.name}{!p.enabled?' — disabled':p.missingRequired.length?' — incomplete':''}</option>)}</select></label>
    {binding.settingsPath?<Link to={binding.settingsPath}>Manage shared connections</Link>:<p className="field-help">The connection provider is unavailable or you do not have permission to manage it.</p>}
    <div><Button disabled={saving||selected===(binding.selectedProfileId??'')||!binding.settingsPath} onClick={()=>void save()}>Save connection</Button></div>
    {error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
  </section>
}
