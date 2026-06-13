import { Link } from 'react-router-dom'
import { ArrowLeft, Workflow } from 'lucide-react'
import { Card } from '../ui'

export function SupersetAdminPage() {
  return (
    <div style={{ width: '100%', maxWidth: 'none', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '1rem', flexWrap: 'wrap' }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
            <Workflow size={18} color="var(--color-accent)" />
            <h1 style={{ fontSize: '1.4rem', fontWeight: 700 }}>Superset Graph Builder</h1>
          </div>
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem' }}>
            Use the embedded Superset workspace for datasets, charts, and dashboard authoring. The wider admin tools stay on the main admin page.
          </p>
        </div>
        <Link
          to="/admin"
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 8,
            padding: '0.7rem 0.95rem',
            borderRadius: 'var(--radius-md)',
            background: 'var(--color-surface)',
            border: '1px solid var(--color-border)',
            color: 'var(--color-text)',
            textDecoration: 'none',
            fontWeight: 500,
          }}
        >
          <ArrowLeft size={15} /> Back to Admin
        </Link>
      </div>

      <Card style={{ padding: '0.75rem' }}>
        <div style={{ marginBottom: '0.9rem', padding: '0.25rem 0.25rem 0 0.25rem' }}>
          <div style={{ fontWeight: 600 }}>Embedded workspace</div>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.82rem' }}>
            Use the Superset menu inside the frame for navigation. Top-level routes remain protected by the app shell.
          </div>
        </div>
        <div style={{
          border: '1px solid var(--color-border)',
          borderRadius: 'var(--radius-lg)',
          overflow: 'hidden',
          height: 'calc(100vh - 12rem)',
          minHeight: 720,
          background: 'white',
        }}>
          <iframe
            src="/superset/welcome/"
            title="Superset Graph Builder"
            style={{ width: '100%', height: '100%', border: 0, display: 'block' }}
          />
        </div>
      </Card>
    </div>
  )
}
