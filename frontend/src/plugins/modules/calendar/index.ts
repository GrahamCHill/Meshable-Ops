import type { WorkspacePluginModule } from '../../registry'
export default {id:'calendar',apiVersion:1,loadPage:() => import('./CalendarPage')} satisfies WorkspacePluginModule
