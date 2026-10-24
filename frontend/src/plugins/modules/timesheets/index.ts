import type {WorkspacePluginModule} from '../../registry'
export default {id:'timesheets',apiVersion:1,loadPage:()=>import('../../operations/TimesheetsPage'),loadExtensions:{'project.overview':()=>import('./ProjectHoursPanel')}} satisfies WorkspacePluginModule
