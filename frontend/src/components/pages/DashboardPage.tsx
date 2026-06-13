import { useQuery } from '@tanstack/react-query'
import { healthApi } from '../../api/client'
import { Card, Spinner, StatusDot } from '../ui'
import { useAuthStore } from '../../hooks/useAuthStore'
import { Database, Server, Search } from 'lucide-react'

const infra = [
  { key: 'postgres', label: 'PostgreSQL', icon: Database, desc: 'Relational / JPA' },
  { key: 'mongodb', label: 'MongoDB', icon: Server, desc: 'Documents / Events' },
  { key: 'qdrant', label: 'Qdrant', icon: Search, desc: 'Vector store' },
] as const

export function DashboardPage() {
  const { user } = useAuthStore()
  const { data, isLoading } = useQuery({
    queryKey: ['health'],
    queryFn: healthApi.check,
    refetchInterval: 30_000,
  })

  return (
    <div style={{ maxWidth: 800 }}>
      <div style={{ marginBottom: '2rem' }}>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: 4 }}>
          Welcome back, {user?.name || user?.username}
        </h1>
        <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.9rem' }}>
          Infrastructure health and quick stats
        </p>
      </div>

      <h2 style={{ fontSize: '0.8rem', fontWeight: 600, letterSpacing: '0.08em', textTransform: 'uppercase', color: 'var(--color-text-tertiary)', marginBottom: '0.75rem' }}>
        Service health
      </h2>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '1rem', marginBottom: '2rem' }}>
        {infra.map(({ key, label, icon: Icon, desc }) => (
          <Card key={key}>
            <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: 12 }}>
              <div style={{
                width: 36, height: 36, borderRadius: 8,
                background: 'var(--color-accent-muted)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <Icon size={16} color="var(--color-accent)" />
              </div>
              {isLoading
                ? <Spinner size={14} />
                : <StatusDot status={data?.[key] ?? 'UNKNOWN'} />
              }
            </div>
            <div style={{ fontWeight: 600, fontSize: '0.9rem', marginBottom: 2 }}>{label}</div>
            <div style={{ fontSize: '0.8rem', color: 'var(--color-text-secondary)' }}>{desc}</div>
          </Card>
        ))}
      </div>

      <h2 style={{ fontSize: '0.8rem', fontWeight: 600, letterSpacing: '0.08em', textTransform: 'uppercase', color: 'var(--color-text-tertiary)', marginBottom: '0.75rem' }}>
        Stack summary
      </h2>
      <Card>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem', fontSize: '0.875rem' }}>
          {[
            ['Frontend', 'React 18 · Vite · TanStack Query · Zustand'],
            ['Backend', 'Spring Boot 3.3 · Java 21 · JWT'],
            ['Relational DB', 'PostgreSQL 16 · JPA / Hibernate'],
            ['Document DB', 'MongoDB 7 · Spring Data Mongo'],
            ['Vector DB', 'Qdrant · gRPC client · cosine similarity'],
            ['Auth', 'BCrypt · JJWT · stateless JWT'],
          ].map(([title, detail]) => (
            <div key={title}>
              <div style={{ fontWeight: 600, marginBottom: 2 }}>{title}</div>
              <div style={{ color: 'var(--color-text-secondary)' }}>{detail}</div>
            </div>
          ))}
        </div>
      </Card>
    </div>
  )
}
