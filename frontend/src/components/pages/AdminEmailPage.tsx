import {trimTrailingSlashes} from '../../lib/url'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { emailApi, emailInput, type EmailInput, type EmailSettings } from '../../api/email'
import { AdminPageHeader } from '../admin/AdminPageHeader'
import { EmailTransportForm } from '../admin/email/EmailTransportForm'
import { EmailSendPanel } from '../admin/email/EmailSendPanel'
import { getErrorMessage } from '../admin/adminUtils'
import { Button, Spinner } from '../ui'

export function AdminEmailPage() {
  const client = useQueryClient()
  const query = useQuery({ queryKey: ['admin-email-settings'], queryFn: emailApi.settings })
  const [draft, setDraft] = useState<EmailInput>()
  const [dirty, setDirty] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  useEffect(() => { if (query.data && !dirty) setDraft(emailInput(query.data.admin, query.data.source)) }, [query.data, dirty])
  const change = (patch: Partial<EmailInput>) => { setDraft(previous => previous && { ...previous, ...patch }); setDirty(true); setError(''); setNotice('') }
  const save = async () => {
    if (!draft) return
    setSaving(true); setError(''); setNotice('')
    try {
      // Keep write-only credentials out of the query mutation cache and wipe them after saving.
      const saved = await emailApi.save(draft.source === 'ENVIRONMENT' ? { source: 'ENVIRONMENT' } : draft)
      client.setQueryData(['admin-email-settings'], saved)
      setDraft(emailInput(saved.admin, saved.source)); setDirty(false); setNotice('Email settings saved.')
    } catch (failure) { setError(getErrorMessage(failure, 'Unable to save email settings.')) }
    finally { setSaving(false) }
  }
  const settings = query.data
  const environment = draft?.source === 'ENVIRONMENT'
  const view = environment ? settings?.environment : settings?.admin
  const value = environment && view ? emailInput(view, 'ENVIRONMENT') : draft
  const active = settings?.source === 'ADMIN' ? settings.admin : settings?.environment
  const sameSmtp = environment || (!!draft && !!view && draft.provider === view.provider && draft.smtpHost.trim().toLowerCase() === view.smtpHost.toLowerCase() && draft.smtpPort === view.smtpPort && draft.smtpSecurity === view.smtpSecurity && draft.smtpAuth === view.smtpAuth)
  const sameApi = environment || (!!draft && !!view && draft.provider === view.provider && trimTrailingSlashes(draft.apiBaseUrl.trim()) === view.apiBaseUrl && draft.mailgunDomain.trim() === view.mailgunDomain)
  return <div className="page-stack" style={{ maxWidth: 900 }}>
    <AdminPageHeader title="Email delivery" description="Connect an SMTP account, app password, or transactional email service." actions={<Link to="/admin/notifications">Back to Notifications</Link>} />
    {query.isLoading && <Spinner />}
    {query.isError && <p role="alert" className="feedback error">Unable to load email settings. <Button variant="ghost" onClick={() => query.refetch()}>Retry</Button></p>}
    {settings && draft && view && value && <>
      <form className="page-stack" onSubmit={event => { event.preventDefault(); void save() }}>
        <fieldset className="page-stack" disabled={saving}>
          <div className="form-field"><label htmlFor="email-source">Configuration source</label><select id="email-source" value={draft.source} onChange={event => change({ source: event.target.value as EmailSettings['source'] })}><option value="ENVIRONMENT">Server environment</option><option value="ADMIN">Admin settings</option></select><small>These sources keep separate credentials. Selecting one does not copy secrets from the other.</small></div>
          {environment && <p className="field-help">Environment values are read-only. Choose Admin settings to edit a separate configuration.</p>}
          {!environment && !settings.encryptionReady && <p className="feedback warning">Set APP_SECRETS_ENCRYPTION_KEY on the server before saving email credentials.</p>}
          <fieldset className="page-stack" disabled={environment}>
            <label className="checkbox-label"><input type="checkbox" checked={value.enabled} onChange={event => change({ enabled: event.target.checked })} /> Enable email delivery</label>
            <div className="form-grid"><div className="form-field"><label htmlFor="email-from">Sender email</label><input id="email-from" type="email" maxLength={254} value={value.fromAddress} onChange={event => change({ fromAddress: event.target.value })} /></div><div className="form-field"><label htmlFor="email-name">Sender name</label><input id="email-name" maxLength={120} value={value.fromName} onChange={event => change({ fromName: event.target.value })} /></div></div>
            <div className="form-field"><label htmlFor="email-reply-to">Reply-to email (optional)</label><input id="email-reply-to" type="email" maxLength={254} value={value.replyTo} onChange={event => change({ replyTo: event.target.value })} /></div>
          </fieldset>
          <EmailTransportForm value={value} readOnly={environment} smtpConfigured={sameSmtp && view.smtpUsernameConfigured && view.smtpPasswordConfigured} keyConfigured={sameApi && view.apiKeyConfigured} onChange={change} />
          <p className="field-help">Your provider must allow the sender address or domain. Email delivery is optional; configure it before using mail workflows.</p>
        </fieldset>
        {error && <p role="alert" className="feedback error">{error}</p>}{notice && <p role="status" className="feedback success">{notice}</p>}
        <div className="save-bar"><span>{dirty ? 'You have unsaved changes' : 'Settings are up to date'}</span><div className="inline-actions"><Button type="button" variant="ghost" disabled={!dirty || saving} onClick={() => { setDraft(emailInput(settings.admin, settings.source)); setDirty(false); setError(''); setNotice('') }}>Discard changes</Button><Button type="submit" disabled={!dirty} loading={saving}>Save email settings</Button></div></div>
      </form>
      {active?.enabled && <EmailSendPanel disabled={dirty || saving} />}
    </>}
  </div>
}
