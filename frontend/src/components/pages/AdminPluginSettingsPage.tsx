import {useQuery} from '@tanstack/react-query'
import {Link, useParams, useSearchParams} from 'react-router-dom'
import {pluginsApi} from '../../api/plugins'
import {hasPluginSettings,pluginSettingsLocations,pluginSettingsPath} from '../../lib/pluginAdmin'
import {AdminPageHeader} from '../admin/AdminPageHeader'
import {PluginConfigurationForm} from '../admin/plugins/PluginConfigurationForm'
import {PluginSetting} from '../admin/plugins/PluginSetting'
import {MessagingMembers} from '../admin/plugins/MessagingMembers'
import {PluginSharedSettingsPanel} from '../admin/plugins/PluginSharedSettingsPanel'

export default function AdminPluginSettingsPage() {
  const {pluginId,sectionId,settingsId}=useParams(),[params,setParams]=useSearchParams()
  const query=useQuery({queryKey:['admin-plugins'],queryFn:pluginsApi.installed,refetchInterval:15000})
  const plugin=query.data?.find(p=>p.manifest.id===pluginId)
  const locations=plugin?pluginSettingsLocations(plugin):[],location=locations.find(s=>s.category===sectionId&&s.id===(settingsId??''))
  if(query.isLoading)return <p role="status">Loading settings…</p>
  if(query.isError)return <div className="page-stack"><p role="alert">Unable to load settings.</p><button onClick={()=>void query.refetch()}>Retry</button></div>
  if(!plugin||!hasPluginSettings(plugin)||!location)return <div className="page-stack"><AdminPageHeader title="Settings unavailable" description="Settings appear here only while the plugin is enabled and available."/><Link to="/admin/plugins">Manage plugin activation</Link></div>
  const members=!location.id&&['discord','telegram','teams'].includes(plugin.manifest.id)
  const tab=members&&params.get('tab')==='members'?'members':'settings'
  const refs=plugin.manifest.sharedSettings?.filter(r=>r.sectionId===location.id)??[]
  return <div className="page-stack" style={{maxWidth:900}}><AdminPageHeader title={location.label} description={location.description}/>
    {locations.length>1&&<nav className="inline-actions" aria-label={`${plugin.manifest.name} settings sections`}>{locations.filter(s=>s.id!==location.id).map(s=><Link key={s.id} to={pluginSettingsPath(plugin,s)}>{s.label}</Link>)}</nav>}
    {plugin.manifest.sharedSettingsSchemas?.map(s=><Link key={s.id} to={`/admin/sections/${encodeURIComponent(s.adminMenu.category)}/shared-settings/${encodeURIComponent(s.id)}`}>Manage {s.label.toLowerCase()} connections</Link>)}
    {members&&<nav className="workspace-tabs" aria-label={`${plugin.manifest.name} settings`}><button aria-current={tab==='settings'?'page':undefined} onClick={()=>setParams({})}>Settings</button><button aria-current={tab==='members'?'page':undefined} onClick={()=>setParams({tab:'members'})}>People & chats</button></nav>}
    {tab==='members'?<MessagingMembers key={pluginId} pluginId={plugin.manifest.id}/>:<>
      {location.fields.length>0&&<PluginConfigurationForm key={`${pluginId}:${location.id}`} plugin={plugin} fieldKeys={location.fields} title="Options"/>}
      {refs.length>0&&<PluginSharedSettingsPanel key={`${pluginId}:${location.id}:shared`} pluginId={plugin.manifest.id} sectionId={location.id}/>}
      {!location.id&&!location.fields.length&&!refs.length&&<PluginSetting key={pluginId} plugin={plugin}/>}
    </>}
    <Link to={`/admin/sections/${encodeURIComponent(location.category)}`}>Back to {location.categoryLabel||'admin section'}</Link>
  </div>
}
