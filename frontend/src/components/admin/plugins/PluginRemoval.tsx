import {useState} from 'react'
import {useQueryClient} from '@tanstack/react-query'
import {useNavigate} from 'react-router-dom'
import {pluginsApi,type PluginView} from '../../../api/plugins'
import {useAuthStore} from '../../../hooks/useAuthStore'
import {getErrorMessage} from '../adminUtils'
import {Button} from '../../ui'

export function PluginRemoval({plugin}: {plugin:PluginView}) {
  const role=useAuthStore(state=>state.user?.role);const [confirmation,setConfirmation]=useState('');const [busy,setBusy]=useState(false);const [error,setError]=useState('');const client=useQueryClient();const navigate=useNavigate()
  if(role!=='SUPER_ADMIN')return null
  const run=async(restore:boolean)=>{setBusy(true);setError('');try{if(restore)await pluginsApi.restore(plugin.manifest.id);else await pluginsApi.remove(plugin.manifest.id,confirmation);await Promise.all([client.invalidateQueries({queryKey:['admin-plugins']}),client.invalidateQueries({queryKey:['plugins']}),client.invalidateQueries({queryKey:['my-access']}),client.invalidateQueries({queryKey:['admin-notifications']}),client.invalidateQueries({queryKey:['notification-preferences']}),client.invalidateQueries({queryKey:['admin-navigation']}),client.invalidateQueries({queryKey:['workspace-navigation']}),client.invalidateQueries({queryKey:['admin-section-artwork']}),client.invalidateQueries({queryKey:['admin-section-icons']})]);navigate('/admin/plugins')}catch(e){setError(getErrorMessage(e))}finally{setBusy(false)}}
  return <section className="page-stack"><h2>{plugin.removed?'Restore plugin':'Remove plugin'}</h2><p>{plugin.source==='BUNDLED'?'Bundled code remains in the application image. Removing it hides its pages and delivery methods and disables it; it can be restored here.':'Removing an uploaded plugin deletes its installed package. Upload the ZIP again to reinstall.'} Business tables and encrypted configuration are retained. Use Clear data separately before removal if those records should be emptied.</p>{plugin.removed?<div><Button disabled={busy} onClick={()=>void run(true)}>Restore disabled plugin</Button></div>:<><label className="form-field">Type {plugin.manifest.id} to confirm removal<input value={confirmation} onChange={e=>setConfirmation(e.target.value)} autoComplete="off"/></label><div><Button disabled={busy||confirmation!==plugin.manifest.id} onClick={()=>void run(false)}>Remove plugin</Button></div></>}{error&&<p role="alert">{error}</p>}</section>
}
