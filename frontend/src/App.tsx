import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { lazy, Suspense } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AppLayout } from './components/layout/AppLayout'
import { BrandingHead } from './components/layout/Branding'
import { AdminBrandingPage } from './components/pages/AdminBrandingPage'
import { AdminPluginsPage } from './components/pages/AdminPluginsPage'
import { AdminPage } from './components/pages/AdminPage'
import { DashboardPage } from './components/pages/DashboardPage'
import { DashboardsPage } from './components/pages/DashboardsPage'
import { SupersetAdminPage } from './components/pages/SupersetAdminPage'
import { AdminDashboardLinksPage } from './components/pages/AdminDashboardLinksPage'
import { AdminUsersPage } from './components/pages/AdminUsersPage'
import { AdminAiPage } from './components/pages/AdminAiPage'
import { AdminDocumentAccessPage } from './components/pages/AdminDocumentAccessPage'
import { AdminEmailPage } from './components/pages/AdminEmailPage'
import { AccountSecurityPage } from './components/pages/AccountSecurityPage'
import { AdminSharesPage } from './components/pages/AdminSharesPage'
import { AdminWebhooksPage } from './components/pages/AdminWebhooksPage'
import { SharedFilePage } from './components/pages/SharedFilePage'
import { AdminStoragePage } from './components/pages/AdminStoragePage'
import { AdminSystemPage } from './components/pages/AdminSystemPage'
import { AdminAuditPage } from './components/pages/AdminAuditPage'
import { ItemsPage } from './components/pages/ItemsPage'
import { SearchPage } from './components/pages/SearchPage'
import { EventsPage } from './components/pages/EventsPage'
import { LoginPage, RegisterPage } from './components/pages/AuthPages'
import { useAuthStore } from './hooks/useAuthStore'
import { ThemeProvider } from './hooks/useTheme'
import './styles/global.css'

const AdminTemplatesPage = lazy(() => import('./components/pages/AdminTemplatesPage'))
const DocumentsPage = lazy(() => import('./components/pages/DocumentsPage'))
const WorkflowsPage = lazy(() => import('./components/pages/WorkflowsPage'))
const PluginPage = lazy(() => import('./plugins/PluginPage'))
const loading = (children: React.ReactNode) => <Suspense fallback={<p role="status">Loading workspace…</p>}>{children}</Suspense>

const qc = new QueryClient({
  defaultOptions: { queries: { retry: 1, staleTime: 30_000 } },
})

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuthStore()
  return isAuthenticated() ? <>{children}</> : <Navigate to="/login" replace />
}

function AdminRoute({ children }: { children: React.ReactNode }) {
  const { user } = useAuthStore()
  return user?.role === 'ADMIN' ? <>{children}</> : <Navigate to="/" replace />
}

function App() {
  return (
    <ThemeProvider>
      <QueryClientProvider client={qc}>
        <BrandingHead />
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/shared/:token" element={<SharedFilePage />} />
            <Route path="/*" element={
              <ProtectedRoute>
                <AppLayout>
                  <Routes>
                    <Route path="/" element={<DashboardPage />} />
                    <Route path="/dashboards" element={<DashboardsPage />} />
                    <Route path="/admin" element={
                      <AdminRoute>
                        <AdminPage />
                      </AdminRoute>
                    } />
                    <Route path="/admin/users" element={<AdminRoute><AdminUsersPage /></AdminRoute>} />
                    <Route path="/admin/branding" element={<AdminRoute><AdminBrandingPage /></AdminRoute>} />
                    <Route path="/admin/plugins" element={<AdminRoute><AdminPluginsPage /></AdminRoute>} />
                    <Route path="/plugins/:pluginId/*" element={loading(<PluginPage />)} />
                    <Route path="/admin/ai" element={<AdminRoute><AdminAiPage /></AdminRoute>} />
                    <Route path="/admin/documents" element={<AdminRoute><AdminDocumentAccessPage /></AdminRoute>} />
                    <Route path="/admin/email" element={<AdminRoute><AdminEmailPage /></AdminRoute>} />
                    <Route path="/account/security" element={<AccountSecurityPage />} />
                    <Route path="/admin/shares" element={<AdminRoute><AdminSharesPage /></AdminRoute>} />
                    <Route path="/admin/webhooks" element={<AdminRoute><AdminWebhooksPage /></AdminRoute>} />
                    <Route path="/admin/document-templates" element={<AdminRoute>{loading(<AdminTemplatesPage />)}</AdminRoute>} />
                    <Route path="/documents" element={loading(<DocumentsPage />)} />
                    <Route path="/workflows" element={loading(<WorkflowsPage />)} />
                    <Route path="/admin/storage" element={<AdminRoute><AdminStoragePage /></AdminRoute>} />
                    <Route path="/admin/system" element={<AdminRoute><AdminSystemPage /></AdminRoute>} />
                    <Route path="/admin/audit" element={<AdminRoute><AdminAuditPage /></AdminRoute>} />
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
                    <Route path="/events" element={<EventsPage />} />
                  </Routes>
                </AppLayout>
              </ProtectedRoute>
            } />
          </Routes>
        </BrowserRouter>
      </QueryClientProvider>
    </ThemeProvider>
  )
}

export default App
