import type {WorkspacePluginModule} from '../../registry'
export default {id:'mermaid',apiVersion:1,loadPage:()=>import('./MermaidPage'),loadExtensions:{'project.overview':()=>import('./MermaidProjectPanel')}} satisfies WorkspacePluginModule
