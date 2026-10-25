import type {WorkspacePluginModule} from '../../registry'
export default {id:'google-analytics',apiVersion:1,loadPage:()=>import('../../analytics/AnalyticsPage'),loadExtensions:{'project.overview':()=>import('../../analytics/AnalyticsExtension'),'client.overview':()=>import('../../analytics/AnalyticsExtension')}} satisfies WorkspacePluginModule
