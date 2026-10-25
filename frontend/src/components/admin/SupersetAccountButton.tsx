import { useState, type FormEvent } from 'react'
import { isAxiosError } from 'axios'
import { supersetApi, type SupersetAccount } from '../../api/superset'
import { useAuthStore } from '../../hooks/useAuthStore'
import { Button } from '../ui'
import { Modal } from '../ui/Modal'

function PasswordDialog({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const email = useAuthStore(state => state.user?.email)
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [account, setAccount] = useState<SupersetAccount | null>(null)
  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (busy) return
    if (password !== confirmation) { setError('The passwords do not match.'); return }
    setBusy(true); setError('')
    try {
      const saved = await supersetApi.setPassword(password)
      setPassword(''); setConfirmation(''); setAccount(saved); onSaved()
    } catch (failure) {
      const data: unknown = isAxiosError(failure) ? failure.response?.data : null
      const message = data && typeof data === 'object' && 'message' in data ? data.message : null
      setError(typeof message === 'string' ? message : 'Could not save your Superset password. Try again.')
    } finally { setBusy(false) }
  }
  return <Modal title="Set or reset Superset password" onClose={onClose} busy={busy}>
    {account ? <div className="page-stack">
      <p role="status">Your Superset account {account.created ? 'is ready' : 'password has been updated'}.</p>
      <p>Sign in to the workspace with <strong>{account.username}</strong> and the password you just chose.</p>
      <Button onClick={onClose}>Return to workspace</Button>
    </div> : <form className="page-stack" onSubmit={submit}>
      <p>Create your personal Superset login or replace its password. Your Superset username is your email address.</p>
      <fieldset className="page-stack" disabled={busy}>
        <label className="form-field">Superset email<input type="email" value={email ?? ''} readOnly autoComplete="username" /></label>
        <label className="form-field">New Superset password<input type="password" value={password} onChange={event => setPassword(event.target.value)} required minLength={14} maxLength={128} autoComplete="new-password" aria-describedby="superset-password-help" /></label>
        <small id="superset-password-help">Use 14–128 characters. This password is separate from your application password.</small>
        <label className="form-field">Confirm password<input type="password" value={confirmation} onChange={event => setConfirmation(event.target.value)} required minLength={14} maxLength={128} autoComplete="new-password" /></label>
      </fieldset>
      {error && <p className="feedback error" role="alert">{error}</p>}
      <div className="inline-actions"><Button type="submit" loading={busy}>Save Superset password</Button><Button type="button" variant="ghost" disabled={busy} onClick={onClose}>Cancel</Button></div>
    </form>}
  </Modal>
}

export function SupersetAccountButton({ onSaved }: { onSaved: () => void }) {
  const [open, setOpen] = useState(false)
  return <>
    <Button variant="secondary" size="sm" onClick={() => setOpen(true)}>Set or reset Superset password</Button>
    {open && <PasswordDialog onClose={() => setOpen(false)} onSaved={onSaved} />}
  </>
}
