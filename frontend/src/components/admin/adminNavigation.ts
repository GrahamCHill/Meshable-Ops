export const adminGroups = [
  { label: 'Manage', items: [
    { to: '/admin/users', label: 'Users & access', description: 'Manage accounts, roles, and upload permissions.' },
    { to: '/admin/storage', label: 'Storage connections', description: 'Connect S3 or Azure Blob and maintain the search index.' },
  ] },
  { label: 'Analytics', items: [
    { to: '/admin/superset-graph-builder', label: 'Superset workspace', description: 'Build dashboards, charts, and datasets.' },
    { to: '/admin/dashboard-links', label: 'Dashboard links', description: 'Choose the dashboards people see in the app.' },
  ] },
  { label: 'Operations', items: [
    { to: '/admin/system', label: 'System health', description: 'Check services, connectivity, and runtime details.' },
    { to: '/admin/audit', label: 'Audit & backup', description: 'Review activity and export application data.' },
  ] },
]
