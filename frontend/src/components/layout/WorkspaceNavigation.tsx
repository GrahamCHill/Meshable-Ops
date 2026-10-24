import {useState} from 'react'
import {NavLink,useLocation} from 'react-router-dom'
import {useAccess} from '../../api/access'
import {useAvailablePlugins} from '../../api/plugins'
import {useNavigation,ordered} from '../../api/navigation'
import {pageModule} from '../../plugins/registry'

export function WorkspaceNavigation({searchEnabled}:{searchEnabled:boolean}) {
  const navigation=useNavigation(),plugins=useAvailablePlugins(),access=useAccess(),location=useLocation()
  const [expanded,setExpanded]=useState<Record<string,boolean>>({})
  const preferences=navigation.data?.preferences
  const items=ordered(navigation.data?.items??[],preferences?.order).filter(item=>{
    if(item.id==='search'&&!searchEnabled)return false
    if(item.kind==='CORE')return access.canPath(item.path)
    const plugin=plugins.data?.find(plugin=>plugin.manifest.id===item.pluginId)
    const module=plugin&&pageModule(plugin)
    return !!module&&(!module.navigationPath||access.canPath(module.navigationPath))
  })
  return <nav className="sidebar-nav" aria-label="Main navigation"><div className="nav-caption">Workspace</div>
    {navigation.isLoading&&<p className="nav-item" role="status">Loading navigation…</p>}
    {navigation.isError&&<div className="nav-item"><p role="alert">Navigation could not load.</p><button onClick={()=>void navigation.refetch()}>Retry</button></div>}
    {items.map(item=>{
      const children=ordered(item.children,preferences?.submenus[item.id]),open=expanded[item.id]??false
      const to=item.kind==='EXTENSION'?`${item.path}?placement=navigation.links&returnTo=${encodeURIComponent(location.pathname)}`:item.path
      return <div key={item.id} className="plugin-navigation"><div className="plugin-navigation-row">
        <NavLink to={to} end={item.kind!=='CORE'||item.path==='/'} className={({isActive})=>`nav-item${isActive?' is-active':''}`}>{item.label}</NavLink>
        {!!children.length&&<button type="button" className="submenu-toggle" aria-label={`${open?'Collapse':'Expand'} ${item.label} sections`} aria-expanded={open} aria-controls={`submenu-${item.id}`} onClick={()=>setExpanded({...expanded,[item.id]:!open})}>{open?'▴':'▾'}</button>}
      </div>{!!children.length&&<div id={`submenu-${item.id}`} hidden={!open} className="plugin-submenu" aria-label={`${item.label} sections`}>{children.map(child=><NavLink key={child.id} to={child.path} className={({isActive})=>`nav-item${isActive?' is-active':''}`}>{child.label}</NavLink>)}</div>}</div>
    })}
  </nav>
}
