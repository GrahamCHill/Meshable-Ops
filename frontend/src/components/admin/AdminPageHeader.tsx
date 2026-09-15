import type { LucideIcon } from 'lucide-react'

export function AdminPageHeader({
  title,
  description,
  icon: Icon,
  actions,
}: {
  title: string
  description: string
  icon: LucideIcon
  actions?: React.ReactNode
}) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '1rem', flexWrap: 'wrap' }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
        <div style={{ width: 40, height: 40, borderRadius: 12, background: 'var(--color-accent-muted)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
          <Icon size={19} color="var(--color-accent)" />
        </div>
        <div>
          <h1 style={{ fontSize: '1.4rem', fontWeight: 700, marginBottom: 4 }}>{title}</h1>
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem', maxWidth: 720 }}>{description}</p>
        </div>
      </div>
      {actions}
    </div>
  )
}
