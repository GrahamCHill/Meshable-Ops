import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { adminUsersApi, type AdminUser } from '../../api/client'
import { Badge, Button, Card, Spinner } from '../ui'
import { AdminPageHeader } from '../admin/AdminPageHeader'
import { formatDate, getErrorMessage } from '../admin/adminUtils'
import { useAuthStore } from '../../hooks/useAuthStore'

type CreateUserForm = {
  email: string
  username: string
  name: string
  contactNumber: string
  password: string
  role: 'ADMIN' | 'USER'
  canUploadDocuments: boolean
}

const emptyForm: CreateUserForm = { email: '', username: '', name: '', contactNumber: '', password: '', role: 'USER', canUploadDocuments: false }

function UserRow({ user, pending, deleting, requestDelete, cancelDelete, deactivate, updatingPermission, toggleUploadPermission }: Readonly<{
  user: AdminUser
  pending: boolean
  deleting: boolean
  requestDelete: () => void
  cancelDelete: () => void
  deactivate: () => void
  updatingPermission: boolean
  toggleUploadPermission: () => void
}>) {
  const active = user.active !== false
  return (
    <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', padding: '0.9rem 1rem',   background: 'var(--color-bg)' }}>
      <div style={{ minWidth: 0, flex: '1 1 260px' }}>
        <div style={{ fontWeight: 600 }}>{user.email}</div>
        <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.8rem' }}>{user.username}</div>
        <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.8rem', marginTop: 4, lineHeight: 1.6 }}>
          {user.name ? `Name: ${user.name}` : 'Name: Not provided'}<br />
          {user.contactNumber ? `Contact: ${user.contactNumber}` : 'Contact: Not provided'}
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 8 }}>
          <Badge color={user.role === 'ADMIN' ? 'accent' : 'default'}>{user.role}</Badge>
          <Badge color={active ? 'success' : 'danger'}>{active ? 'Active' : 'Inactive'}</Badge>
          <Badge color={user.canUploadDocuments ? 'success' : 'default'}>{user.canUploadDocuments ? 'Uploads enabled' : 'Uploads disabled'}</Badge>
        </div>
        <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.78rem', marginTop: 8 }}>Created {formatDate(user.createdAt)}</div>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8, alignItems: 'flex-end' }}>
        <Button variant="secondary" size="sm" loading={updatingPermission} disabled={!active} onClick={toggleUploadPermission}>
           {user.canUploadDocuments ? 'Disable uploads' : 'Enable uploads'}
        </Button>
      {pending ? (
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap', justifyContent: 'flex-end', maxWidth: 360 }}>
          <span style={{ color: 'var(--color-text-secondary)', fontSize: '0.8rem' }}>This blocks future logins.</span>
          <Button variant="danger" size="sm" loading={deleting} onClick={deactivate}>Confirm deactivate</Button>
          <Button variant="ghost" size="sm" disabled={deleting} onClick={cancelDelete}>Cancel</Button>
        </div>
      ) : (
        <Button variant="danger" size="sm" disabled={!active} onClick={requestDelete}> {active ? 'Deactivate' : 'Inactive'}</Button>
      )}
      </div>
    </div>
  )
}

export function AdminUsersPage() {
  const queryClient = useQueryClient()
  const [form, setForm] = useState(emptyForm)
  const [error, setError] = useState('')
  const [rosterError, setRosterError] = useState('')
  const [pendingId, setPendingId] = useState<string | null>(null)
  const { user: currentUser, setUploadPermission } = useAuthStore()
  const users = useQuery({ queryKey: ['admin-users'], queryFn: adminUsersApi.list })
  const createUser = useMutation({
    mutationFn: adminUsersApi.create,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['admin-users'] }); setForm(emptyForm); setError('') },
    onError: err => setError(getErrorMessage(err, 'Unable to create the user right now.')),
  })
  const deactivateUser = useMutation({
    mutationFn: adminUsersApi.deactivate,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['admin-users'] }); setPendingId(null); setRosterError('') },
    onError: err => setRosterError(getErrorMessage(err, 'Unable to deactivate the user right now.')),
  })
  const uploadPermission = useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) => adminUsersApi.updateUploadPermission(id, enabled),
    onSuccess: saved => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      if (saved.id === currentUser?.id) setUploadPermission(saved.canUploadDocuments)
      setRosterError('')
    },
    onError: err => setRosterError(getErrorMessage(err, 'Unable to update upload permission right now.')),
  })

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
      <AdminPageHeader title="Users" description="Create application accounts, assign roles, and control login access."  actions={<Badge color="accent">{users.data?.length ?? 0} users</Badge>} />
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '1rem', alignItems: 'start' }}>
        <Card>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', fontWeight: 600, marginBottom: '1rem' }}> Create a user</div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            <label>Email<input type="email" placeholder="person@example.com" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} /></label>
            <label>Username<input placeholder="person" value={form.username} onChange={e => setForm({ ...form, username: e.target.value })} /></label>
            <label>Name<input placeholder="Person Name" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></label>
            <label>Contact number<input type="tel" placeholder="+1 555 0100" value={form.contactNumber} onChange={e => setForm({ ...form, contactNumber: e.target.value })} /></label>
            <label>Password<input type="password" placeholder="At least 8 characters" value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} /></label>
            <label>Role<select value={form.role} onChange={e => setForm({ ...form, role: e.target.value as CreateUserForm['role'] })}><option value="USER">USER</option><option value="ADMIN">ADMIN</option></select></label>
            <label style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}><input type="checkbox" checked={form.canUploadDocuments} onChange={e => setForm({ ...form, canUploadDocuments: e.target.checked })} /> Allow document and folder uploads</label>
            {error && <div style={{ color: 'var(--color-danger)', fontSize: '0.82rem' }}>{error}</div>}
            <Button loading={createUser.isPending} onClick={() => createUser.mutate({ email: form.email.trim(), username: form.username.trim(), name: form.name.trim() || undefined, contactNumber: form.contactNumber.trim() || undefined, password: form.password, role: form.role, canUploadDocuments: form.canUploadDocuments })}>Create user</Button>
          </div>
        </Card>
        <Card>
          <div style={{ fontWeight: 600, marginBottom: '1rem' }}>Access roster</div>
          {users.isLoading ? <div style={{ padding: '2rem', textAlign: 'center' }}><Spinner /></div> : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              {rosterError && <div style={{ color: 'var(--color-danger)', fontSize: '0.82rem' }}>{rosterError}</div>}
              {(users.data ?? []).map(user => <UserRow key={user.id} user={user} pending={pendingId === user.id} deleting={deactivateUser.isPending && pendingId === user.id} requestDelete={() => setPendingId(user.id)} cancelDelete={() => setPendingId(null)} deactivate={() => deactivateUser.mutate(user.id)} updatingPermission={uploadPermission.isPending && uploadPermission.variables?.id === user.id} toggleUploadPermission={() => uploadPermission.mutate({ id: user.id, enabled: !user.canUploadDocuments })} />)}
              {users.data?.length === 0 && <div style={{ color: 'var(--color-text-secondary)' }}>No users have been created yet.</div>}
            </div>
          )}
        </Card>
      </div>
    </div>
  )
}
