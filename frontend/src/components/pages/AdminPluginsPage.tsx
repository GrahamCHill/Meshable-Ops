import {useQuery} from '@tanstack/react-query'
import {Link,useSearchParams} from 'react-router-dom'
import {pluginsApi} from '../../api/plugins'
import {AdminPageHeader} from '../admin/AdminPageHeader'
import {PluginSetting} from '../admin/plugins/PluginSetting'
import {PluginConfigurationForm} from '../admin/plugins/PluginConfigurationForm'
import {PluginDataControls} from '../admin/plugins/PluginDataControls'
import {PluginConnections} from '../admin/plugins/PluginConnections'
import {PluginCompatibility} from '../admin/plugins/PluginCompatibility'
import {PluginRemoval} from '../admin/plugins/PluginRemoval'
import {useState} from 'react'
import {PluginInstaller} from '../admin/plugins/PluginInstaller'
import {MessagingMembers} from '../admin/plugins/MessagingMembers'
import {bundledRoutes} from '../../plugins/registry'
import {useAccess} from '../../api/access'
import {PluginCode} from '../admin/plugins/PluginCode'

export function AdminPluginsPage() {
  const access=useAccess()
  const plugins=useQuery({queryKey:['admin-plugins'],queryFn:pluginsApi.installed});const [params,setParams]=useSearchParams();const [filter,setFilter]=useState('');const [category,setCategory]=useState('');const [showRemoved,setShowRemoved]=useState(false)
  const selected=plugins.data?.find(plugin=>plugin.manifest.id===params.get('plugin'));const tab=params.get('tab')??'general'
  const hasConfiguration=selected?.manifest.hasAdminSettings&&selected.manifest.settingsFields.length>0
  const tabs=[{id:'general',label:'Manage'},...(hasConfiguration?[{id:'configuration',label:'Settings'}]:[]),...(selected&&(selected.features.length||selected.manifest.requires.length||selected.manifest.extends.length||selected.manifest.requiresCapabilities.length)?[{id:'connections',label:'Connections'}]:[]),...(['discord','telegram','teams'].includes(selected?.manifest.id??'')?[{id:'members',label:'People & chats'}]:[]),...(selected?.manifest.tables.length?[{id:'data',label:'Data'}]:[]),{id:'removal',label:'Remove / restore'}]
  const categories=[...new Set(plugins.data?.flatMap(plugin=>plugin.manifest.categories??[])??[])].sort()
  const nameFor=(id:string)=>plugins.data?.find(plugin=>plugin.manifest.id===id)?.manifest.name??id
  const relationships=(plugin:NonNullable<typeof selected>)=><>{!!plugin.manifest.requires.length&&<p><strong>Requires:</strong> {plugin.manifest.requires.map(nameFor).join(', ')}</p>}{!!plugin.manifest.extends.length&&<p><strong>Extends:</strong> {plugin.manifest.extends.map(nameFor).join(', ')}</p>}{!!plugin.missingDependencies.length&&<p role="status">Missing required support: {plugin.missingDependencies.join(', ')}</p>}</>
  return <div className="page-stack" style={{maxWidth:selected?850:1100}}><AdminPageHeader title={selected?selected.manifest.name:'Plugins'} description={selected?selected.manifest.description:'Discover, configure, and manage workspace extensions.'}/>
    {plugins.isLoading&&<p role="status">Loading plugins…</p>}{plugins.isError&&<p role="alert">Unable to load plugins. <button onClick={()=>void plugins.refetch()}>Retry</button></p>}
    {params.has('plugin')&&!selected&&!plugins.isLoading&&<p role="alert">Plugin unavailable. <Link to="/admin/plugins">Back to plugins</Link></p>}
    {selected?<><Link to="/admin/plugins">Back to plugins</Link>{selected.manifest.banner&&<img className="plugin-banner" src={`/api/admin/plugins/${selected.manifest.id}/assets/banner`} alt="" onError={e=>{e.currentTarget.hidden=true}}/>}
      <p>{selected.manifest.categories.join(' · ')} · Version {selected.manifest.version} · {selected.source==='ZIP'?'Uploaded ZIP':'Bundled'}</p>{!!selected.manifest.runtimes?.length&&<p>Runtimes: {selected.manifest.runtimes.map(runtime=>({REACT:'React',KOTLIN:'Kotlin',PYTHON:'Python'}[runtime])).join(', ')}</p>}{selected.source==='BUNDLED'&&selected.active&&<div className="inline-actions">{bundledRoutes.filter(route=>route.pluginId===selected.manifest.id&&access.can(route.feature)).map(route=><Link key={route.path} className="button button-secondary" to={route.path}>{route.label}</Link>)}</div>}{relationships(selected)}<PluginCompatibility plugin={selected}/>
      {selected.blockedBySecurity&&<p role="status">Blocked by the global third-party extensions security setting.</p>}{selected.source==='ZIP'&&<p className="field-help">Author: {selected.author?.name||'Not provided'} · Organisation: {selected.author?.organization||'Not provided'} · {selected.author?.email||'No email supplied'}</p>}
      {selected.source==='ZIP'&&<PluginCode key={selected.manifest.id} id={selected.manifest.id}/>}
      <nav className="workspace-tabs" aria-label="Plugin management">{tabs.map(item=><button key={item.id} aria-current={tab===item.id?'page':undefined} onClick={()=>setParams({plugin:selected.manifest.id,tab:item.id})}>{item.label}</button>)}</nav>
      {selected.removed?<PluginRemoval plugin={selected}/>:tab==='removal'?<PluginRemoval plugin={selected}/>:tab==='configuration'?hasConfiguration?<PluginConfigurationForm key={selected.manifest.id} plugin={selected}/>:<p>This plugin has no administrator configuration.</p>:tab==='members'&&['discord','telegram','teams'].includes(selected.manifest.id)?<MessagingMembers key={selected.manifest.id} pluginId={selected.manifest.id}/>:tab==='connections'?<PluginConnections plugin={selected}/>:tab==='data'&&selected.manifest.tables.length?<PluginDataControls plugin={selected}/>:<PluginSetting key={selected.manifest.id} plugin={selected}/>}
    </>:!params.has('plugin')&&<><PluginInstaller/><div className="profile-fields"><label className="form-field">Find a plugin<input type="search" value={filter} onChange={e=>setFilter(e.target.value)}/></label><label className="form-field">Category<select value={category} onChange={e=>setCategory(e.target.value)}><option value="">All categories</option>{categories.map(value=><option key={value}>{value}</option>)}</select></label></div><label><input type="checkbox" checked={showRemoved} onChange={e=>setShowRemoved(e.target.checked)}/>Show removed bundled plugins</label>
      <div className="admin-cards">{plugins.data?.filter(plugin=>(showRemoved||!plugin.removed)&&(!category||plugin.manifest.categories.includes(category))&&`${plugin.manifest.name} ${plugin.manifest.description}`.toLowerCase().includes(filter.toLowerCase())).map(plugin=><section key={plugin.manifest.id} className="page-stack plugin-catalog-card">{plugin.manifest.banner&&<img className="plugin-card-banner" src={`/api/admin/plugins/${plugin.manifest.id}/assets/banner`} alt="" onError={e=>{e.currentTarget.hidden=true}}/>}<div className="inline-actions">{plugin.manifest.icon&&<img className="plugin-icon" src={`/api/admin/plugins/${plugin.manifest.id}/assets/icon`} alt="" onError={e=>{e.currentTarget.hidden=true}}/>}<h2>{plugin.manifest.name}</h2></div><small>{plugin.manifest.categories.join(' · ')} · {plugin.removed?'Removed':plugin.blockedBySecurity?'Blocked by security':plugin.active?'Enabled':plugin.enabled?'Unavailable':'Disabled'} · {plugin.source==='ZIP'?'ZIP':'Bundled'}</small><p>{plugin.manifest.description}</p>{relationships(plugin)}<PluginCompatibility plugin={plugin} compact/><div className="inline-actions">{!plugin.removed&&plugin.manifest.hasAdminSettings&&!!plugin.manifest.settingsFields.length&&<Link className="button button-secondary" to={`/admin/plugins?plugin=${plugin.manifest.id}&tab=configuration`}>Settings</Link>}<Link to={`/admin/plugins?plugin=${plugin.manifest.id}`}>{plugin.removed?'Restore':'Manage'}</Link></div></section>)}</div>
    </>}
  </div>
}
