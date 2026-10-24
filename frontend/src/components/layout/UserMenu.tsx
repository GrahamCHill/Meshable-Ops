import {useEffect,useRef,useState} from 'react'
import {Link,useLocation} from 'react-router-dom'
import {useProfile} from '../../api/profile'
import {useAuthStore} from '../../hooks/useAuthStore'
import {useTheme} from '../../hooks/useTheme'
import {useAvailablePlugins} from '../../api/plugins'
import {ApplicationExtensions} from '../../plugins/ApplicationExtensions'
export function UserMenu({onLogout}: {onLogout:()=>Promise<void>}) {
  const plugins=useAvailablePlugins();const calendar=plugins.data?.some(plugin=>plugin.manifest.id==='calendar');const {user}=useAuthStore();const profile=useProfile();const {mode,toggleMode}=useTheme();const [open,setOpen]=useState(false);const root=useRef<HTMLDivElement>(null);const button=useRef<HTMLButtonElement>(null);const location=useLocation()
  const name=profile.data?.name||user?.name||user?.username||'Account'
  useEffect(()=>setOpen(false),[location.pathname,location.search])
  useEffect(()=>{if(!open)return;const close=(event:PointerEvent)=>{if(!root.current?.contains(event.target as Node))setOpen(false)};const key=(event:KeyboardEvent)=>{if(event.key==='Escape'){setOpen(false);button.current?.focus()}};document.addEventListener('pointerdown',close);document.addEventListener('keydown',key);return()=>{document.removeEventListener('pointerdown',close);document.removeEventListener('keydown',key)}},[open])
  return <div ref={root} className="user-menu"><button ref={button} className="account-button" aria-expanded={open} aria-controls="account-menu" onClick={()=>setOpen(!open)}>{profile.data?.avatarVersion?<img src={`/api/account/profile/avatar?v=${profile.data.avatarVersion}`} alt=""/>:<span className="account-initials" aria-hidden="true">{name.slice(0,2).toUpperCase()}</span>}<span className="account-name">{name}</span><span aria-hidden="true">▾</span></button>{open&&<div id="account-menu" className="account-menu" aria-label="Your account"><strong>{name}</strong><small>{user?.email}</small><Link to="/account/settings">User settings</Link><Link to="/account/settings?tab=security">Security & MFA</Link><Link to="/account/settings?tab=notifications">Notification preferences</Link>{calendar&&<Link to="/account/settings?tab=calendar">Calendar connections</Link>}<button onClick={toggleMode}>Use {mode==='dark'?'light':'dark'} theme</button><ApplicationExtensions slot="account.menu" compact/><button onClick={()=>void onLogout()}>Sign out</button></div>}</div>
}
