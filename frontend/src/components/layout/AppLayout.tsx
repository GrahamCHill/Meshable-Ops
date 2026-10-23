import { useEffect, useState, type ReactNode } from 'react'
import { NavLink, useLocation, useNavigate } from 'react-router-dom'

import { useAuthStore } from '../../hooks/useAuthStore'
import { useTheme } from '../../hooks/useTheme'
import { authApi } from '../../api/auth'
import { SearchLauncher } from '../search/SearchLauncher'

const nav = [
  { to: '/', label: 'Home' },
  { to: '/dashboards', label: 'Dashboards' },
  { to: '/storage', label: 'Storage' },
  { to: '/events', label: 'Events' },
]

export function AppLayout({ children }: { children: ReactNode }) {
  const { token, user, refreshUser, logout } = useAuthStore()
  const { mode, toggleMode } = useTheme()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const [mobileOpen, setMobileOpen] = useState(false)

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
    authApi.refreshSessionCookie().then(refreshUser).catch(() => {
      logout()
      navigate('/login', { replace: true })
    })
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
        <span className="brand">Starter</span>
        <button className="utility-button" aria-label={mobileOpen ? 'Close navigation' : 'Open navigation'} aria-expanded={mobileOpen} aria-controls="app-navigation" onClick={() => setMobileOpen(!mobileOpen)}>{mobileOpen ? 'Close' : 'Menu'}</button>
      </div>
      {mobileOpen && <button className="sidebar-scrim" aria-label="Close navigation" onClick={() => setMobileOpen(false)} />}
      <aside id="app-navigation" className={`app-sidebar${mobileOpen ? ' is-open' : ''}`}>
        <div className="brand sidebar-brand">Starter</div>
        <nav className="sidebar-nav" aria-label="Main navigation">
          <div className="nav-caption">Workspace</div>
          {nav.map(renderNavItem)}
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
          <button className="sign-out" onClick={handleLogout}> Sign out</button>
        </div>
      </aside>
      <main className="app-main">{children}</main>
      <SearchLauncher />
    </div>
  )
}
