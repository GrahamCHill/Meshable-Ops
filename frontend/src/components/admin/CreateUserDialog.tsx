import { useEffect, useRef, useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { adminUsersApi } from '../../api/users'
import type { AccountRole } from '../../api/access'
import { getErrorMessage } from './adminUtils'
import { Button } from '../ui'

export function CreateUserDialog({ superAdmin, close }: { superAdmin: boolean; close: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null)
  const qc = useQueryClient()
  const [form, setForm] = useState({email: '', username: '', name: '', contactNumber: '', password: '', role: 'USER' as AccountRole, canUploadDocuments: false})
  useEffect(() => { const element = dialog.current; element?.showModal(); return () => element?.close() }, [])
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const submit = async () => {
    setPending(true); setError('')
    try {
      await adminUsersApi.create({...form, email: form.email.trim(), username: form.username.trim(), name: form.name.trim() || undefined, contactNumber: form.contactNumber.trim() || undefined})
      setForm({...form, password: ''}); qc.invalidateQueries({queryKey: ['admin-users']}); close()
    } catch (failure) { setError(getErrorMessage(failure)) } finally { setPending(false) }
  }
  return <dialog ref={dialog} className="user-dialog" aria-labelledby="create-user-title" onCancel={event => { if (pending) event.preventDefault(); else close() }}>
    <form onSubmit={event => { event.preventDefault(); void submit() }}>
      <h2 id="create-user-title">Add new user</h2>
      <div className="user-dialog-grid">
        <label>Email<input required autoFocus type="email" autoComplete="off" value={form.email} onChange={e => setForm({...form, email: e.target.value})} /></label>
        <label>Username<input required minLength={3} maxLength={50} autoComplete="off" value={form.username} onChange={e => setForm({...form, username: e.target.value})} /></label>
        <label>Name<input maxLength={120} value={form.name} onChange={e => setForm({...form, name: e.target.value})} /></label>
        <label>Contact number<input type="tel" maxLength={32} value={form.contactNumber} onChange={e => setForm({...form, contactNumber: e.target.value})} /></label>
      </div>
      <label>Initial password<input required type="password" autoComplete="new-password" minLength={8} maxLength={72} value={form.password} onChange={e => setForm({...form, password: e.target.value})} /></label>
      <label>Role<select value={form.role} onChange={e => { const role = e.target.value as AccountRole; setForm({...form, role, canUploadDocuments: role === 'EXTERNAL' ? false : form.canUploadDocuments}) }}><option value="USER">Member</option><option value="EXTERNAL">External member (read only)</option>{superAdmin && <><option value="ADMIN">Admin</option><option value="SUPER_ADMIN">Super admin</option></>}</select></label>
      <label className="check-label"><input type="checkbox" disabled={form.role === 'EXTERNAL'} checked={form.canUploadDocuments} onChange={e => setForm({...form, canUploadDocuments: e.target.checked})} />Allow storage uploads</label>
      {form.role === 'EXTERNAL' && <p className="field-help">New external members have no storage access. Grant their directories in Access controls → External sharing. No invitation email is sent.</p>}
      {error && <p className="feedback error" role="alert">{error}</p>}
      <div className="inline-actions"><Button type="submit" loading={pending}>Create user</Button><Button type="button" variant="ghost" disabled={pending} onClick={close}>Cancel</Button></div>
    </form>
  </dialog>
}
