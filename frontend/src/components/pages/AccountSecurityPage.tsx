import { useAccess } from '../../api/access'
import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import QRCode from 'qrcode'
import { mfaApi, type MfaComplete, type MfaEnrollment, type MfaMethod } from '../../api/mfa'
import { useAuthStore } from '../../hooks/useAuthStore'
import { getErrorMessage } from '../admin/adminUtils'
import { Button, Spinner } from '../ui'

export function AccountSecurityPage({embedded=false}: {embedded?: boolean} = {}) {
  const client = useQueryClient(); const navigate = useNavigate()
  const access = useAccess()
  const { login } = useAuthStore()
  const status = useQuery({queryKey:['account-mfa'],queryFn:mfaApi.status})
  const [password,setPassword] = useState(''); const [method,setMethod] = useState<MfaMethod>('TOTP')
  const [proofCode,setProofCode] = useState(''); const [proofToken,setProofToken] = useState('')
  const [enrollment,setEnrollment] = useState<MfaEnrollment & {kind:'totp'|'email'}>()
  const [confirmCode,setConfirmCode] = useState(''); const [qr,setQr] = useState('')
  const [recovery,setRecovery] = useState<string[]>([]); const [busy,setBusy] = useState(false)
  const [error,setError] = useState(''); const [notice,setNotice] = useState('')
  const active = !!status.data && (status.data.totpEnabled || status.data.emailEnabled)
  useEffect(() => {if(status.data) setMethod(status.data.totpEnabled ? 'TOTP' : status.data.emailEnabled && status.data.emailAvailable ? 'EMAIL' : 'RECOVERY')},[status.data?.totpEnabled,status.data?.emailEnabled,status.data?.emailAvailable])
  useEffect(() => {let cancelled=false; setQr(''); if(enrollment?.otpauthUri) void QRCode.toDataURL(enrollment.otpauthUri,{width:240,margin:2}).then(value=>{if(!cancelled)setQr(value)}); return()=>{cancelled=true}},[enrollment])
  const proof = () => ({password,method,code:proofCode.trim(),challengeToken:proofToken})
  const run = async (action:()=>Promise<void>) => {setBusy(true);setError('');setNotice('');try{await action()}catch(failure){setError(getErrorMessage(failure))}finally{setBusy(false)}}
  const complete = (result:MfaComplete) => {login(result.auth.token,result.auth); setRecovery(result.recoveryCodes);setEnrollment(undefined);setPassword('');setProofCode('');setProofToken('');setConfirmCode('');client.invalidateQueries({queryKey:['account-mfa']});setNotice('Security settings saved. Other sessions have been signed out.')}
  const setup = (kind:'totp'|'email') => run(async()=>{setRecovery([]);setEnrollment({...await mfaApi.setup(kind,proof()),kind});setProofCode('');setProofToken('')})
  return <div className="page-stack" style={{maxWidth:850}}>
    {!embedded&&<div className="inline-actions"><Link to="/">Back to workspace</Link>{access.canPath('/admin')&&<Link to="/admin">Back to admin menu</Link>}</div>}
    <div>{embedded?<h2>Security & MFA</h2>:<h1>Security & MFA</h1>}<p>Protect your account with an authenticator app or verified email codes.</p></div>
    {status.isLoading&&<Spinner/>}{status.isError&&<p role="alert">Unable to load security settings.</p>}
    {status.data&&<>
      {status.data.setupRecommended&&<div className="page-stack"><p role="status">Set up MFA for your primary administrator account. An authenticator works without email delivery.</p><Button variant="ghost" disabled={busy} onClick={()=>void run(async()=>{await mfaApi.dismiss();await client.invalidateQueries({queryKey:['account-mfa']});navigate('/')})}>Skip setup for now</Button></div>}
      <p>Authenticator: {status.data.totpEnabled?'Enabled':'Off'} · Email MFA: {status.data.emailEnabled?'Enabled':'Off'} · Recovery codes: {status.data.recoveryCodesRemaining}</p>
      {!status.data.emailAvailable&&<p className="field-help">Email MFA setup is unavailable until working email delivery is configured. Existing accounts can use an authenticator or recovery code.</p>}
      {!status.data.encryptionReady&&<p className="field-help">Set the server encryption key before enrolling an authenticator.</p>}
      <fieldset disabled={busy} className="page-stack">
        <div className="form-field"><label htmlFor="security-password">Current password</label><input id="security-password" type="password" autoComplete="current-password" maxLength={72} value={password} onChange={e=>setPassword(e.target.value)}/></div>
        {active&&<section className="page-stack"><h2 className="card-title">Verify your existing MFA</h2><div className="form-field"><label htmlFor="security-proof-method">Verification method</label><select id="security-proof-method" value={method} onChange={e=>{setMethod(e.target.value as MfaMethod);setProofCode('');setProofToken('')}}>{status.data.totpEnabled&&<option value="TOTP">Authenticator app</option>}{status.data.emailEnabled&&status.data.emailAvailable&&<option value="EMAIL">Email code</option>}<option value="RECOVERY">Recovery code</option></select></div>{method==='EMAIL'&&<Button type="button" variant="secondary" disabled={!password} onClick={()=>void run(async()=>{setProofToken((await mfaApi.setup('manage-email',{password})).challengeToken);setNotice('Verification email sent.')})}>Send verification email</Button>}<div className="form-field"><label htmlFor="security-proof-code">Current verification or recovery code</label><input id="security-proof-code" autoComplete="one-time-code" maxLength={128} value={proofCode} onChange={e=>setProofCode(e.target.value)}/></div></section>}
        {!enrollment&&<div className="inline-actions">{!status.data.totpEnabled&&<Button disabled={!password||!status.data.encryptionReady||(active&&!proofCode)} onClick={()=>void setup('totp')}>Set up authenticator</Button>}{!status.data.emailEnabled&&status.data.emailAvailable&&<Button disabled={!password||(active&&!proofCode)} onClick={()=>void setup('email')}>Set up email MFA</Button>}</div>}
        {enrollment&&<section className="page-stack"><h2 className="card-title">Confirm {enrollment.kind==='totp'?'authenticator':'email'} setup</h2>{enrollment.kind==='totp'?<>{qr&&<img src={qr} width={240} height={240} alt="Authenticator setup QR code"/>}<p>Scan this code in your authenticator app, or enter the setup key manually:</p><code style={{overflowWrap:'anywhere'}}>{enrollment.secret}</code></>:<p>Enter the code sent to your account email. Enrollment stays off until you confirm receipt.</p>}<div className="form-field"><label htmlFor="security-confirm-code">New verification code</label><input id="security-confirm-code" autoComplete="one-time-code" inputMode="numeric" maxLength={6} value={confirmCode} onChange={e=>setConfirmCode(e.target.value)}/></div><div className="inline-actions"><Button disabled={!confirmCode} onClick={()=>void run(async()=>complete(await mfaApi.confirm(enrollment.challengeToken,enrollment.kind==='totp'?'TOTP':'EMAIL',confirmCode.trim())))}>Confirm MFA setup</Button><Button variant="ghost" onClick={()=>{setEnrollment(undefined);setConfirmCode('')}}>Cancel setup</Button></div></section>}
        {active&&!enrollment&&<div className="inline-actions"><Button variant="secondary" disabled={!password||!proofCode} onClick={()=>void run(async()=>complete(await mfaApi.recovery(proof())))}>Generate new recovery codes</Button>{status.data.totpEnabled&&<Button variant="ghost" disabled={!password||!proofCode} onClick={()=>void run(async()=>complete(await mfaApi.disable({...proof(),target:'TOTP'})))}>Remove authenticator</Button>}{status.data.emailEnabled&&<Button variant="ghost" disabled={!password||!proofCode} onClick={()=>void run(async()=>complete(await mfaApi.disable({...proof(),target:'EMAIL'})))}>Remove email MFA</Button>}</div>}
      </fieldset>
      {!!recovery.length&&<section className="page-stack"><h2 className="card-title">Save your recovery codes</h2><p>These codes are shown once. Keep them somewhere secure, separate from this account. Each code can be used once if email or your authenticator is unavailable.</p><pre style={{whiteSpace:'pre-wrap'}}>{recovery.join('\n')}</pre><Button variant="secondary" onClick={()=>setRecovery([])}>I have saved these codes</Button></section>}
      {error&&<p role="alert" className="feedback error">{error}</p>}{notice&&<p role="status">{notice}</p>}
    </>}
  </div>
}
