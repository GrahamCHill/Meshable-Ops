import type {WorkspacePluginModule} from '../../registry'

export default {
  id: 'workflows', apiVersion: 1, navigationPath: '/workflows',
  loadPage: () => import('./WorkflowsPage'),
  routes: [
    {path: '/workflows', label: 'Workflows', feature: 'workflows', loadPage: () => import('./WorkflowsPage')},
    {path: '/admin/workflows', label: 'Workflow builder', feature: 'admin.workflows', adminCategory: 'documents-workflows', description: 'Design and publish the steps members follow in Workflows.', loadPage: () => import('./AdminWorkflowsPage')},
  ],
} satisfies WorkspacePluginModule
