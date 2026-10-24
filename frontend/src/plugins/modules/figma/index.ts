import type {WorkspacePluginModule} from '../../registry'
export default {id:'figma',apiVersion:1,loadPage:()=>import('./FigmaPage'),loadExtensions:{'project.overview':()=>import('./FigmaProjectPanel')}} satisfies WorkspacePluginModule
