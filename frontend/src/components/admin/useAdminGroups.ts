import {useQuery} from '@tanstack/react-query'
import {pluginsApi,useAvailablePlugins} from '../../api/plugins'
import {bundledRoutes} from '../../plugins/registry'
import {useAccess} from '../../api/access'
import {adminGroups} from './adminNavigation'

export function useAdminGroups() {
  const access=useAccess()
  const plugins=useQuery({queryKey:['admin-plugins'],queryFn:pluginsApi.installed,enabled:access.can('admin.plugins')})
  const available=useAvailablePlugins()
  const groups=adminGroups.map(group=>({...group,items:[...group.items]}))
  if(access.can('admin.plugins'))plugins.data?.filter(plugin=>!plugin.removed&&plugin.manifest.hasAdminSettings&&plugin.manifest.adminMenu).forEach(plugin=>{
    const menu=plugin.manifest.adminMenu!;let group=groups.find(group=>group.id===menu.category)
    if(!group){group={id:menu.category,label:menu.categoryLabel||menu.category,description:'Plugin connections and settings.',items:[]};groups.push(group)}
    group.items.push({to:`/admin/plugins?plugin=${plugin.manifest.id}&tab=${plugin.manifest.settingsFields.length?'configuration':'general'}`,label:menu.label||plugin.manifest.name,description:plugin.manifest.description})
  })
  bundledRoutes.filter(route=>route.adminCategory&&access.can(route.feature)&&available.data?.some(plugin=>plugin.source==='BUNDLED'&&plugin.manifest.id===route.pluginId)).forEach(route=>{
    groups.find(group=>group.id===route.adminCategory)?.items.push({to:route.path,label:route.label,description:route.description??route.label})
  })
  return groups
}
