import type {WorkspacePluginModule} from '../../registry'
export default {id:'meta-ads',apiVersion:1,loadPage:()=>import('../../analytics/AnalyticsPage'),loadExtensions:{'project.overview':()=>import('../../analytics/AnalyticsExtension'),'client.overview':()=>import('../../analytics/AnalyticsExtension')}} satisfies WorkspacePluginModule
