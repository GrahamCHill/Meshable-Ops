import {Link,useLocation} from 'react-router-dom'
import {useAvailablePlugins} from '../api/plugins'
import {pluginRole} from '../api/access'
import {useAuthStore} from '../hooks/useAuthStore'
import {Component,lazy,Suspense,useMemo,type ReactNode} from 'react'
import type {PluginView} from '../api/plugins'
import {extensionModule} from './registry'
const RuntimePanel=lazy(()=>import('./runtime/RuntimePluginPage'))
class ExtensionBoundary extends Component<{children:ReactNode},{failed:boolean}> {state={failed:false};static getDerivedStateFromError(){return{failed:true}};render(){return this.state.failed?<p role="alert">An extension could not load.</p>:this.props.children}}
function ExtensionPanel({plugin,slot,section,context}: {plugin:PluginView;slot:string;section:string;context:Record<string,unknown>}) {
  const loader=extensionModule(plugin,slot);const View=useMemo(()=>loader?lazy(loader):undefined,[loader])
  return <ExtensionBoundary><Suspense fallback={<p role="status">Loading extension…</p>}>{View?<View plugin={plugin} slot={slot} context={context}/>:plugin.source==='ZIP'?<RuntimePanel plugin={plugin} sectionOverride={section} embedded placement={slot} hostContext={context}/>:<p>Extension component unavailable in this build.</p>}</Suspense></ExtensionBoundary>
}

/** Declared actions, not arbitrary DOM access. Destination APIs still enforce plugin and row permissions. */
export function ApplicationExtensions({slot,compact=false,context={}}: {slot:string;compact?:boolean;context?:Record<string,unknown>}) {
  const plugins=useAvailablePlugins();const role=useAuthStore(state=>pluginRole(state.user?.role??''));const location=useLocation()
  const links=plugins.data?.flatMap(plugin=>(plugin.manifest.placements??[]).filter(item=>item.slot===slot&&(!item.section||plugin.manifest.menuItems.some(menu=>menu.path===item.section&&role&&menu.roles.includes(role)))).map(item=>({plugin,item})))??[]
  if(!links.length)return null
  return <div className={`application-extensions${compact?' is-compact':''}`} aria-label={`${slot} extensions`}>{links.map(({plugin,item})=>item.mode==='PANEL'&&!compact?<section className="application-extension-panel" key={`${plugin.manifest.id}:${item.section}`}><h2>{item.label}</h2><ExtensionPanel plugin={plugin} slot={slot} section={item.section} context={{pathname:location.pathname,...context}}/></section>:<Link key={`${plugin.manifest.id}:${item.section}`} to={`/plugins/${plugin.manifest.id}${item.section?'/'+item.section:''}?placement=${encodeURIComponent(slot)}&returnTo=${encodeURIComponent(location.pathname)}`}>{item.label}</Link>)}</div>
}
