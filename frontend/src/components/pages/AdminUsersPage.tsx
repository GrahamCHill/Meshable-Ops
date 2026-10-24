import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { adminUsersApi } from '../../api/users'
import { type AccountRole, isAdminRole } from '../../api/access'
import { Badge, Button, Spinner } from '../ui'
import { AdminPageHeader } from '../admin/AdminPageHeader'
import { CreateUserDialog } from '../admin/CreateUserDialog'
import { formatDate, getErrorMessage } from '../admin/adminUtils'
import { useAuthStore } from '../../hooks/useAuthStore'
import '../../styles/access.css'

const labels: Record<string,string> = {SUPER_ADMIN: 'Super admin', ADMIN: 'Admin', USER: 'Member', EXTERNAL: 'External member'}
export function AdminUsersPage() {
  const qc = useQueryClient()
  const [adding, setAdding] = useState(false)
  const [filter, setFilter] = useState('')
  const [pendingId, setPendingId] = useState<string>()
  const {user: current, setUploadPermission} = useAuthStore()
  const superAdmin = current?.role === 'SUPER_ADMIN'
  const users = useQuery({queryKey: ['admin-users'], queryFn: adminUsersApi.list})
  const refresh = () => { qc.invalidateQueries({queryKey: ['admin-users']}); qc.invalidateQueries({queryKey: ['my-access']}) }
  const deactivate = useMutation({mutationFn: adminUsersApi.deactivate, onSuccess: () => { refresh(); setPendingId(undefined) }})
  const role = useMutation({mutationFn: ({id, value}: {id: string; value: AccountRole}) => adminUsersApi.updateRole(id, value), onSuccess: refresh})
  const upload = useMutation({mutationFn: ({id, enabled}: {id: string; enabled: boolean}) => adminUsersApi.updateUploadPermission(id, enabled), onSuccess: saved => { refresh(); if (saved.id === current?.id) setUploadPermission(saved.canUploadDocuments) }})
  const failure = deactivate.error || role.error || upload.error
  const rows = users.data?.filter(user => `${user.email} ${user.username} ${user.name ?? ''} ${labels[user.role]}`.toLowerCase().includes(filter.toLowerCase())) ?? []
  return <div className="page-stack">
    <AdminPageHeader title="Users" description="Manage accounts and roles in the roster. Configure individual views and cloud directories in Access controls." actions={<div className="inline-actions"><Badge>{users.data?.length ?? 0} users</Badge><Button onClick={() => setAdding(true)}>Add new user</Button></div>} />
    {superAdmin && <Link to="/admin/access-controls">Open access controls and external sharing</Link>}
    <label>Find a user<input type="search" placeholder="Name, email, username, or role" value={filter} onChange={e => setFilter(e.target.value)} /></label>
    {failure && <p className="feedback error" role="alert">{getErrorMessage(failure)}</p>}
    {users.isLoading && <Spinner />}
    {users.isError && <p role="alert">Unable to load users. <button onClick={() => users.refetch()}>Retry</button></p>}
    {!users.isLoading && !users.isError && <div className="access-table-wrap"><table className="access-table" aria-label="User roster"><thead><tr><th>Name</th><th>Email</th><th>Username</th><th>Contact number</th><th>Role</th><th>Uploads</th><th>Status</th><th>Created</th><th>Actions</th></tr></thead><tbody>{rows.map(user => {
      const own = user.id === current?.id
      const manageable = superAdmin || !isAdminRole(user.role)
      return <tr key={user.id}>
        <td>{user.name || '—'}</td><td>{user.email}</td><td>{user.username}</td><td>{user.contactNumber || '—'}</td>
        <td>{superAdmin && !own ? <select aria-label={`Role for ${user.email}`} value={user.role} disabled={role.isPending} onChange={e => role.mutate({id: user.id, value: e.target.value as AccountRole})}>{Object.entries(labels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select> : labels[user.role] ?? user.role}</td>
        <td><input type="checkbox" aria-label={`Allow uploads for ${user.email}`} checked={user.canUploadDocuments} disabled={!user.active || !manageable || user.role === 'EXTERNAL' || upload.isPending} onChange={e => upload.mutate({id: user.id, enabled: e.target.checked})} /></td>
        <td>{user.active ? 'Active' : 'Inactive'}</td><td>{formatDate(user.createdAt)}</td>
        <td>{pendingId === user.id ? <div className="inline-actions"><Button size="sm" variant="danger" loading={deactivate.isPending} onClick={() => deactivate.mutate(user.id)}>Confirm deactivate</Button><Button size="sm" variant="ghost" onClick={() => setPendingId(undefined)}>Cancel</Button></div> : <Button size="sm" variant="ghost" disabled={own || !manageable || !user.active} onClick={() => setPendingId(user.id)}>Deactivate</Button>}</td>
      </tr>
    })}</tbody></table>{!rows.length && <p>No matching users.</p>}</div>}
    {adding && <CreateUserDialog superAdmin={superAdmin} close={() => setAdding(false)} />}
  </div>
}
