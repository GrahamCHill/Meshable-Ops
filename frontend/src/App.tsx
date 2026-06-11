import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AppLayout } from './components/layout/AppLayout'
import { DashboardPage } from './components/pages/DashboardPage'
import { DashboardsPage } from './components/pages/DashboardsPage'
import { SupersetAdminPage } from './components/pages/SupersetAdminPage'
import { ItemsPage } from './components/pages/ItemsPage'
import { SearchPage } from './components/pages/SearchPage'
import { EventsPage } from './components/pages/EventsPage'
import { LoginPage, RegisterPage } from './components/pages/AuthPages'
import { useAuthStore } from './hooks/useAuthStore'
import './styles/global.css'

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
    <QueryClientProvider client={qc}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/*" element={
            <ProtectedRoute>
              <AppLayout>
                <Routes>
                  <Route path="/" element={<DashboardPage />} />
                  <Route path="/dashboards" element={<DashboardsPage />} />
                  <Route path="/admin/superset" element={
                    <AdminRoute>
                      <SupersetAdminPage />
                    </AdminRoute>
                  } />
                  <Route path="/items" element={<ItemsPage />} />
                  <Route path="/search" element={<SearchPage />} />
                  <Route path="/events" element={<EventsPage />} />
                </Routes>
              </AppLayout>
            </ProtectedRoute>
          } />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  )
}

export default App
