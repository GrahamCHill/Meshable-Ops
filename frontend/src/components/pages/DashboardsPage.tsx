import { useState } from 'react'
import { Link } from 'react-router-dom'
import { DashboardEmbed } from '../ui/DashboardEmbed'
import { Info, LayoutDashboard, Shield } from 'lucide-react'
import { useAuthStore } from '../../hooks/useAuthStore'

interface DashboardConfig {
  id: string
  label: string
  description: string
  guestTokenResourceId?: string
}

/**
 * Add your Superset dashboard IDs here.
 *
 * `id` is the embedded dashboard UUID used by the iframe SDK.
 * `guestTokenResourceId` is optional. Use it only when the numeric Superset
 * dashboard id differs from the embed UUID and your backend needs that numeric id
 * to mint the guest token.
 */
const DASHBOARDS: DashboardConfig[] = [
  {
    id: 'fdda8aae-c6fb-48fc-9322-5f797764b9dd',
    label: 'Metrics Overview',
    description: 'Items created over time',
  },
  {
    id: 'fdda8aae-c6fb-48fc-9322-5f797764b9dd',
    label: 'Metrics Overview 22',
    description: 'Items created over time a second time',
  },
  // Add more dashboards:
  // { id: 'another-uuid', guestTokenResourceId: '2', label: 'Sales', description: 'Sales dashboard' },
]

export function DashboardsPage() {
  const [active, setActive] = useState(0)
  const { user } = useAuthStore()

  const current = DASHBOARDS[active]
  const isPlaceholder = current.id === 'YOUR-DASHBOARD-UUID-HERE'

  return (
    <div style={{ width: '100%', maxWidth: 'none' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '1rem', marginBottom: '1.25rem', flexWrap: 'wrap' }}>
        <div>
          <h1 style={{ fontSize: '1.4rem', fontWeight: 700, marginBottom: 4 }}>Dashboards</h1>
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem' }}>
            Embedded Apache Superset dashboards for authenticated app users
          </p>
        </div>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: 6,
          fontSize: '0.8rem',
          color: 'var(--color-text-secondary)',
          padding: '6px 12px',
          border: '1px solid var(--color-border)',
          borderRadius: 'var(--radius-sm)',
          background: 'var(--color-surface)',
        }}>
          <Shield size={13} /> Superset stays internal to Docker
        </div>
      </div>

      {user?.role === 'ADMIN' && (
        <div style={{
          marginBottom: '1rem',
          padding: '0.9rem 1rem',
          border: '1px solid var(--color-border)',
          borderRadius: 'var(--radius-md)',
          background: 'var(--color-surface)',
          color: 'var(--color-text-secondary)',
          fontSize: '0.85rem',
        }}>
          Need to create or edit dashboards? Open the admin workspace at{' '}
          <Link to="/admin/superset" style={{ color: 'var(--color-accent)', textDecoration: 'none', fontWeight: 600 }}>
            /admin/superset
          </Link>.
        </div>
      )}

      {DASHBOARDS.length > 1 && (
        <div style={{
          display: 'flex', gap: 4, marginBottom: '1rem',
          borderBottom: '1px solid var(--color-border)', paddingBottom: 0,
        }}>
          {DASHBOARDS.map((dashboard, index) => (
            <button key={dashboard.id} onClick={() => setActive(index)} style={{
              padding: '8px 16px',
              fontSize: '0.875rem',
              fontWeight: active === index ? 600 : 400,
              color: active === index ? 'var(--color-text)' : 'var(--color-text-secondary)',
              background: 'none',
              border: 'none',
              borderBottom: active === index ? '2px solid var(--color-accent)' : '2px solid transparent',
              cursor: 'pointer',
              transition: 'all 150ms ease',
              marginBottom: -1,
            }}>
              {dashboard.label}
            </button>
          ))}
        </div>
      )}

      {isPlaceholder ? (
        <div style={{
          background: 'var(--color-surface)',
          border: '1px solid var(--color-border)',
          borderRadius: 'var(--radius-lg)',
          padding: '3rem 2rem',
          textAlign: 'center',
        }}>
          <div style={{
            width: 48, height: 48, borderRadius: 12,
            background: 'var(--color-accent-muted)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            margin: '0 auto 1rem',
          }}>
            <LayoutDashboard size={22} color="var(--color-accent)" />
          </div>
          <h2 style={{ fontSize: '1.1rem', fontWeight: 600, marginBottom: 8 }}>No dashboard UUID configured</h2>
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem', maxWidth: 440, margin: '0 auto 1.5rem' }}>
            Keep Superset internal, enable dashboard embedding there, then paste the embed identifiers into this page.
          </p>
          <div style={{
            background: 'var(--color-bg)',
            border: '1px solid var(--color-border)',
            borderRadius: 'var(--radius-md)',
            padding: '1rem 1.5rem',
            textAlign: 'left',
            maxWidth: 560,
            margin: '0 auto',
            fontSize: '0.8rem',
            color: 'var(--color-text-secondary)',
            lineHeight: 1.8,
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 8, color: 'var(--color-text)', fontWeight: 500 }}>
              <Info size={14} /> Steps to embed a dashboard
            </div>
            1. Admins can manage dashboards from the internal Superset admin page.<br />
            2. Enable embedding for the target dashboard and copy the embed UUID.<br />
            3. Find the numeric dashboard id for the same dashboard.<br />
            4. Paste both values into <code style={{ background: 'var(--color-surface-2)', padding: '1px 4px', borderRadius: 3 }}>DashboardsPage.tsx</code>.<br />
            5. Any authenticated user can view embeds on pages that include them.
          </div>
        </div>
      ) : (
        <DashboardEmbed
          key={current.id}
          dashboardId={current.id}
          guestTokenResourceId={current.guestTokenResourceId}
          height="calc(100vh - 12rem)"
        />
      )}
    </div>
  )
}
