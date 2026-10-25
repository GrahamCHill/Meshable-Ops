import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate, Link } from 'react-router-dom'
import { authApi, type AuthResponse } from '../../api/auth'
import { mfaApi, type MfaChallenge, type MfaMethod } from '../../api/mfa'
import { useAuthStore } from '../../hooks/useAuthStore'
import { Button } from '../ui'
import { Brand } from '../layout/Branding'
import { LoginBackground } from '../layout/LoginBackground'

function getAuthErrorMessage(error: unknown) {
  const fallback = 'Invalid email or password'
  if (!error || typeof error !== 'object') return fallback

  const maybeResponse = (error as { response?: { data?: { message?: string } } }).response
  return typeof maybeResponse?.data?.message === 'string' ? maybeResponse.data.message : fallback
}

function AuthCard({ children, title, subtitle }: { children: React.ReactNode; title: string; subtitle: React.ReactNode }) {
  return (
    <LoginBackground>
      <div className="auth-panel">
        <div className="auth-heading">
          <Brand className="auth-brand" />
          <h1>{title}</h1>
          <p>{subtitle}</p>
        </div>
        <div>
          {children}
        </div>
      </div>
    </LoginBackground>
  )
}

export function LoginPage() {
  const navigate = useNavigate()
  const { login, logout } = useAuthStore()
  const providers = useQuery({queryKey:['auth-providers'],queryFn:authApi.providers})
  const external = new URLSearchParams(window.location.search).get('external')
  const completingExternal = useRef(false)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(external === 'failed' ? 'External sign-in was cancelled or could not be verified. Please try again.' : '')
  const [busy, setBusy] = useState(external === 'complete')
  const [challenge, setChallenge] = useState<MfaChallenge>()
  const [method, setMethod] = useState<MfaMethod>('TOTP')
  const [code, setCode] = useState('')
  const [notice, setNotice] = useState('')
  useEffect(() => {
    if (external !== 'complete' || completingExternal.current) return
    completingExternal.current = true
    // Consume the server-held result once, including React StrictMode's repeated effect setup.
    window.history.replaceState(window.history.state, '', '/login')
    void authApi.completeExternal().then(result => {
      if (result.linked) { navigate('/account/settings?tab=security&identity=connected', {replace:true}); return }
      if (!result.auth) throw new Error('Missing sign-in result')
      if ('mfaRequired' in result.auth) {
        logout(); setChallenge(result.auth); setMethod(result.auth.methods[0] ?? 'RECOVERY')
      } else {
        login(result.auth.token, result.auth)
        navigate(result.auth.suggestMfaSetup ? '/account/security?setup=1' : '/', {replace:true})
      }
    }).catch(failure => setError(getAuthErrorMessage(failure))).finally(() => setBusy(false))
  }, [external, login, logout, navigate])
  const complete = (data: AuthResponse) => { login(data.token, data); navigate(data.suggestMfaSetup ? '/account/security?setup=1' : '/') }
  const submit = async () => {
    setBusy(true); setError(''); setNotice('')
    try {
      if (challenge) complete(await mfaApi.verify(challenge.challengeToken, method, code.trim()))
      else {
        const data = await authApi.login({ email, password }); setPassword('')
        if ('mfaRequired' in data) { logout(); setChallenge(data); setMethod(data.methods[0] ?? 'RECOVERY') }
        else complete(data)
      }
    } catch (failure) { setError(getAuthErrorMessage(failure)) }
    finally { setBusy(false) }
  }
  const sendEmail = async () => {
    if (!challenge) return
    setBusy(true); setError('')
    try { setNotice((await mfaApi.sendLoginEmail(challenge.challengeToken)).message) }
    catch (failure) { setError(getAuthErrorMessage(failure)) }
    finally { setBusy(false) }
  }

  return (
    <AuthCard title={challenge ? 'Verify your sign-in' : 'Sign in'} subtitle={challenge ? 'Complete multi-factor authentication' : 'Your business workspace'}>
      <form onSubmit={event => {event.preventDefault(); void submit()}} style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
        {!challenge ? <>
        {!!providers.data?.length && <div className="page-stack">
          {providers.data.map(provider => <Button key={provider.id} type="button" variant="secondary" disabled={busy} onClick={() => {setBusy(true); window.location.assign(provider.authorizationUrl)}}>Continue with {provider.name}</Button>)}
          <p className="field-help">Or sign in with your workspace password. Connect an external identity in Security &amp; MFA before using it here.</p>
        </div>}
        <div>
          <label style={{ fontSize: '0.8rem', fontWeight: 500, display: 'block', marginBottom: 4 }}>Email</label>
          <input type="email" placeholder="you@example.com" value={email} onChange={e => { setEmail(e.target.value); setError('') }} />
        </div>
        <div>
          <label style={{ fontSize: '0.8rem', fontWeight: 500, display: 'block', marginBottom: 4 }}>Password</label>
          <input type="password" placeholder="password" value={password} onChange={e => { setPassword(e.target.value); setError('') }}
            autoComplete="current-password" />
        </div>
        </> : <>
          {!!challenge.methods.length && <><label htmlFor="mfa-method">Verification method</label><select id="mfa-method" value={method} onChange={event => {setMethod(event.target.value as MfaMethod); setCode(''); setError(''); setNotice('')}}>{challenge.methods.map(value => <option key={value} value={value}>{value === 'TOTP' ? 'Authenticator app' : value === 'EMAIL' ? 'Email verification code' : 'Recovery code'}</option>)}</select>
          {method === 'EMAIL' && <Button type="button" variant="secondary" disabled={busy} onClick={() => void sendEmail()}>Send verification email</Button>}
          <label htmlFor="mfa-code">{method === 'RECOVERY' ? 'Recovery code' : 'Verification code'}</label><input id="mfa-code" autoComplete="one-time-code" maxLength={128} value={code} onChange={event => setCode(event.target.value)} /></>}
          {!challenge.methods.length && <p role="alert">Email delivery is unavailable and there are no remaining recovery codes. Restore email delivery or contact your administrator.</p>}
          <Button type="button" variant="ghost" disabled={busy} onClick={() => {setChallenge(undefined); setCode(''); setError(''); setNotice('')}}>Back to sign in</Button>
        </>}
        {error && <p style={{ color: 'var(--color-danger)', fontSize: '0.8rem' }}>{error}</p>}
        {notice && <p role="status">{notice}</p>}
        <Button type="submit" loading={busy} disabled={busy || (challenge ? !code.trim() || !challenge.methods.length : !email || !password)} style={{ width: '100%', marginTop: 4 }}>{challenge ? 'Verify and sign in' : 'Sign in'}</Button>
        <p style={{ textAlign: 'center', fontSize: '0.8rem', color: 'var(--color-text-secondary)' }}>
          Need access? Contact an administrator for an invite.
        </p>
      </form>
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
          className="button button-secondary button-md"
        >
          Back to sign in
        </Link>
      </div>
    </AuthCard>
  )
}
