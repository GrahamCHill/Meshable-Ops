import type {WorkspacePluginModule} from '../../registry'
export default {id:'jira',apiVersion:1,loadPage:()=>import('./JiraPage'),loadExtensions:{'project.overview':()=>import('./JiraProjectPanel')}} satisfies WorkspacePluginModule
