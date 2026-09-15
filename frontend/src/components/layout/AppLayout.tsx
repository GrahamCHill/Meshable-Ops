import { useEffect, type ReactNode } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { LayoutDashboard, BarChart2, HardDrive, Search, Activity, LogOut, Zap, Shield, Moon, Sun, Link2, Workflow, Users, HeartPulse, ScrollText } from 'lucide-react'
import { useAuthStore } from '../../hooks/useAuthStore'
import { useTheme } from '../../hooks/useTheme'
import { authApi } from '../../api/client'

const nav = [
  { to: '/', label: 'Home', icon: LayoutDashboard },
  { to: '/dashboards', label: 'Dashboards', icon: BarChart2 },
  { to: '/storage', label: 'Storage', icon: HardDrive },
  { to: '/search', label: 'Vector Search', icon: Search },
  { to: '/events', label: 'Events', icon: Activity },
]

const adminNav = [
  { to: '/admin', label: 'Admin Overview', icon: Shield },
  { to: '/admin/users', label: 'Users', icon: Users },
  { to: '/admin/storage', label: 'Storage & Vectors', icon: HardDrive },
  { to: '/admin/system', label: 'System', icon: HeartPulse },
  { to: '/admin/audit', label: 'Audit & Backup', icon: ScrollText },
  { to: '/admin/dashboard-links', label: 'Dashboard Links', icon: Link2 },
  { to: '/admin/superset-graph-builder', label: 'Superset Builder', icon: Workflow },
]

export function AppLayout({ children }: { children: ReactNode }) {
  const { token, user, refreshUser, logout } = useAuthStore()
  const { mode, toggleMode } = useTheme()
  const navigate = useNavigate()

  useEffect(() => {
    if (!token) return
    authApi.refreshSessionCookie()
      .then(session => refreshUser(session))
      .catch(() => {
        logout()
        navigate('/login', { replace: true })
      })
  }, [logout, navigate, refreshUser, token])

  const handleLogout = async () => {
    try {
      await authApi.logout()
    } catch {
      // Clear local auth state even if the server-side cookie is already gone.
    }
    logout()
    navigate('/login')
  }

  const renderNavItem = ({ to, label, icon: Icon }: typeof nav[number]) => (
    <NavLink key={to} to={to} end={to === '/' || to === '/admin'} style={({ isActive }) => ({
      display: 'flex', alignItems: 'center', gap: 10,
      padding: '0.6rem 0.75rem', borderRadius: 'var(--radius-sm)',
      fontSize: '0.875rem', fontWeight: isActive ? 500 : 400,
      color: isActive ? 'var(--color-text)' : 'var(--color-text-secondary)',
      background: isActive ? 'var(--color-surface-2)' : 'transparent',
      textDecoration: 'none', transition: 'all 150ms ease',
    })}>
      <Icon size={16} />{label}
    </NavLink>
  )

  return (
    <div style={{ display: 'flex', minHeight: '100vh' }}>
      <aside style={{
        width: 220, background: 'var(--color-surface)',
        borderRight: '1px solid var(--color-border)',
        display: 'flex', flexDirection: 'column', padding: '1.5rem 0', flexShrink: 0,
      }}>
        <div style={{ padding: '0 1.25rem 1.5rem', display: 'flex', alignItems: 'center', gap: 8 }}>
          <div style={{ width: 30, height: 30, borderRadius: 8, background: 'var(--color-accent)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Zap size={16} color="white" />
          </div>
          <span style={{ fontWeight: 700, fontSize: '0.95rem', letterSpacing: '-0.02em' }}>Starter</span>
        </div>
        <nav style={{ flex: 1, minHeight: 0, overflowY: 'auto', padding: '0 0.75rem', display: 'flex', flexDirection: 'column', gap: 2 }}>
          {nav.map(renderNavItem)}
          {user?.role === 'ADMIN' && (
            <>
              <div style={{ padding: '1rem 0.75rem 0.35rem', color: 'var(--color-text-tertiary)', fontSize: '0.68rem', fontWeight: 700, letterSpacing: '0.08em', textTransform: 'uppercase' }}>Administration</div>
              {adminNav.map(renderNavItem)}
            </>
          )}
        </nav>
        <div style={{ padding: '1rem 1.25rem 0', borderTop: '1px solid var(--color-border)' }}>
          <button
            onClick={toggleMode}
            style={{
              width: '100%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 10,
              fontSize: '0.85rem',
              color: 'var(--color-text)',
              background: 'var(--color-surface-2)',
              border: '1px solid var(--color-border)',
              borderRadius: 'var(--radius-sm)',
              padding: '0.65rem 0.75rem',
              marginBottom: 12,
            }}
          >
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
              {mode === 'dark' ? <Moon size={14} /> : <Sun size={14} />}
              {mode === 'dark' ? 'Dark mode' : 'Light mode'}
            </span>
            <span style={{ fontSize: '0.72rem', color: 'var(--color-text-secondary)' }}>
              Switch
            </span>
          </button>
          <div style={{ fontSize: '0.8rem', color: 'var(--color-text-secondary)', marginBottom: 4 }}>{user?.email}</div>
          <button onClick={handleLogout} style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: '0.8rem', color: 'var(--color-text-tertiary)', background: 'none', border: 'none', cursor: 'pointer', padding: 0, transition: 'color 150ms ease' }}
            onMouseOver={e => (e.currentTarget.style.color = 'var(--color-danger)')}
            onMouseOut={e => (e.currentTarget.style.color = 'var(--color-text-tertiary)')}>
            <LogOut size={13} /> Sign out
          </button>
        </div>
      </aside>
      <main style={{ flex: 1, overflow: 'auto', padding: '2rem' }}>{children}</main>
    </div>
  )
}
