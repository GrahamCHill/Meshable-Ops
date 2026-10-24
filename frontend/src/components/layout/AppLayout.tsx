import { useEffect, useState, type ReactNode } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'

import { useAuthStore } from '../../hooks/useAuthStore'
import { useTheme } from '../../hooks/useTheme'
import { authApi } from '../../api/auth'
import { SearchLauncher } from '../search/SearchLauncher'
import { Brand } from './Branding'
import { useAvailablePlugins } from '../../api/plugins'
import { pageModule } from '../../plugins/registry'
import { useQuery } from '@tanstack/react-query'
import { aiApi } from '../../api/ai'

const nav = [
  { to: '/', label: 'Home' },
  { to: '/dashboards', label: 'Dashboards' },
  { to: '/storage', label: 'Storage' },
  { to: '/documents', label: 'Documents' },
  { to: '/workflows', label: 'Workflows' },
  { to: '/events', label: 'Events' },
]

export function AppLayout({ children }: { children: ReactNode }) {
  const { token, user, refreshUser, logout } = useAuthStore()
  const { mode, toggleMode } = useTheme()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const [mobileOpen, setMobileOpen] = useState(false)
  const plugins = useAvailablePlugins()
  const searchStatus = useQuery({queryKey: ['ai-search-status'], queryFn: aiApi.status, refetchInterval: 30_000})

  useEffect(() => {
    setMobileOpen(false)
  }, [pathname])

  useEffect(() => {
    if (!mobileOpen) return
    const close = (event: KeyboardEvent) => { if (event.key === 'Escape') setMobileOpen(false) }
    window.addEventListener('keydown', close)
    return () => window.removeEventListener('keydown', close)
  }, [mobileOpen])

  useEffect(() => {
    if (!token) return
    const controller = new AbortController()
    authApi.refreshSessionCookie(controller.signal).then(data => {
      if (!controller.signal.aborted && useAuthStore.getState().token === token) refreshUser(data)
    }).catch(() => {
      if (!controller.signal.aborted && useAuthStore.getState().token === token) { logout(); navigate('/login', { replace: true }) }
    })
    return () => controller.abort()
  }, [logout, navigate, refreshUser, token])

  const handleLogout = async () => {
    try { await authApi.logout() } catch { /* Clear local state if the cookie has expired. */ }
    logout()
    navigate('/login')
  }

  const renderNavItem = ({ to, label }: typeof nav[number]) => (
    <NavLink key={to} to={to} end={to === '/'}
      className={({ isActive }) => `nav-item${isActive ? ' is-active' : ''}`}>
      <span>{label}</span>
    </NavLink>
  )

  return (
    <div className="app-shell">
      <div className="mobile-bar">
        <Brand />
        <button className="utility-button" aria-label={mobileOpen ? 'Close navigation' : 'Open navigation'} aria-expanded={mobileOpen} aria-controls="app-navigation" onClick={() => setMobileOpen(!mobileOpen)}>{mobileOpen ? 'Close' : 'Menu'}</button>
      </div>
      {mobileOpen && <button className="sidebar-scrim" aria-label="Close navigation" onClick={() => setMobileOpen(false)} />}
      <aside id="app-navigation" className={`app-sidebar${mobileOpen ? ' is-open' : ''}`}>
        <Brand className="sidebar-brand" />
        <nav className="sidebar-nav" aria-label="Main navigation">
          <div className="nav-caption">Workspace</div>
          {nav.map(renderNavItem)}
          {searchStatus.data?.searchEnabled && renderNavItem({to: '/search', label: 'Search workspace'})}
          {plugins.data?.filter(plugin => pageModule(plugin)).map(plugin => <div key={plugin.manifest.id}>
            <NavLink to={`/plugins/${plugin.manifest.id}`} end className={({isActive}) => `nav-item${isActive ? ' is-active' : ''}`}>{plugin.manifest.navigationLabel}</NavLink>
            <div className="plugin-submenu" aria-label={`${plugin.manifest.navigationLabel} sections`}>
              {plugin.manifest.menuItems.filter(item => user && item.roles.includes(user.role)).map(item => renderNavItem({to: `/plugins/${plugin.manifest.id}/${item.path}`, label: item.label}))}
            </div>
          </div>)}
          {user?.role === 'ADMIN' && (
            <div className="admin-navigation">
              {renderNavItem({ to: '/admin', label: 'Administration' })}
            </div>
          )}
        </nav>
        <div className="sidebar-footer">
          <button className="theme-toggle" onClick={toggleMode} aria-label={`Switch to ${mode === 'dark' ? 'light' : 'dark'} mode`}>
            {mode === 'dark' ? 'Dark mode' : 'Light mode'}
          </button>
          <div className="user-email" title={user?.email}>{user?.email}</div>
          <NavLink to="/account/security" className="sign-out">Security & MFA</NavLink>
          <button className="sign-out" onClick={handleLogout}> Sign out</button>
        </div>
      </aside>
      <main className="app-main">{children}</main>
      <SearchLauncher />
    </div>
  )
}
