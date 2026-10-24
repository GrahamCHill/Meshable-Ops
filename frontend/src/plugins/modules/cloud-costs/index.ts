import type {WorkspacePluginModule} from '../../registry'
export default {id:'cloud-costs',apiVersion:1,loadPage:()=>import('../../operations/CloudCostsPage')} satisfies WorkspacePluginModule
