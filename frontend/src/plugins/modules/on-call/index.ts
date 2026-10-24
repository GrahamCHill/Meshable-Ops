import type {WorkspacePluginModule} from '../../registry'
export default {id:'on-call',apiVersion:1,loadPage:()=>import('../../operations/OnCallPage')} satisfies WorkspacePluginModule
