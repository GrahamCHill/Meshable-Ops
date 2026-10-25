import {useState} from 'react'
import {useQuery, useQueryClient} from '@tanstack/react-query'
import {useSearchParams} from 'react-router-dom'
import {authApi} from '../../api/auth'
import {getErrorMessage} from '../admin/adminUtils'
import {Button} from '../ui'

export function ExternalIdentitySettings() {
  const providers = useQuery({queryKey:['auth-providers'],queryFn:authApi.providers})
  const identities = useQuery({queryKey:['account-identities'],queryFn:authApi.identities})
  const client = useQueryClient()
  const [params] = useSearchParams()
  const [busy,setBusy] = useState(false)
  const [error,setError] = useState('')
  const [notice,setNotice] = useState(params.get('identity') === 'connected' ? 'Your sign-in identity is connected.' : '')
  const run = async (provider: string, disconnect: boolean) => {
    setBusy(true); setError(''); setNotice('')
    try {
      if (disconnect) {
        await authApi.disconnectIdentity(provider)
        await client.invalidateQueries({queryKey:['account-identities']})
        setNotice('Identity disconnected. You can still sign in with your workspace password.')
      } else {
        const result = await authApi.connectIdentity(provider)
        window.location.assign(result.authorizationUrl)
      }
    } catch (failure) {setError(getErrorMessage(failure))}
    finally {setBusy(false)}
  }
  if (!providers.data?.length && !identities.data?.length) return providers.isError ? <p role="alert">Unable to load external sign-in providers.</p> : null
  const available = providers.data ?? []
  const disabled = identities.data?.filter(id => !available.some(provider => provider.id === id)) ?? []
  return <section className="page-stack">
    <h2 className="card-title">Connected sign-in accounts</h2>
    <p>Connect an account to sign in with it next time. Your workspace permissions and MFA still apply. Keep your workspace password for recovery and security settings.</p>
    {identities.isError && <p role="alert">Unable to load connected accounts.</p>}
    {available.map(provider => {
      const connected = identities.data?.includes(provider.id) ?? false
      return <div className="inline-actions" key={provider.id}><span>{provider.name} · {connected ? 'Connected' : 'Not connected'}</span><Button variant="secondary" disabled={busy || !identities.data} onClick={() => void run(provider.id,connected)}>{connected ? 'Disconnect' : 'Connect'} {provider.name}</Button></div>
    })}
    {disabled.map(id => <div className="inline-actions" key={id}><span>{id} · Provider disabled</span><Button variant="secondary" disabled={busy} onClick={() => void run(id,true)}>Disconnect {id}</Button></div>)}
    {error && <p role="alert">{error}</p>}{notice && <p role="status">{notice}</p>}
  </section>
}
