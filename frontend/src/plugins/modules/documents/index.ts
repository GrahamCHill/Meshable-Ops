import type {WorkspacePluginModule} from '../../registry'
import {PROJECT_MANAGEMENT_SECTION_ID} from '../../../components/admin/adminNavigation'

export default {
  id: 'documents', apiVersion: 1, navigationPath: '/documents',
  loadPage: () => import('./DocumentsPage'),
  routes: [
    {path: '/documents', label: 'Documents', feature: 'documents', loadPage: () => import('./DocumentsPage')},
    {path: '/admin/document-templates', label: 'Document templates', feature: 'admin.templates', adminCategory: PROJECT_MANAGEMENT_SECTION_ID, description: 'Design reusable documents with your own content, fields, and layout.', loadPage: () => import('./AdminTemplatesPage')},
  ],
} satisfies WorkspacePluginModule
