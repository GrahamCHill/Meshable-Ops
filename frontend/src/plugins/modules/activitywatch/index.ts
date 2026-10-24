import type {WorkspacePluginModule} from '../../registry'
export default {id:'activitywatch',apiVersion:1,loadPage:()=>import('../../operations/ActivityWatchPage')} satisfies WorkspacePluginModule
