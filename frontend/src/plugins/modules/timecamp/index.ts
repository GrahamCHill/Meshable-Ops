import type {WorkspacePluginModule} from '../../registry'
export default {id:'timecamp',apiVersion:1,loadPage:()=>import('../../operations/TimeCampPage')} satisfies WorkspacePluginModule
