import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { lazy, Suspense, useMemo } from 'react'
import {bundledRoutes} from './plugins/registry'
import {BundledPluginRoute} from './plugins/BundledPluginRoute'
import { AccessBoundary } from './components/layout/AccessBoundary'
import { isAdminRole } from './api/access'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AppLayout } from './components/layout/AppLayout'
import { BrandingHead } from './components/layout/Branding'
import { AdminBrandingPage } from './components/pages/AdminBrandingPage'
import { AdminSecurityPage } from './components/pages/AdminSecurityPage'
import { AdminSectionPage } from './components/pages/AdminSectionPage'
import { AdminPage } from './components/pages/AdminPage'
import { DashboardPage } from './components/pages/DashboardPage'
import { DashboardsPage } from './components/pages/DashboardsPage'
import { SupersetAdminPage } from './components/pages/SupersetAdminPage'
import { AdminDashboardLinksPage } from './components/pages/AdminDashboardLinksPage'
import { AdminAiPage } from './components/pages/AdminAiPage'
import { AdminDocumentAccessPage } from './components/pages/AdminDocumentAccessPage'
import { AdminEmailPage } from './components/pages/AdminEmailPage'
import { AdminSharesPage } from './components/pages/AdminSharesPage'
import { AdminWebhooksPage } from './components/pages/AdminWebhooksPage'
import { SharedFilePage } from './components/pages/SharedFilePage'
import { AdminStoragePage } from './components/pages/AdminStoragePage'
import { AdminSystemPage } from './components/pages/AdminSystemPage'
import { AdminAuditPage } from './components/pages/AdminAuditPage'
import { ItemsPage } from './components/pages/ItemsPage'
import { SearchPage } from './components/pages/SearchPage'
import { AdminEventsPage } from './components/pages/AdminEventsPage'
import { LoginPage, RegisterPage } from './components/pages/AuthPages'
import { useAuthStore } from './hooks/useAuthStore'
import { ThemeProvider } from './hooks/useTheme'
import {PageDraftProvider} from './hooks/usePageDraft'
import './styles/global.css'

const AdminUsersPage = lazy(() => import('./components/pages/AdminUsersPage').then(module => ({default: module.AdminUsersPage})))
const AdminPluginsPage = lazy(() => import('./components/pages/AdminPluginsPage').then(module => ({default: module.AdminPluginsPage})))
const AdminPluginSettingsPage = lazy(() => import('./components/pages/AdminPluginSettingsPage'))
const SharedSettingsPage = lazy(() => import('./components/pages/SharedSettingsPage'))
const AdminAccessPage = lazy(() => import('./components/pages/AdminAccessPage'))
const PluginPage = lazy(() => import('./plugins/PluginPage'))
const AdminNotificationsPage = lazy(() => import('./components/pages/AdminNotificationsPage'))
const ProjectsPage = lazy(() => import('./components/pages/ProjectsPage'))
const ProjectDetailPage = lazy(() => import('./components/pages/ProjectDetailPage'))
const ClientDetailPage = lazy(() => import('./components/pages/ClientDetailPage'))
const AdminProjectSettingsPage = lazy(() => import('./components/pages/AdminProjectSettingsPage'))
const UserSettingsPage = lazy(() => import('./components/pages/UserSettingsPage'))
const AdminNavigationPage = lazy(() => import('./components/pages/AdminNavigationPage'))
const loading = (children: React.ReactNode) => <Suspense fallback={<p role="status">Loading workspace…</p>}>{children}</Suspense>

const makeQueryClient = () => new QueryClient({
  defaultOptions: { queries: { retry: 1, staleTime: 30_000 } },
})

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuthStore()
  return isAuthenticated() ? <>{children}</> : <Navigate to="/login" replace />
}

function AdminRoute({ children }: { children: React.ReactNode }) {
  const { user } = useAuthStore()
  return isAdminRole(user?.role) ? <>{children}</> : <Navigate to="/" replace />
}

function App() {
  const token = useAuthStore(state => state.token)
  const qc = useMemo(makeQueryClient, [token])
  return (
    <QueryClientProvider client={qc}>
      <ThemeProvider>
        <BrandingHead />
        <BrowserRouter>
          <PageDraftProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/shared/:token" element={<SharedFilePage />} />
            <Route path="/*" element={
              <ProtectedRoute>
                <AccessBoundary><AppLayout>
                  <Routes>
                    <Route path="/" element={<DashboardPage />} />
                    <Route path="/dashboards" element={<DashboardsPage />} />
                    <Route path="/admin" element={
                      <AdminRoute>
                        <AdminPage />
                      </AdminRoute>
                    } />
                    <Route path="/admin/access-controls" element={<AdminRoute>{loading(<AdminAccessPage />)}</AdminRoute>} />
                    <Route path="/admin/projects" element={<AdminRoute>{loading(<AdminProjectSettingsPage />)}</AdminRoute>} />
                    <Route path="/projects" element={loading(<ProjectsPage />)} />
                    <Route path="/projects/clients/:clientId" element={loading(<ClientDetailPage />)} />
                    <Route path="/projects/:projectId" element={loading(<ProjectDetailPage />)} />
                    <Route path="/admin/security" element={<AdminRoute><AdminSecurityPage /></AdminRoute>} />
                    <Route path="/admin/sections/:sectionId" element={<AdminRoute><AdminSectionPage /></AdminRoute>} />
                    <Route path="/admin/sections/:sectionId/plugins/:pluginId" element={<AdminRoute>{loading(<AdminPluginSettingsPage />)}</AdminRoute>} />
                    <Route path="/admin/sections/:sectionId/plugins/:pluginId/:settingsId" element={<AdminRoute>{loading(<AdminPluginSettingsPage />)}</AdminRoute>} />
                    <Route path="/admin/sections/:sectionId/shared-settings/:schemaId" element={<AdminRoute>{loading(<SharedSettingsPage />)}</AdminRoute>} />
                    <Route path="/admin/users" element={<AdminRoute>{loading(<AdminUsersPage />)}</AdminRoute>} />
                    <Route path="/admin/branding" element={<AdminRoute><AdminBrandingPage /></AdminRoute>} />
                    <Route path="/admin/navigation" element={<AdminRoute>{loading(<AdminNavigationPage />)}</AdminRoute>} />
                    <Route path="/admin/plugins" element={<AdminRoute>{loading(<AdminPluginsPage />)}</AdminRoute>} />
                    {bundledRoutes.map(route => <Route key={route.path} path={route.path} element={<BundledPluginRoute route={route}/>}/>)}
                    <Route path="/plugins/:pluginId/*" element={loading(<PluginPage />)} />
                    <Route path="/admin/ai" element={<AdminRoute><AdminAiPage /></AdminRoute>} />
                    <Route path="/admin/documents" element={<AdminRoute><AdminDocumentAccessPage /></AdminRoute>} />
                    <Route path="/admin/email" element={<AdminRoute><AdminEmailPage /></AdminRoute>} />
                    <Route path="/admin/email/inbox" element={<AdminRoute>{loading(<SharedSettingsPage incomingEmail />)}</AdminRoute>} />
                    <Route path="/admin/notifications" element={<AdminRoute>{loading(<AdminNotificationsPage />)}</AdminRoute>} />
                    <Route path="/account/settings" element={loading(<UserSettingsPage />)} />
                    <Route path="/account/notifications" element={<Navigate to="/account/settings?tab=notifications" replace />} />
                    <Route path="/account/security" element={<Navigate to="/account/settings?tab=security" replace />} />
                    <Route path="/admin/shares" element={<AdminRoute><AdminSharesPage /></AdminRoute>} />
                    <Route path="/admin/webhooks" element={<AdminRoute><AdminWebhooksPage /></AdminRoute>} />
                    <Route path="/admin/storage" element={<AdminRoute><AdminStoragePage /></AdminRoute>} />
                    <Route path="/admin/system" element={<AdminRoute><AdminSystemPage /></AdminRoute>} />
                    <Route path="/admin/audit" element={<AdminRoute><AdminAuditPage /></AdminRoute>} />
                    <Route path="/admin/events" element={<AdminRoute><AdminEventsPage /></AdminRoute>} />
                    <Route path="/admin/superset-graph-builder" element={
                      <AdminRoute>
                        <SupersetAdminPage />
                      </AdminRoute>
                    } />
                    <Route path="/admin/dashboard-links" element={
                      <AdminRoute>
                        <AdminDashboardLinksPage />
                      </AdminRoute>
                    } />
                    <Route path="/storage" element={<ItemsPage />} />
                    <Route path="/items" element={<Navigate to="/storage" replace />} />
                    <Route path="/search" element={<SearchPage />} />
                    <Route path="/events" element={<Navigate to="/admin/events" replace />} />
                  </Routes>
                </AppLayout></AccessBoundary>
              </ProtectedRoute>
            } />
          </Routes>
          </PageDraftProvider>
        </BrowserRouter>
      </ThemeProvider>
    </QueryClientProvider>
  )
}

export default App
