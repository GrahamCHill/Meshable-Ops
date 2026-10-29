import {useAccess,isAdminRole} from '../../api/access'
import {lazy,Suspense,useEffect,useState,type ReactNode} from 'react'
import {NavLink,useNavigate,useLocation} from 'react-router-dom'
import {useAuthStore} from '../../hooks/useAuthStore'
import {authApi} from '../../api/auth'
import {SearchLauncher} from '../search/SearchLauncher'
import {Brand} from './Branding'
import {UserMenu} from './UserMenu'
import {NavigationToggle} from './NavigationToggle'
import {WorkspaceNavigation} from './WorkspaceNavigation'
import {ApplicationExtensions} from '../../plugins/ApplicationExtensions'
import {useQuery} from '@tanstack/react-query'
import {aiApi} from '../../api/ai'
const WorkflowActionDialog=lazy(()=>import('../../plugins/modules/workflows/components/WorkflowActionDialog'))

export function AppLayout({children}: {children:ReactNode}) {
  const {token,user,refreshUser,logout}=useAuthStore();const navigate=useNavigate();const {pathname}=useLocation();const access=useAccess()
  const preference=`workspace-sidebar:${user?.id}`
  const [collapsed,setCollapsed]=useState(()=>localStorage.getItem(preference)==='collapsed')
  useEffect(()=>setCollapsed(localStorage.getItem(preference)==='collapsed'),[preference])
  const search=useQuery({queryKey:['ai-search-status'],queryFn:aiApi.status,refetchInterval:30000,enabled:access.can('search')})
  useEffect(()=>{if(!token)return;const controller=new AbortController();authApi.refreshSessionCookie(controller.signal).then(data=>{if(!controller.signal.aborted&&useAuthStore.getState().token===token)refreshUser(data)}).catch(()=>{if(!controller.signal.aborted&&useAuthStore.getState().token===token){logout();navigate('/login',{replace:true})}});return()=>controller.abort()},[logout,navigate,refreshUser,token])
  const handleLogout=async()=>{try{await authApi.logout()}catch{/* Expired cookies must not block local sign out. */}logout();navigate('/login')}
  const toggleNavigation=()=>setCollapsed(current=>{localStorage.setItem(preference,current?'expanded':'collapsed');return !current})
  const feature=pathname.startsWith('/admin')?'admin':pathname.startsWith('/account')?'account':pathname.startsWith('/plugins')?'plugin':pathname.split('/')[1]||'home'
  return <div className={`app-shell${collapsed?' is-collapsed':''}`}>
    <header className="app-header"><div className="header-start"><Brand/></div><div className="header-end"><ApplicationExtensions slot="header.actions" compact/><UserMenu onLogout={handleLogout}/></div></header>
    <NavigationToggle collapsed={collapsed} onToggle={toggleNavigation}/>
    <aside id="app-navigation" className="app-sidebar" aria-label="Workspace navigation" hidden={collapsed}>
      <WorkspaceNavigation searchEnabled={search.data?.searchEnabled===true}/>
      {isAdminRole(user?.role)&&access.canPath('/admin')&&<div className="admin-navigation"><NavLink to="/admin" className={({isActive})=>`nav-item${isActive?' is-active':''}`}>Administration</NavLink></div>}
    </aside>
    <main className="app-main"><ApplicationExtensions slot={`page.${feature}.before`}/>{children}<ApplicationExtensions slot={`page.${feature}.after`}/></main><SearchLauncher/><Suspense fallback={null}><WorkflowActionDialog/></Suspense>
  </div>
}
