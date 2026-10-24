import {useQuery} from '@tanstack/react-query'
import api from './http'
import {useAuthStore} from '../hooks/useAuthStore'

export interface NavigationPreferences {order:string[];submenus:Record<string,string[]>;revision:number}
export interface NavigationChild {id:string;label:string;path:string;roles:string[]}
export interface NavigationItem {id:string;label:string;path:string;kind:'CORE'|'PLUGIN'|'EXTENSION';feature:string;pluginId?:string;section:string;children:NavigationChild[]}
export interface NavigationView {preferences:NavigationPreferences;items:NavigationItem[]}
export const navigationApi = {
  current:()=>api.get<NavigationView>('/navigation').then(r=>r.data),
  admin:()=>api.get<NavigationView>('/admin/navigation').then(r=>r.data),
  save:(value:NavigationPreferences)=>api.put<NavigationView>('/admin/navigation',value).then(r=>r.data),
}
export function useNavigation() {
  const {user,token}=useAuthStore()
  return useQuery({queryKey:['workspace-navigation',user?.id,token],queryFn:navigationApi.current,enabled:!!token,refetchInterval:15000})
}
export function ordered<T extends {id:string}>(items:T[], order:string[]=[]):T[] {
  const ranks=new Map(order.map((id,index)=>[id,index]))
  return items.map((item,index)=>({item,index})).sort((a,b)=>(ranks.get(a.item.id)??Number.MAX_SAFE_INTEGER)-(ranks.get(b.item.id)??Number.MAX_SAFE_INTEGER)||a.index-b.index).map(({item})=>item)
}
/** Keep removed plugin positions reserved when administrators rearrange currently registered items. */
export function moveNavigation(order:string[], known:string[], id:string, target:string):string[] {
  const all=[...new Set([...order,...known])], visible=all.filter(key=>known.includes(key))
  const from=visible.indexOf(id), to=visible.indexOf(target)
  if(from<0||to<0)return all
  visible.splice(to,0,...visible.splice(from,1))
  let index=0
  return all.map(key=>known.includes(key)?visible[index++]:key)
}
