import type {WorkspacePluginModule} from '../../registry'
export default {id:'billing-items',apiVersion:1,loadPage:()=>import('../../operations/BillingItemsPage')} satisfies WorkspacePluginModule
