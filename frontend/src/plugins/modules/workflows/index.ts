import type {WorkspacePluginModule} from '../../registry'
import {PROJECT_MANAGEMENT_SECTION_ID} from '../../../components/admin/adminNavigation'

export default {
  id: 'workflows', apiVersion: 1, navigationPath: '/workflows',
  loadPage: () => import('./WorkflowsPage'),
  routes: [
    {path: '/workflows', label: 'Workflows', feature: 'workflows', loadPage: () => import('./WorkflowsPage')},
    {path: '/admin/workflows', label: 'Workflow builder', feature: 'admin.workflows', adminCategory: PROJECT_MANAGEMENT_SECTION_ID, description: 'Design and publish the steps members follow in Workflows.', loadPage: () => import('./AdminWorkflowsPage')},
  ],
} satisfies WorkspacePluginModule
