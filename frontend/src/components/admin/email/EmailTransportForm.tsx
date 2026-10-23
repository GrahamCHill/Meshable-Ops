import { useId } from 'react'
import { emailApiUrls, type EmailInput, type EmailProvider } from '../../../api/email'
import { Button } from '../../ui'

export function EmailTransportForm({ value, readOnly, smtpConfigured, keyConfigured, onChange }: {
  value: EmailInput; readOnly: boolean; smtpConfigured: boolean; keyConfigured: boolean; onChange: (patch: Partial<EmailInput>) => void
}) {
  const id = useId()
  const preset = (smtpHost: string, smtpAuth: EmailInput['smtpAuth'] = 'PASSWORD', smtpUsername = '') =>
    onChange({ smtpHost, smtpPort: 587, smtpSecurity: 'STARTTLS', smtpAuth, smtpUsername, smtpPassword: '', clearSmtpCredentials: false })
  return <fieldset className="page-stack" disabled={readOnly}>
    <div className="form-field"><label htmlFor={`${id}-provider`}>Delivery provider</label><select id={`${id}-provider`} value={value.provider} onChange={event => {
      const provider = event.target.value as EmailProvider
      onChange({ provider, apiBaseUrl: emailApiUrls[provider], apiKey: '', clearApiKey: false, smtpUsername: '', smtpPassword: '', clearSmtpCredentials: false })
    }}><option value="SMTP">SMTP account or relay</option><option value="RESEND">Resend API</option><option value="SENDGRID">SendGrid API</option><option value="MAILGUN">Mailgun API</option></select></div>
    {value.provider === 'SMTP' ? <>
      {!readOnly && <div className="inline-actions" aria-label="SMTP presets"><Button type="button" variant="ghost" size="sm" onClick={() => preset('smtp.gmail.com')}>Gmail</Button><Button type="button" variant="ghost" size="sm" onClick={() => preset('smtp.office365.com', 'OAUTH2')}>Microsoft 365</Button><Button type="button" variant="ghost" size="sm" onClick={() => preset('email-smtp.us-east-1.amazonaws.com')}>Amazon SES</Button><Button type="button" variant="ghost" size="sm" onClick={() => preset('smtp.sendgrid.net', 'PASSWORD', 'apikey')}>SendGrid SMTP</Button></div>}
      <div className="form-grid"><div className="form-field"><label htmlFor={`${id}-host`}>SMTP host</label><input id={`${id}-host`} maxLength={253} value={value.smtpHost} placeholder="smtp.example.com" onChange={event => onChange({ smtpHost: event.target.value })} /></div><div className="form-field"><label htmlFor={`${id}-port`}>SMTP port</label><input id={`${id}-port`} type="number" min={1} max={65535} value={value.smtpPort} onChange={event => onChange({ smtpPort: Number(event.target.value) })} /></div></div>
      <div className="form-grid"><div className="form-field"><label htmlFor={`${id}-security`}>Connection security</label><select id={`${id}-security`} value={value.smtpSecurity} onChange={event => onChange({ smtpSecurity: event.target.value as EmailInput['smtpSecurity'] })}><option value="STARTTLS">STARTTLS (usually port 587)</option><option value="TLS">Implicit TLS (usually port 465)</option><option value="NONE">No TLS (unauthenticated relay only)</option></select></div><div className="form-field"><label htmlFor={`${id}-auth`}>SMTP authentication</label><select id={`${id}-auth`} value={value.smtpAuth} onChange={event => onChange({ smtpAuth: event.target.value as EmailInput['smtpAuth'] })}><option value="PASSWORD">Password or app password</option><option value="OAUTH2">OAuth2 access token</option><option value="NONE">No authentication (relay)</option></select></div></div>
      {value.smtpAuth !== 'NONE' && <>
        {readOnly ? <p className="field-help">SMTP credentials: {smtpConfigured ? 'Configured on the server' : 'Not configured'}</p> : <>
          <div className="form-field"><label htmlFor={`${id}-username`}>SMTP username</label><input id={`${id}-username`} autoComplete="off" maxLength={512} value={value.smtpUsername} placeholder={smtpConfigured ? 'Saved securely; leave blank to keep' : 'Account email or SMTP username'} onChange={event => onChange({ smtpUsername: event.target.value, clearSmtpCredentials: false })} /></div>
          <div className="form-field"><label htmlFor={`${id}-secret`}>{value.smtpAuth === 'OAUTH2' ? 'SMTP OAuth2 access token' : 'SMTP password or app password'}</label><input id={`${id}-secret`} type="password" autoComplete="new-password" maxLength={8192} value={value.smtpPassword} placeholder={smtpConfigured ? 'Saved securely; leave blank to keep' : 'Enter credential'} onChange={event => onChange({ smtpPassword: event.target.value, clearSmtpCredentials: false })} /></div>
          <label className="checkbox-label"><input type="checkbox" checked={value.clearSmtpCredentials} onChange={event => onChange({ clearSmtpCredentials: event.target.checked, smtpUsername: '', smtpPassword: '' })} /> Remove saved SMTP credentials</label>
          <p className="field-help">Credentials are write-only and encrypted. Blank fields keep them only for the same host, port, security, authentication, and account. Enter both again when changing accounts.</p>
        </>}
        {value.smtpAuth === 'OAUTH2' && <p className="field-help">Provide a valid SMTP OAuth2 access token. Token acquisition and renewal are managed outside this app.</p>}
      </>}
      <p className="field-help">App passwords go in the password field. Gmail account policy must allow app passwords. For Amazon SES, use the correct region's endpoint and SES SMTP credentials.</p>
    </> : <>
      <div className="form-field"><label htmlFor={`${id}-url`}>Email API base URL</label><input id={`${id}-url`} type="url" maxLength={2000} value={value.apiBaseUrl} onChange={event => onChange({ apiBaseUrl: event.target.value })} /><small>{value.provider === 'MAILGUN' ? 'Include /v3. EU region: https://api.eu.mailgun.net/v3.' : value.provider === 'SENDGRID' ? 'Use https://api.sendgrid.com or the configured EU service base URL, without /v3/mail/send.' : 'Use https://api.resend.com, without /emails.'}</small></div>
      {value.provider === 'MAILGUN' && <div className="form-field"><label htmlFor={`${id}-domain`}>Mailgun sending domain</label><input id={`${id}-domain`} maxLength={253} value={value.mailgunDomain} placeholder="mg.example.com" onChange={event => onChange({ mailgunDomain: event.target.value })} /></div>}
      {readOnly ? <p className="field-help">API key: {keyConfigured ? 'Configured on the server' : 'Not configured'}</p> : <>
        <div className="form-field"><label htmlFor={`${id}-key`}>Email API key</label><input id={`${id}-key`} type="password" autoComplete="new-password" maxLength={8192} value={value.apiKey} placeholder={keyConfigured ? 'Saved securely; leave blank to keep' : 'Enter API key'} onChange={event => onChange({ apiKey: event.target.value, clearApiKey: false })} /></div>
        <label className="checkbox-label"><input type="checkbox" checked={value.clearApiKey} onChange={event => onChange({ clearApiKey: event.target.checked, apiKey: '' })} /> Remove saved API key</label>
        <p className="field-help">A saved key is retained only for the same provider, API URL, and Mailgun domain.</p>
      </>}
    </>}
  </fieldset>
}
