import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { adminUsersApi, type AdminUser } from '../../api/client'
import { Badge, Button, Card, Spinner } from '../ui'
import { ArrowRight, KeyRound, Shield, Users, Workflow } from 'lucide-react'

type CreateUserForm = {
  email: string
  username: string
  password: string
  role: 'ADMIN' | 'USER'
}

const emptyForm: CreateUserForm = {
  email: '',
  username: '',
  password: '',
  role: 'USER',
}

function getErrorMessage(error: unknown) {
  const fallback = 'Unable to save the user right now.'
  if (!error || typeof error !== 'object') return fallback

  const maybeResponse = (error as {
    response?: { data?: { message?: string } | Record<string, string> }
  }).response

  if (!maybeResponse?.data) return fallback
  if ('message' in maybeResponse.data && typeof maybeResponse.data.message === 'string') {
    return maybeResponse.data.message
  }

  const firstFieldError = Object.values(maybeResponse.data)[0]
  return typeof firstFieldError === 'string' ? firstFieldError : fallback
}

function formatDate(value: string) {
  return new Date(value).toLocaleString()
}

function UserRow({ user }: { user: AdminUser }) {
  return (
    <div style={{
      display: 'flex',
      gap: 12,
      alignItems: 'center',
      justifyContent: 'space-between',
      flexWrap: 'wrap',
      padding: '0.9rem 1rem',
      border: '1px solid var(--color-border)',
      borderRadius: 'var(--radius-md)',
      background: 'var(--color-bg)',
    }}>
      <div style={{ minWidth: 0 }}>
        <div style={{ fontWeight: 600 }}>{user.email}</div>
        <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.8rem' }}>{user.username}</div>
      </div>
      <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.82rem' }}>
        Created {formatDate(user.createdAt)}
      </div>
      <Badge color={user.role === 'ADMIN' ? 'accent' : 'default'}>{user.role}</Badge>
      <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.82rem' }}>
        Updated {formatDate(user.updatedAt)}
      </div>
    </div>
  )
}

export function AdminPage() {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<CreateUserForm>(emptyForm)
  const [error, setError] = useState('')

  const usersQuery = useQuery({
    queryKey: ['admin-users'],
    queryFn: () => adminUsersApi.list(),
  })

  const createUserMutation = useMutation({
    mutationFn: adminUsersApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      setForm(emptyForm)
      setError('')
    },
    onError: (mutationError) => {
      setError(getErrorMessage(mutationError))
    },
  })

  return (
    <div style={{ width: '100%', maxWidth: 'none', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '1rem', flexWrap: 'wrap' }}>
        <div>
          <h1 style={{ fontSize: '1.4rem', fontWeight: 700, marginBottom: 4 }}>Admin Workspace</h1>
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem' }}>
            Manage application access here, then open Superset Graph Builder when you need to author datasets, charts, and dashboards.
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
          <Shield size={13} /> Production bootstrap now comes from `.env`
        </div>
      </div>

      <Card style={{ padding: '1rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{
              width: 38,
              height: 38,
              borderRadius: 12,
              background: 'var(--color-accent-muted)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}>
              <Workflow size={18} color="var(--color-accent)" />
            </div>
            <div>
              <div style={{ fontWeight: 600 }}>Superset Graph Builder</div>
              <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.82rem' }}>
                Open the embedded Superset workspace in its own admin subpage.
              </div>
            </div>
          </div>
          <Link
            to="/admin/superset-graph-builder"
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 8,
              padding: '0.75rem 1rem',
              borderRadius: 'var(--radius-md)',
              background: 'var(--color-accent)',
              color: 'white',
              textDecoration: 'none',
              fontWeight: 600,
            }}
          >
            Open Graph Builder <ArrowRight size={15} />
          </Link>
        </div>
      </Card>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '1rem', alignItems: 'start' }}>
        <Card>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: '0.9rem' }}>
            <div style={{
              width: 34,
              height: 34,
              borderRadius: 10,
              background: 'var(--color-accent-muted)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}>
              <KeyRound size={16} color="var(--color-accent)" />
            </div>
            <div>
              <div style={{ fontWeight: 600 }}>Create a user</div>
              <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.82rem' }}>
                Invite-only access stays under admin control.
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            <div>
              <label style={{ fontSize: '0.8rem', fontWeight: 500, display: 'block', marginBottom: 4 }}>Email</label>
              <input
                type="email"
                placeholder="person@example.com"
                value={form.email}
                onChange={(e) => {
                  setForm(current => ({ ...current, email: e.target.value }))
                  setError('')
                }}
              />
            </div>
            <div>
              <label style={{ fontSize: '0.8rem', fontWeight: 500, display: 'block', marginBottom: 4 }}>Username</label>
              <input
                placeholder="person"
                value={form.username}
                onChange={(e) => {
                  setForm(current => ({ ...current, username: e.target.value }))
                  setError('')
                }}
              />
            </div>
            <div>
              <label style={{ fontSize: '0.8rem', fontWeight: 500, display: 'block', marginBottom: 4 }}>Password</label>
              <input
                type="password"
                placeholder="At least 8 characters"
                value={form.password}
                onChange={(e) => {
                  setForm(current => ({ ...current, password: e.target.value }))
                  setError('')
                }}
              />
            </div>
            <div>
              <label style={{ fontSize: '0.8rem', fontWeight: 500, display: 'block', marginBottom: 4 }}>Role</label>
              <select
                value={form.role}
                onChange={(e) => setForm(current => ({ ...current, role: e.target.value as 'ADMIN' | 'USER' }))}
              >
                <option value="USER">USER</option>
                <option value="ADMIN">ADMIN</option>
              </select>
            </div>
            {error && <div style={{ color: 'var(--color-danger)', fontSize: '0.82rem' }}>{error}</div>}
            <Button
              loading={createUserMutation.isPending}
              onClick={() => createUserMutation.mutate({
                email: form.email.trim(),
                username: form.username.trim(),
                password: form.password,
                role: form.role,
              })}
            >
              Create user
            </Button>
          </div>
        </Card>

        <Card>
          <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'center', marginBottom: '0.9rem', flexWrap: 'wrap' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Users size={16} />
              <div>
                <div style={{ fontWeight: 600 }}>Access roster</div>
                <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.82rem' }}>
                  Current application accounts and roles.
                </div>
              </div>
            </div>
            {usersQuery.data && <Badge color="accent">{usersQuery.data.length} users</Badge>}
          </div>

          {usersQuery.isLoading ? (
            <div style={{ display: 'flex', justifyContent: 'center', padding: '2rem' }}>
              <Spinner />
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              {(usersQuery.data ?? []).map((user) => (
                <UserRow key={user.id} user={user} />
              ))}
              {usersQuery.data?.length === 0 && (
                <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem' }}>
                  No users have been created yet.
                </div>
              )}
            </div>
          )}
        </Card>
      </div>
    </div>
  )
}
