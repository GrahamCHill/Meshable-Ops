import type {WorkspacePluginModule} from '../../registry'
export default {id:'knowledge',apiVersion:1,loadPage:()=>import('./KnowledgePage')} satisfies WorkspacePluginModule
