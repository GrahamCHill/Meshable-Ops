import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { authApi } from '../../api/client'
import { useAuthStore } from '../../hooks/useAuthStore'
import { Button } from '../ui'
import { Zap } from 'lucide-react'

function getAuthErrorMessage(error: unknown) {
  const fallback = 'Invalid email or password'
  if (!error || typeof error !== 'object') return fallback

  const maybeResponse = (error as { response?: { data?: { message?: string } } }).response
  return typeof maybeResponse?.data?.message === 'string' ? maybeResponse.data.message : fallback
}

function AuthCard({ children, title, subtitle }: { children: React.ReactNode; title: string; subtitle: React.ReactNode }) {
  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '1rem' }}>
      <div style={{ width: '100%', maxWidth: 380 }}>
        <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
          <div style={{
            width: 44, height: 44, borderRadius: 12, background: 'var(--color-accent)',
            display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 1rem',
          }}>
            <Zap size={22} color="white" />
          </div>
          <h1 style={{ fontSize: '1.3rem', fontWeight: 700, marginBottom: 4 }}>{title}</h1>
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem' }}>{subtitle}</p>
        </div>
        <div style={{
          background: 'var(--color-surface)',
          border: '1px solid var(--color-border)',
          borderRadius: 'var(--radius-lg)',
          padding: '1.75rem',
        }}>
          {children}
        </div>
      </div>
    </div>
  )
}

export function LoginPage() {
  const navigate = useNavigate()
  const { login } = useAuthStore()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')

  const mutation = useMutation({
    mutationFn: authApi.login,
    onSuccess: data => { login(data.token, data); navigate('/') },
    onError: (loginError) => setError(getAuthErrorMessage(loginError)),
  })

  return (
    <AuthCard title="Sign in" subtitle="Welcome back to your starter app">
      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
        <div>
          <label style={{ fontSize: '0.8rem', fontWeight: 500, display: 'block', marginBottom: 4 }}>Email</label>
          <input type="email" placeholder="you@example.com" value={email} onChange={e => { setEmail(e.target.value); setError('') }} />
        </div>
        <div>
          <label style={{ fontSize: '0.8rem', fontWeight: 500, display: 'block', marginBottom: 4 }}>Password</label>
          <input type="password" placeholder="password" value={password} onChange={e => { setPassword(e.target.value); setError('') }}
            onKeyDown={e => e.key === 'Enter' && mutation.mutate({ email, password })} />
        </div>
        {error && <p style={{ color: 'var(--color-danger)', fontSize: '0.8rem' }}>{error}</p>}
        <Button loading={mutation.isPending} onClick={() => mutation.mutate({ email, password })} style={{ width: '100%', marginTop: 4 }}>
          Sign in
        </Button>
        <p style={{ textAlign: 'center', fontSize: '0.8rem', color: 'var(--color-text-secondary)' }}>
          Need access? Contact an administrator for an invite.
        </p>
        <p style={{ textAlign: 'center', fontSize: '0.75rem', color: 'var(--color-text-tertiary)' }}>
          Local dev seeds: admin@app.local / admin123 and user@app.local / user123
        </p>
      </div>
    </AuthCard>
  )
}

export function RegisterPage() {
  return (
    <AuthCard title="Invite only" subtitle="Accounts are created by invitation">
      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
        <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem', lineHeight: 1.6 }}>
          Self-service registration is disabled. Ask an administrator to create your account or send you an invite.
        </p>
        <Link
          to="/login"
          style={{
            width: '100%',
            marginTop: 4,
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '0.75rem 1rem',
            borderRadius: 'var(--radius-md)',
            background: 'var(--color-accent)',
            color: 'white',
            textDecoration: 'none',
            fontSize: '0.875rem',
            fontWeight: 500,
          }}
        >
          Back to sign in
        </Link>
      </div>
    </AuthCard>
  )
}
