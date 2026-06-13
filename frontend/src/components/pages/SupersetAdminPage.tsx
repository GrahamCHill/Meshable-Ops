export function SupersetAdminPage() {
  return (
    <div style={{ width: '100%', maxWidth: 'none' }}>
      <div style={{ marginBottom: '1.25rem' }}>
        <h1 style={{ fontSize: '1.4rem', fontWeight: 700, marginBottom: 4 }}>Superset Admin</h1>
        <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem' }}>
          Internal dashboard authoring workspace for dashboard makers and admins
        </p>
        <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.8rem', marginTop: 6 }}>
          Use the Superset menu inside the frame for navigation. Direct top-level Superset pages stay blocked outside this app shell.
        </p>
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
          src="/superset/welcome/"
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
