import type {WorkspacePluginModule} from '../../registry'
export default {id:'billing',apiVersion:1,loadPage:()=>import('../../operations/BillingPage')} satisfies WorkspacePluginModule
