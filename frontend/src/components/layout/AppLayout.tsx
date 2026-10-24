import {useAccess,pluginRole,isAdminRole} from '../../api/access'
import {useEffect,useState,type ReactNode} from 'react'
import {NavLink,useNavigate,useLocation} from 'react-router-dom'
import {useAuthStore} from '../../hooks/useAuthStore'
import {authApi} from '../../api/auth'
import {SearchLauncher} from '../search/SearchLauncher'
import {Brand} from './Branding'
import {UserMenu} from './UserMenu'
import {useAvailablePlugins} from '../../api/plugins'
import {pageModule} from '../../plugins/registry'
import {ApplicationExtensions} from '../../plugins/ApplicationExtensions'
import {useQuery} from '@tanstack/react-query'
import {aiApi} from '../../api/ai'
const nav=[{to:'/',label:'Home'},{to:'/dashboards',label:'Dashboards'},{to:'/storage',label:'Storage'},{to:'/projects',label:'Projects'},{to:'/documents',label:'Documents'},{to:'/workflows',label:'Workflows'},{to:'/events',label:'Events'}]

export function AppLayout({children}: {children:ReactNode}) {
  const {token,user,refreshUser,logout}=useAuthStore();const navigate=useNavigate();const {pathname}=useLocation();const access=useAccess();const plugins=useAvailablePlugins()
  const preference=`workspace-sidebar:${user?.id}`
  const [collapsed,setCollapsed]=useState(()=>localStorage.getItem(preference)==='collapsed')
  const [expanded,setExpanded]=useState<Record<string,boolean>>({})
  useEffect(()=>setCollapsed(localStorage.getItem(preference)==='collapsed'),[preference])
  const search=useQuery({queryKey:['ai-search-status'],queryFn:aiApi.status,refetchInterval:30000,enabled:access.can('search')})
  useEffect(()=>{if(!token)return;const controller=new AbortController();authApi.refreshSessionCookie(controller.signal).then(data=>{if(!controller.signal.aborted&&useAuthStore.getState().token===token)refreshUser(data)}).catch(()=>{if(!controller.signal.aborted&&useAuthStore.getState().token===token){logout();navigate('/login',{replace:true})}});return()=>controller.abort()},[logout,navigate,refreshUser,token])
  const handleLogout=async()=>{try{await authApi.logout()}catch{/* Expired cookies must not block local sign out. */}logout();navigate('/login')}
  const renderNav=({to,label}:typeof nav[number])=><NavLink key={to} to={to} end={to==='/'} className={({isActive})=>`nav-item${isActive?' is-active':''}`}>{label}</NavLink>
  const feature=pathname.startsWith('/admin')?'admin':pathname.startsWith('/account')?'account':pathname.startsWith('/plugins')?'plugin':pathname.split('/')[1]||'home'
  return <div className={`app-shell${collapsed?' is-collapsed':''}`}>
    <header className="app-header"><div className="header-start"><button className="navigation-toggle" aria-label={collapsed?'Expand navigation':'Collapse navigation'} aria-expanded={!collapsed} aria-controls="app-navigation" onClick={()=>{setCollapsed(!collapsed);localStorage.setItem(preference,collapsed?'expanded':'collapsed')}}><span aria-hidden="true">{collapsed?'☰':'☰'}</span><span className="navigation-toggle-label">{collapsed?'Menu':'Collapse'}</span></button><Brand/></div><div className="header-end"><ApplicationExtensions slot="header.actions" compact/><UserMenu onLogout={handleLogout}/></div></header>
    <aside id="app-navigation" className="app-sidebar" aria-label="Workspace navigation" hidden={collapsed}>
      <nav className="sidebar-nav" aria-label="Main navigation"><div className="nav-caption">Workspace</div>{nav.filter(item=>access.canPath(item.to)).map(renderNav)}{access.can('search')&&search.data?.searchEnabled&&renderNav({to:'/search',label:'Search workspace'})}
      {plugins.data?.filter(plugin=>pageModule(plugin)).map(plugin=>{const items=plugin.manifest.menuItems.filter(item=>user&&item.roles.includes(pluginRole(user.role)));const open=expanded[plugin.manifest.id]??false;return <div key={plugin.manifest.id} className="plugin-navigation"><div className="plugin-navigation-row"><NavLink to={`/plugins/${plugin.manifest.id}`} end className={({isActive})=>`nav-item${isActive?' is-active':''}`}>{plugin.manifest.navigationLabel}</NavLink>{!!items.length&&<button className="submenu-toggle" aria-label={`${open?'Collapse':'Expand'} ${plugin.manifest.navigationLabel} sections`} aria-expanded={open} aria-controls={`submenu-${plugin.manifest.id}`} onClick={()=>setExpanded({...expanded,[plugin.manifest.id]:!open})}>{open?'▴':'▾'}</button>}</div>{!!items.length&&<div id={`submenu-${plugin.manifest.id}`} hidden={!open} className="plugin-submenu" aria-label={`${plugin.manifest.navigationLabel} sections`}>{items.map(item=>renderNav({to:`/plugins/${plugin.manifest.id}/${item.path}`,label:item.label}))}</div>}</div>})}
      <ApplicationExtensions slot="navigation.links" compact/></nav>
      {isAdminRole(user?.role)&&access.canPath('/admin')&&<div className="admin-navigation">{renderNav({to:'/admin',label:'Administration'})}</div>}
    </aside>
    <main className="app-main"><ApplicationExtensions slot={`page.${feature}.before`}/>{children}<ApplicationExtensions slot={`page.${feature}.after`}/></main><SearchLauncher/>
  </div>
}
