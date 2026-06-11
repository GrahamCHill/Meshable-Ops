import { useState } from 'react'

const ADMIN_VIEWS = [
  { id: 'welcome', label: 'Welcome', src: '/superset/welcome/' },
  { id: 'databases', label: 'Databases', src: '/databaseview/list/' },
  { id: 'new-database', label: 'Add Database', src: '/databaseview/add' },
  { id: 'charts', label: 'Charts', src: '/chart/list/' },
  { id: 'new-chart', label: 'New Chart', src: '/chart/add' },
  { id: 'datasets', label: 'Datasets', src: '/tablemodelview/list/' },
]

export function SupersetAdminPage() {
  const [activeView, setActiveView] = useState(ADMIN_VIEWS[0])

  return (
    <div style={{ width: '100%', maxWidth: 'none' }}>
      <div style={{ marginBottom: '1.25rem' }}>
        <h1 style={{ fontSize: '1.4rem', fontWeight: 700, marginBottom: 4 }}>Superset Admin</h1>
        <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem' }}>
          Internal dashboard authoring workspace for dashboard makers and admins
        </p>
        <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.8rem', marginTop: 6 }}>
          Use Charts to build visuals, then save them into a new dashboard. The legacy Superset dashboard list route is unstable in this build.
        </p>
      </div>

      <div style={{
        display: 'flex',
        flexWrap: 'wrap',
        gap: '0.5rem',
        marginBottom: '0.9rem',
      }}>
        {ADMIN_VIEWS.map(view => {
          const active = view.id === activeView.id
          return (
            <button
              key={view.id}
              type="button"
              onClick={() => setActiveView(view)}
              style={{
                border: active ? '1px solid var(--color-accent)' : '1px solid var(--color-border)',
                background: active ? 'var(--color-accent-soft)' : 'var(--color-surface)',
                color: 'var(--color-text)',
                borderRadius: '999px',
                padding: '0.45rem 0.85rem',
                fontSize: '0.85rem',
                fontWeight: 600,
                cursor: 'pointer',
              }}
            >
              {view.label}
            </button>
          )
        })}
      </div>

      <div style={{
        background: 'var(--color-surface)',
        border: '1px solid var(--color-border)',
        borderRadius: 'var(--radius-lg)',
        padding: '0.75rem',
        height: 'calc(100vh - 12rem)',
        minHeight: 720,
      }}>
        <iframe
          key={activeView.src}
          src={activeView.src}
          title="Superset Admin"
          style={{
            width: '100%',
            height: '100%',
            border: 0,
            display: 'block',
            borderRadius: 'calc(var(--radius-lg) - 4px)',
            background: 'white',
          }}
        />
      </div>
    </div>
  )
}
