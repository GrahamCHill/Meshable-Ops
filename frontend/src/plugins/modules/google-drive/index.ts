import type {WorkspacePluginModule} from '../../registry'
export default {id:'google-drive',apiVersion:1,loadPage:()=>import('./GoogleDrivePage')} satisfies WorkspacePluginModule
