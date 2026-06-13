import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  adminBackupApi,
  adminSettingsApi,
  adminUsersApi,
  auditLogsApi,
  healthApi,
  itemsApi,
  type AdminSettings,
  type AdminUser,
} from '../../api/client'
import { Badge, Button, Card, Spinner } from '../ui'
import {
  ArrowRight,
  Database,
  Download,
  HardDrive,
  HeartPulse,
  KeyRound,
  RefreshCcw,
  ScrollText,
  Shield,
  Trash2,
  Users,
  Workflow,
} from 'lucide-react'

type CreateUserForm = {
  email: string
  username: string
  password: string
  role: 'ADMIN' | 'USER'
}

type SettingsForm = {
  storageProvider: 'NONE' | 'S3' | 'AZURE_BLOB'
  storageBucket: string
  storageRegion: string
  storageEndpoint: string
  storageAccessKey: string
  storageSecretKey: string
  vectorReindexEnabled: boolean
  vectorReindexIntervalMinutes: number
}

const emptyForm: CreateUserForm = {
  email: '',
  username: '',
  password: '',
  role: 'USER',
}

const emptySettingsForm: SettingsForm = {
  storageProvider: 'NONE',
  storageBucket: '',
  storageRegion: '',
  storageEndpoint: '',
  storageAccessKey: '',
  storageSecretKey: '',
  vectorReindexEnabled: false,
  vectorReindexIntervalMinutes: 1440,
}

function getErrorMessage(error: unknown, fallback = 'Unable to save right now.') {
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

function formatDate(value?: string | null) {
  if (!value) return 'Never'
  return new Date(value).toLocaleString()
}

function toSettingsForm(settings: AdminSettings): SettingsForm {
  return {
    storageProvider: settings.storageProvider,
    storageBucket: settings.storageBucket ?? '',
    storageRegion: settings.storageRegion ?? '',
    storageEndpoint: settings.storageEndpoint ?? '',
    storageAccessKey: settings.storageAccessKey ?? '',
    storageSecretKey: settings.storageSecretKey ?? '',
    vectorReindexEnabled: settings.vectorReindexEnabled,
    vectorReindexIntervalMinutes: settings.vectorReindexIntervalMinutes,
  }
}

function extractFilename(contentDisposition?: string) {
  if (!contentDisposition) return 'app-backup.json'
  const match = contentDisposition.match(/filename="?(.*?)"?$/i)
  return match?.[1] ?? 'app-backup.json'
}

function UserRow({
  user,
  onDelete,
  deleting,
  pendingDeleteId,
  onRequestDelete,
  onCancelDelete,
}: {
  user: AdminUser
  onDelete: (id: string) => void
  deleting: boolean
  pendingDeleteId: string | null
  onRequestDelete: (id: string) => void
  onCancelDelete: () => void
}) {
  const confirmDelete = pendingDeleteId === user.id
  const isActive = user.active !== false

  return (
    <div style={{
      display: 'flex',
      gap: 16,
      alignItems: 'flex-start',
      justifyContent: 'space-between',
      flexWrap: 'wrap',
      padding: '0.9rem 1rem',
      border: '1px solid var(--color-border)',
      borderRadius: 'var(--radius-md)',
      background: 'var(--color-bg)',
    }}>
      <div style={{ minWidth: 0, flex: '1 1 260px' }}>
        <div style={{ fontWeight: 600 }}>{user.email}</div>
        <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.8rem' }}>{user.username}</div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 8 }}>
          <Badge color={user.role === 'ADMIN' ? 'accent' : 'default'}>{user.role}</Badge>
          <Badge color={isActive ? 'success' : 'danger'}>
            {isActive ? 'Active' : 'Inactive'}
          </Badge>
        </div>
        <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.82rem', marginTop: 8, lineHeight: 1.6 }}>
          Created {formatDate(user.createdAt)}<br />
          Updated {formatDate(user.updatedAt)}
        </div>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8, minWidth: 220, alignItems: 'flex-end' }}>
        <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.76rem', fontWeight: 600 }}>
          User actions
        </div>
        {confirmDelete ? (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
            <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.8rem', textAlign: 'right' }}>
              Delete will deactivate this user and block future logins.
            </div>
            <Button
              variant="danger"
              size="sm"
              loading={deleting}
              onClick={() => onDelete(user.id)}
            >
              Confirm delete
            </Button>
            <Button variant="ghost" size="sm" onClick={onCancelDelete} disabled={deleting}>
              Cancel
            </Button>
          </div>
        ) : (
          <Button
            variant="danger"
            size="sm"
            onClick={() => onRequestDelete(user.id)}
            disabled={!isActive}
            title={isActive ? 'Delete user' : 'User is already inactive'}
            style={{ minWidth: 120, justifyContent: 'center' }}
          >
            <Trash2 size={14} />
            {isActive ? 'Delete user' : 'Already deleted'}
          </Button>
        )}
      </div>
    </div>
  )
}

export function AdminPage() {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<CreateUserForm>(emptyForm)
  const [settingsForm, setSettingsForm] = useState<SettingsForm>(emptySettingsForm)
  const [createError, setCreateError] = useState('')
  const [rosterError, setRosterError] = useState('')
  const [settingsError, setSettingsError] = useState('')
  const [settingsSaved, setSettingsSaved] = useState('')
  const [backupError, setBackupError] = useState('')
  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null)

  const usersQuery = useQuery({
    queryKey: ['admin-users'],
    queryFn: () => adminUsersApi.list(),
  })

  const settingsQuery = useQuery({
    queryKey: ['admin-settings'],
    queryFn: () => adminSettingsApi.get(),
  })

  const auditLogsQuery = useQuery({
    queryKey: ['admin-audit-logs'],
    queryFn: () => auditLogsApi.list(0, 12),
  })

  const healthQuery = useQuery({
    queryKey: ['admin-health'],
    queryFn: () => healthApi.check(),
    refetchInterval: 30000,
  })

  useEffect(() => {
    if (settingsQuery.data) {
      setSettingsForm(toSettingsForm(settingsQuery.data))
    }
  }, [settingsQuery.data])

  const createUserMutation = useMutation({
    mutationFn: adminUsersApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      queryClient.invalidateQueries({ queryKey: ['admin-audit-logs'] })
      setForm(emptyForm)
      setCreateError('')
    },
    onError: (mutationError) => {
      setCreateError(getErrorMessage(mutationError, 'Unable to create the user right now.'))
    },
  })

  const deactivateUserMutation = useMutation({
    mutationFn: adminUsersApi.deactivate,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      queryClient.invalidateQueries({ queryKey: ['admin-audit-logs'] })
      setPendingDeleteId(null)
      setRosterError('')
    },
    onError: (mutationError) => {
      setRosterError(getErrorMessage(mutationError, 'Unable to deactivate the user right now.'))
    },
  })

  const settingsMutation = useMutation({
    mutationFn: adminSettingsApi.update,
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: ['admin-settings'] })
      queryClient.invalidateQueries({ queryKey: ['admin-audit-logs'] })
      setSettingsForm(toSettingsForm(saved))
      setSettingsError('')
      setSettingsSaved(`Saved ${formatDate(saved.updatedAt)}`)
    },
    onError: (mutationError) => {
      setSettingsSaved('')
      setSettingsError(getErrorMessage(mutationError, 'Unable to save admin settings right now.'))
    },
  })

  const reindexMutation = useMutation({
    mutationFn: () => itemsApi.reindex(),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ['admin-settings'] })
      queryClient.invalidateQueries({ queryKey: ['admin-audit-logs'] })
      setSettingsSaved(result.message)
      setSettingsError('')
    },
    onError: (mutationError) => {
      setSettingsSaved('')
      setSettingsError(getErrorMessage(mutationError, 'Unable to recalculate vectors right now.'))
    },
  })

  const backupMutation = useMutation({
    mutationFn: () => adminBackupApi.export(),
    onSuccess: ({ blob, filename }) => {
      const url = window.URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = extractFilename(filename)
      anchor.click()
      window.URL.revokeObjectURL(url)
      setBackupError('')
      queryClient.invalidateQueries({ queryKey: ['admin-audit-logs'] })
    },
    onError: (mutationError) => {
      setBackupError(getErrorMessage(mutationError, 'Unable to export the backup right now.'))
    },
  })

  return (
    <div style={{ width: '100%', maxWidth: 'none', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '1rem', flexWrap: 'wrap' }}>
        <div>
          <h1 style={{ fontSize: '1.4rem', fontWeight: 700, marginBottom: 4 }}>Admin Workspace</h1>
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem' }}>
            Manage users, system settings, vector-search operations, backup export, and the embedded Superset authoring workspace from one place.
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
          <Shield size={13} /> Admin-only operational controls
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
                Open the embedded Superset workspace in its own protected admin subpage.
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
                  setCreateError('')
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
                  setCreateError('')
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
                  setCreateError('')
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
            {createError && <div style={{ color: 'var(--color-danger)', fontSize: '0.82rem' }}>{createError}</div>}
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
              <HardDrive size={16} color="var(--color-accent)" />
            </div>
            <div>
              <div style={{ fontWeight: 600 }}>System settings</div>
              <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.82rem' }}>
                Configure object storage details and automated vector maintenance.
              </div>
            </div>
          </div>

          {settingsQuery.isLoading ? (
            <div style={{ display: 'flex', justifyContent: 'center', padding: '2rem' }}>
              <Spinner />
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              <div>
                <label style={{ fontSize: '0.8rem', fontWeight: 500, display: 'block', marginBottom: 4 }}>Storage provider</label>
                <select
                  value={settingsForm.storageProvider}
                  onChange={(e) => setSettingsForm(current => ({ ...current, storageProvider: e.target.value as SettingsForm['storageProvider'] }))}
                >
                  <option value="NONE">NONE</option>
                  <option value="S3">S3</option>
                  <option value="AZURE_BLOB">AZURE_BLOB</option>
                </select>
              </div>
              <div>
                <label style={{ fontSize: '0.8rem', fontWeight: 500, display: 'block', marginBottom: 4 }}>Bucket / container</label>
                <input
                  placeholder="analytics-backups"
                  value={settingsForm.storageBucket}
                  onChange={(e) => setSettingsForm(current => ({ ...current, storageBucket: e.target.value }))}
                />
              </div>
              <div>
                <label style={{ fontSize: '0.8rem', fontWeight: 500, display: 'block', marginBottom: 4 }}>Region</label>
                <input
                  placeholder="us-east-1"
                  value={settingsForm.storageRegion}
                  onChange={(e) => setSettingsForm(current => ({ ...current, storageRegion: e.target.value }))}
                />
              </div>
              <div>
                <label style={{ fontSize: '0.8rem', fontWeight: 500, display: 'block', marginBottom: 4 }}>Endpoint</label>
                <input
                  placeholder="https://s3.amazonaws.com or blob endpoint"
                  value={settingsForm.storageEndpoint}
                  onChange={(e) => setSettingsForm(current => ({ ...current, storageEndpoint: e.target.value }))}
                />
              </div>
              <div>
                <label style={{ fontSize: '0.8rem', fontWeight: 500, display: 'block', marginBottom: 4 }}>Access key / account</label>
                <input
                  placeholder="Access key"
                  value={settingsForm.storageAccessKey}
                  onChange={(e) => setSettingsForm(current => ({ ...current, storageAccessKey: e.target.value }))}
                />
              </div>
              <div>
                <label style={{ fontSize: '0.8rem', fontWeight: 500, display: 'block', marginBottom: 4 }}>Secret / credential</label>
                <input
                  type="password"
                  placeholder="Secret key or credential"
                  value={settingsForm.storageSecretKey}
                  onChange={(e) => setSettingsForm(current => ({ ...current, storageSecretKey: e.target.value }))}
                />
              </div>
              <div style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
                <label style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontSize: '0.85rem' }}>
                  <input
                    type="checkbox"
                    checked={settingsForm.vectorReindexEnabled}
                    onChange={(e) => setSettingsForm(current => ({ ...current, vectorReindexEnabled: e.target.checked }))}
                  />
                  Enable automatic vector recalculation
                </label>
              </div>
              <div>
                <label style={{ fontSize: '0.8rem', fontWeight: 500, display: 'block', marginBottom: 4 }}>Reindex interval (minutes)</label>
                <input
                  type="number"
                  min={15}
                  max={10080}
                  value={settingsForm.vectorReindexIntervalMinutes}
                  onChange={(e) => setSettingsForm(current => ({
                    ...current,
                    vectorReindexIntervalMinutes: Number(e.target.value) || 15,
                  }))}
                />
              </div>
              <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.8rem', lineHeight: 1.6 }}>
                Last automatic/manual vector run: {formatDate(settingsQuery.data?.lastVectorReindexAt)}<br />
                Status: {settingsQuery.data?.lastVectorReindexStatus ?? 'No runs recorded yet'}
              </div>
              {settingsError && <div style={{ color: 'var(--color-danger)', fontSize: '0.82rem' }}>{settingsError}</div>}
              {settingsSaved && <div style={{ color: 'var(--color-success)', fontSize: '0.82rem' }}>{settingsSaved}</div>}
              <Button
                loading={settingsMutation.isPending}
                onClick={() => settingsMutation.mutate(settingsForm)}
              >
                Save settings
              </Button>
            </div>
          )}
        </Card>

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
              <Database size={16} color="var(--color-accent)" />
            </div>
            <div>
              <div style={{ fontWeight: 600 }}>Operations</div>
              <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.82rem' }}>
                Manual reindex, health checks, and backup export.
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            <div style={{
              padding: '0.85rem',
              border: '1px solid var(--color-border)',
              borderRadius: 'var(--radius-md)',
              background: 'var(--color-bg)',
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8, fontWeight: 600 }}>
                <HeartPulse size={15} /> Stack health
              </div>
              {healthQuery.isLoading ? (
                <Spinner />
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6, fontSize: '0.82rem' }}>
                  <div>PostgreSQL: <strong>{healthQuery.data?.postgres}</strong></div>
                  <div>MongoDB: <strong>{healthQuery.data?.mongodb}</strong></div>
                  <div>Qdrant: <strong>{healthQuery.data?.qdrant}</strong></div>
                  <div style={{ color: 'var(--color-text-secondary)' }}>Checked {formatDate(healthQuery.data?.timestamp)}</div>
                </div>
              )}
            </div>

            <Button
              variant="secondary"
              loading={reindexMutation.isPending}
              onClick={() => reindexMutation.mutate()}
            >
              <RefreshCcw size={14} /> Recalculate vectors now
            </Button>

            <Button
              loading={backupMutation.isPending}
              onClick={() => backupMutation.mutate()}
            >
              <Download size={14} /> Export app backup
            </Button>

            {backupError && <div style={{ color: 'var(--color-danger)', fontSize: '0.82rem' }}>{backupError}</div>}

            <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.8rem', lineHeight: 1.6 }}>
              Backup export currently packages app-managed relational data, dashboard embed configuration, admin settings, events, and audit logs into a downloadable JSON file.
            </div>
          </div>
        </Card>
      </div>

      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'center', marginBottom: '0.9rem', flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Users size={16} />
            <div>
              <div style={{ fontWeight: 600 }}>Access roster</div>
              <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.82rem' }}>
                Current application accounts, roles, and login status.
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
            {rosterError && (
              <div style={{ color: 'var(--color-danger)', fontSize: '0.82rem' }}>{rosterError}</div>
            )}
            {(usersQuery.data ?? []).map((user) => (
              <UserRow
                key={user.id}
                user={user}
                onDelete={(id) => deactivateUserMutation.mutate(id)}
                deleting={deactivateUserMutation.isPending && pendingDeleteId === user.id}
                pendingDeleteId={pendingDeleteId}
                onRequestDelete={setPendingDeleteId}
                onCancelDelete={() => setPendingDeleteId(null)}
              />
            ))}
            {usersQuery.data?.length === 0 && (
              <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem' }}>
                No users have been created yet.
              </div>
            )}
          </div>
        )}
      </Card>

      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'center', marginBottom: '0.9rem', flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <ScrollText size={16} />
            <div>
              <div style={{ fontWeight: 600 }}>Audit log</div>
              <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.82rem' }}>
                Recent administrative and operational actions recorded by the app.
              </div>
            </div>
          </div>
          <Button variant="ghost" size="sm" onClick={() => auditLogsQuery.refetch()} disabled={auditLogsQuery.isFetching}>
            Refresh
          </Button>
        </div>

        {auditLogsQuery.isLoading ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: '2rem' }}>
            <Spinner />
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            {(auditLogsQuery.data?.content ?? []).map((entry) => (
              <div
                key={entry.id}
                style={{
                  padding: '0.85rem 1rem',
                  border: '1px solid var(--color-border)',
                  borderRadius: 'var(--radius-md)',
                  background: 'var(--color-bg)',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
                  <div>
                    <div style={{ fontWeight: 600 }}>{entry.action}</div>
                    <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.82rem' }}>
                      User {entry.userId}
                      {entry.resourceType ? ` • ${entry.resourceType}` : ''}
                      {entry.resourceId ? ` • ${entry.resourceId}` : ''}
                    </div>
                  </div>
                  <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.8rem', textAlign: 'right' }}>
                    {formatDate(entry.timestamp)}<br />
                    {entry.ipAddress ?? 'No IP recorded'}
                  </div>
                </div>
              </div>
            ))}
            {auditLogsQuery.data?.content.length === 0 && (
              <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem' }}>
                No audit entries have been recorded yet.
              </div>
            )}
          </div>
        )}
      </Card>
    </div>
  )
}
