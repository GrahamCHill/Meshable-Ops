import { type ReactNode } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { LayoutDashboard, BarChart2, Package, Search, Activity, LogOut, Zap, Shield } from 'lucide-react'
import { useAuthStore } from '../../hooks/useAuthStore'

const nav = [
  { to: '/', label: 'Home', icon: LayoutDashboard },
  { to: '/dashboards', label: 'Dashboards', icon: BarChart2 },
  { to: '/items', label: 'Items', icon: Package },
  { to: '/search', label: 'Vector Search', icon: Search },
  { to: '/events', label: 'Events', icon: Activity },
]

const adminNav = [
  { to: '/admin/superset', label: 'Superset Admin', icon: Shield },
]

export function AppLayout({ children }: { children: ReactNode }) {
  const { user, logout } = useAuthStore()
  const navigate = useNavigate()
  const handleLogout = () => { logout(); navigate('/login') }
  const navItems = user?.role === 'ADMIN' ? [...nav, ...adminNav] : nav

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
        <nav style={{ flex: 1, padding: '0 0.75rem', display: 'flex', flexDirection: 'column', gap: 2 }}>
          {navItems.map(({ to, label, icon: Icon }) => (
            <NavLink key={to} to={to} end={to === '/'} style={({ isActive }) => ({
              display: 'flex', alignItems: 'center', gap: 10,
              padding: '0.6rem 0.75rem', borderRadius: 'var(--radius-sm)',
              fontSize: '0.875rem', fontWeight: isActive ? 500 : 400,
              color: isActive ? 'var(--color-text)' : 'var(--color-text-secondary)',
              background: isActive ? 'var(--color-surface-2)' : 'transparent',
              textDecoration: 'none', transition: 'all 150ms ease',
            })}>
              <Icon size={16} />{label}
            </NavLink>
          ))}
        </nav>
        <div style={{ padding: '1rem 1.25rem 0', borderTop: '1px solid var(--color-border)' }}>
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
