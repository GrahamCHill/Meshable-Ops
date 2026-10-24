import {useQuery} from '@tanstack/react-query'
import {Link, useParams, useSearchParams} from 'react-router-dom'
import {pluginsApi} from '../../api/plugins'
import {hasPluginSettings, pluginSettingsPath} from '../../lib/pluginAdmin'
import {AdminPageHeader} from '../admin/AdminPageHeader'
import {PluginConfigurationForm} from '../admin/plugins/PluginConfigurationForm'
import {PluginSetting} from '../admin/plugins/PluginSetting'
import {MessagingMembers} from '../admin/plugins/MessagingMembers'

export default function AdminPluginSettingsPage() {
  const {pluginId,sectionId}=useParams(),[params,setParams]=useSearchParams()
  const query=useQuery({queryKey:['admin-plugins'],queryFn:pluginsApi.installed,refetchInterval:15000})
  const plugin=query.data?.find(p=>p.manifest.id===pluginId)
  if(query.isLoading)return <p role="status">Loading settings…</p>
  if(query.isError)return <div className="page-stack"><p role="alert">Unable to load settings.</p><button onClick={()=>void query.refetch()}>Retry</button></div>
  if(!plugin||!hasPluginSettings(plugin)||(plugin.manifest.adminMenu?.category??'workspace')!==sectionId)return <div className="page-stack"><AdminPageHeader title="Settings unavailable" description="Settings appear here only while the plugin is enabled and available."/><Link to="/admin/plugins">Manage plugin activation</Link></div>
  const members=['discord','telegram','teams'].includes(plugin.manifest.id)
  const tab=members&&params.get('tab')==='members'?'members':'settings'
  return <div className="page-stack" style={{maxWidth:900}}><AdminPageHeader title={plugin.manifest.adminMenu?.label||plugin.manifest.name} description={plugin.manifest.description}/>
    {members&&<nav className="workspace-tabs" aria-label={`${plugin.manifest.name} settings`}><button aria-current={tab==='settings'?'page':undefined} onClick={()=>setParams({})}>Settings</button><button aria-current={tab==='members'?'page':undefined} onClick={()=>setParams({tab:'members'})}>People & chats</button></nav>}
    {tab==='members'?<MessagingMembers key={pluginId} pluginId={plugin.manifest.id}/>:plugin.manifest.settingsFields.length?<PluginConfigurationForm key={pluginId} plugin={plugin}/>:<PluginSetting key={pluginId} plugin={plugin}/>}
    <Link to={pluginSettingsPath(plugin).split('/plugins/')[0]}>Back to {plugin.manifest.adminMenu?.categoryLabel||'admin section'}</Link>
  </div>
}
