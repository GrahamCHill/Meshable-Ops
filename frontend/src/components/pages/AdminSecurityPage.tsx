import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { extensionSecurityApi } from '../../api/plugins'
import { AdminPageHeader } from '../admin/AdminPageHeader'
import { getErrorMessage } from '../admin/adminUtils'
import { Button } from '../ui'

export function AdminSecurityPage() {
  const client = useQueryClient()
  const security = useQuery({queryKey: ['extension-security'], queryFn: extensionSecurityApi.get})
  const save = useMutation({mutationFn: (allow: boolean) => extensionSecurityApi.save({...security.data!, allowThirdPartyExtensions: allow}), onSuccess: async data => {
    client.setQueryData(['extension-security'], data)
    await Promise.all(['admin-plugins', 'plugins', 'plugin-runtime'].map(key => client.invalidateQueries({queryKey: [key]})))
  }})
  return <div className="page-stack" style={{maxWidth: 800}}>
    <AdminPageHeader title="Security" description="Control third-party extensions and account protection." />
    <section className="page-stack"><h2>Third-party extensions</h2>
      <p>Only a super admin can install ZIP plugins or change this setting. Review the source and permissions of every package; author details are self-declared.</p>
      {security.isLoading && <p role="status">Loading security settings…</p>}
      {security.isError && <p role="alert">Unable to load settings. <button onClick={() => void security.refetch()}>Retry</button></p>}
      {security.data && <label className="checkbox-label"><input type="checkbox" checked={security.data.allowThirdPartyExtensions} disabled={save.isPending} onChange={event => save.mutate(event.target.checked)} />Allow third-party extensions</label>}
      <p className="field-help">Off by default. Switching off blocks ZIP installation, hides uploaded plugin pages and menus, and blocks their data and search access. Installed packages, data, and encrypted settings are retained. Bundled plugins remain available.</p>
      {save.isError && <p role="alert">{getErrorMessage(save.error)}</p>}{save.isSuccess && <p role="status">Security settings saved.</p>}
      <div><Link to="/admin/plugins">Manage plugins</Link></div>
    </section>
    <section className="page-stack"><h2>Account protection</h2><p>Set up an authenticator app, email verification, or recovery codes for your account.</p><div><Link className="button button-secondary" to="/account/security">My security & MFA</Link></div></section>
    <section className="page-stack"><h2>Access controls</h2><p>Control member views, delegated administration, external access, and protected storage directories.</p><div><Link className="button button-secondary" to="/admin/access-controls">Manage access controls</Link></div></section>
    <Button variant="ghost" onClick={() => void security.refetch()} disabled={security.isFetching}>Reload security settings</Button>
  </div>
}
