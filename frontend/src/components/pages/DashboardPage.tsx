import { Link } from 'react-router-dom'

import { useAuthStore } from '../../hooks/useAuthStore'
import { Card } from '../ui'

const destinations = [
  { to: '/storage', label: 'Browse storage', description: 'Explore the folders and documents available to the application.' },
  { to: '/search', label: 'Vector search', description: 'Search indexed folder and document metadata.' },
  { to: '/dashboards', label: 'Dashboards', description: 'Open the reporting dashboards available to your role.' },
]

export function DashboardPage() {
  const { user } = useAuthStore()

  return (
    <div style={{ width: '100%', display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
      <div>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: 4 }}>Welcome, {user?.name || user?.username}</h1>
        <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.9rem' }}>
          Choose a workspace below. This landing area is ready for the upcoming Home experience.
        </p>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: '1rem' }}>
        {destinations.map(({ to, label, description }) => (
          <Link key={to} to={to} style={{ textDecoration: 'none', color: 'inherit' }}>
            <Card style={{ height: '100%', display: 'flex', gap: 12, alignItems: 'flex-start' }}>

              <div><div style={{ fontWeight: 600, marginBottom: 5 }}>{label}</div><div style={{ color: 'var(--color-text-secondary)', fontSize: '0.82rem', lineHeight: 1.5 }}>{description}</div></div>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  )
}
