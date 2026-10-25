export const adminGroups = [
  { id: 'workspace', label: 'Workspace', description: 'Business identity, appearance, and optional plugins.', items: [
    { to: '/admin/branding', label: 'Branding', description: 'Set your business name, colours, logos, and sign-in backgrounds.' },
    { to: '/admin/navigation', label: 'Navigation & section icons', description: 'Arrange menu items and set icons or WebP artwork for administration sections.' },
    { to: '/admin/plugins', label: 'Plugins', description: 'Enable optional pages and functionality, including Kanban boards.' },
  ] },
  { id: 'people-security', label: 'People & security', description: 'Accounts, permissions, external access, and extension security.', items: [
    { to: '/admin/access-controls', label: 'Access controls', description: 'Control role features, member views, external sharing, and protected cloud directories.' },
    { to: '/admin/users', label: 'Users', description: 'Manage accounts, roles, and upload permissions.' },
    { to: '/admin/projects', label: 'Project settings', description: 'Project sections, teams, shared report covers, AI insights, and report delivery.' },
    { to: '/admin/security', label: 'Security', description: 'Allow third-party extensions and manage account protection.' },
    { to: '/account/security', label: 'My security & MFA', description: 'Set up authenticator or email verification and recovery codes.' },
  ] },
  { id: 'storage-sharing', label: 'Storage & sharing', description: 'Cloud connections, downloads, watermarks, and protected sharing.', items: [
    { to: '/admin/storage', label: 'Storage connections', description: 'Connect S3, Azure Blob, or Google Cloud Storage and maintain the search index.' },
    { to: '/admin/documents', label: 'Document access', description: 'Control PDF viewing, downloads, and watermarks.' },
    { to: '/admin/shares', label: 'File sharing', description: 'Email protected PDF attachments and manage shared links.' },
  ] },
  { id: 'communications', label: 'Communications', description: 'Notifications, delivery accounts, message templates, and webhooks.', items: [
    { to: '/admin/notifications', label: 'Notifications & delivery', description: 'Delivery methods, message templates, member choices, and workflow alerts.' },
    { to: '/admin/email', label: 'Outgoing email (SMTP / API)', description: 'Send notifications, invoices, and attachments through one shared delivery account.' },
    { to: '/admin/email/inbox', label: 'Incoming email (IMAPS)', description: 'Shared inbox connections for plugins that read and process email replies.' },
    { to: '/admin/webhooks', label: 'Webhooks', description: 'Send JSON, receive signed requests, and review deliveries.' },
  ] },
  { id: 'documents-workflows', label: 'Documents & workflows', description: 'Report covers, reusable documents, and the processes that use them.', items: [
    { to: '/admin/projects/report-covers', label: 'Report covers', description: 'Redesign the shared project report cover, upload HTML and images, and preview the PDF.' },
  ] },
  { id: 'analytics-search', label: 'Analytics & search', description: 'Search providers, dashboards, and analytics authoring.', items: [
    { to: '/admin/superset-graph-builder', label: 'Superset workspace', description: 'Build dashboards, charts, and datasets.' },
    { to: '/admin/dashboard-links', label: 'Dashboard links', description: 'Choose the dashboards people see in the app.' },
  ] },
  { id: 'app-intelligence', label: 'App Intelligence', description: 'AI providers, embeddings, search and generated summaries.', items: [
    { to: '/admin/ai', label: 'App Intelligence', description: 'Configure the optional LLM and embeddings used for search, retrospectives and operations.' },
  ] },
  { id: 'operations', label: 'Operations', description: 'Service health, activity history, and application backups.', items: [
    { to: '/admin/system', label: 'System health', description: 'Check services, connectivity, and runtime details.' },
    { to: '/admin/events', label: 'Event log', description: 'Scroll through the complete event history and inspect event details.' },
    { to: '/admin/audit', label: 'Audit & backup', description: 'Review activity and export application data.' },
  ] },
]
