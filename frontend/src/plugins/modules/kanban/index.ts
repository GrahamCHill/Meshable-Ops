import type { WorkspacePluginModule } from '../../registry'

export default { id: 'kanban', apiVersion: 1, loadPage: () => import('./KanbanPage'),loadExtensions:{'project.overview':()=>import('./ProjectEstimationPanel')} } satisfies WorkspacePluginModule
