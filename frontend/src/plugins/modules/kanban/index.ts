import type { WorkspacePluginModule } from '../../registry'

export default { id: 'kanban', apiVersion: 1, loadPage: () => import('./KanbanPage') } satisfies WorkspacePluginModule
