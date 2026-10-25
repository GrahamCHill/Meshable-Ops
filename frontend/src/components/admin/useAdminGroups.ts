import {useQuery} from '@tanstack/react-query'
import {pluginsApi,useAvailablePlugins} from '../../api/plugins'
import {bundledRoutes} from '../../plugins/registry'
import {useAccess} from '../../api/access'
import {adminGroups} from './adminNavigation'
import {hasEditablePluginSettings,pluginSettingsLocations,pluginSettingsPath} from '../../lib/pluginAdmin'

export function useAdminGroups() {
  const access=useAccess()
  const plugins=useQuery({queryKey:['admin-plugins'],queryFn:pluginsApi.installed,enabled:access.can('admin.plugins'),refetchInterval:15000})
  const available=useAvailablePlugins()
  const groups=adminGroups.map(group=>({...group,items:[...group.items]}))
  if(access.can('admin.plugins'))plugins.data?.filter(hasEditablePluginSettings).forEach(plugin=>{
    const add=(category:string,categoryLabel:string,to:string,label:string,description:string)=>{
      let group=groups.find(group=>group.id===category)
      if(!group){group={id:category,label:categoryLabel||category,description:'Plugin connections and settings.',items:[]};groups.push(group)}
      group.items.push({to,label,description})
    }
    pluginSettingsLocations(plugin).forEach(s=>add(s.category,s.categoryLabel,pluginSettingsPath(plugin,s),s.label,s.description))
    plugin.manifest.sharedSettingsSchemas?.forEach(s=>add(s.adminMenu.category,s.adminMenu.categoryLabel,`/admin/sections/${encodeURIComponent(s.adminMenu.category)}/shared-settings/${encodeURIComponent(s.id)}`,s.adminMenu.label||s.label,s.description))
  })
  bundledRoutes.filter(route=>route.adminCategory&&access.can(route.feature)&&available.data?.some(plugin=>plugin.source==='BUNDLED'&&plugin.manifest.id===route.pluginId)).forEach(route=>{
    groups.find(group=>group.id===route.adminCategory)?.items.push({to:route.path,label:route.label,description:route.description??route.label})
  })
  return groups
}
