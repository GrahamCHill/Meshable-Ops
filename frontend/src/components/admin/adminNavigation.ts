export const adminGroups = [
  { label: 'Manage', items: [
    { to: '/admin/branding', label: 'Branding', description: 'Set your business name, colours, logos, and sign-in backgrounds.' },
    { to: '/admin/plugins', label: 'Plugins', description: 'Enable optional pages and functionality, including Kanban boards.' },
    { to: '/admin/users', label: 'Users & access', description: 'Manage accounts, roles, and upload permissions.' },
    { to: '/admin/storage', label: 'Storage connections', description: 'Connect S3 or Azure Blob and maintain the search index.' },
    { to: '/admin/documents', label: 'Document access', description: 'Control PDF viewing, downloads, and watermarks.' },
    { to: '/admin/email', label: 'Email delivery', description: 'Configure SMTP, app passwords, or email services and send messages.' },
    { to: '/account/security', label: 'Security & MFA', description: 'Set up authenticator or email verification and recovery codes.' },
    { to: '/admin/shares', label: 'File sharing', description: 'Email protected PDF attachments and manage shared links.' },
    { to: '/admin/webhooks', label: 'Webhooks', description: 'Send JSON, receive signed requests, and review deliveries.' },
    { to: '/admin/document-templates', label: 'Document templates', description: 'Design WeasyCanvas templates, data fields, quotes, and invoices.' },
  ] },
  { label: 'Analytics', items: [
    { to: '/admin/ai', label: 'AI & search', description: 'Configure AI providers, protect API keys, and index shared documents.' },
    { to: '/admin/superset-graph-builder', label: 'Superset workspace', description: 'Build dashboards, charts, and datasets.' },
    { to: '/admin/dashboard-links', label: 'Dashboard links', description: 'Choose the dashboards people see in the app.' },
  ] },
  { label: 'Operations', items: [
    { to: '/admin/system', label: 'System health', description: 'Check services, connectivity, and runtime details.' },
    { to: '/admin/audit', label: 'Audit & backup', description: 'Review activity and export application data.' },
  ] },
]
