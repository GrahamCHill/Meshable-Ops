import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { extensionSecurityApi, pluginsApi } from '../../../api/plugins'
import { useAuthStore } from '../../../hooks/useAuthStore'
import { getErrorMessage } from '../adminUtils'
import { Button } from '../../ui'
import {PluginCompatibility} from './PluginCompatibility'

export function PluginInstaller() {
  const superAdmin = useAuthStore(state => state.user?.role === 'SUPER_ADMIN')
  const security = useQuery({queryKey: ['extension-security'], queryFn: extensionSecurityApi.get, enabled: superAdmin})
  const client = useQueryClient(); const [file, setFile] = useState<File>(); const [inputKey, setInputKey] = useState(0)
  const install = useMutation({mutationFn: () => pluginsApi.install(file!), onSuccess: async () => {
    setFile(undefined); setInputKey(value => value + 1)
    await Promise.all(['admin-plugins', 'plugins', 'my-access', 'access-policy','admin-navigation','workspace-navigation','admin-section-artwork','admin-section-icons'].map(key => client.invalidateQueries({queryKey: [key]})))
  }})
  if (!superAdmin) return null
  return <section className="page-stack" aria-labelledby="install-plugin-title">
    <h2 id="install-plugin-title">Install a plugin</h2>
    <p className="field-help">Upload a reviewed ZIP with plugin.json and author.json, up to 16 MiB. Packages can provide frontend pages, Python/Kotlin backend code, or both. Backend-only plugins need no page. Check the uploaded code and permissions before enabling it. Missing or mismatched compatibleVersions show caution and backup advice; installed plugins start disabled.</p>
    <p className="field-help">Installed ZIP packages, artwork, settings, credentials, and records live in PostgreSQL and survive application updates when the same database and wrapping key are retained.</p>
    {security.isLoading && <p role="status">Checking extension security…</p>}
    {security.isError && <p role="alert">Unable to check extension security. <button onClick={() => void security.refetch()}>Retry</button></p>}
    {security.data && !security.data.allowThirdPartyExtensions && <p>Third-party extensions are disabled. <Link to="/admin/security">Change extension security</Link> to allow ZIP installations.</p>}
    {security.data?.allowThirdPartyExtensions && <form className="page-stack" onSubmit={event => {event.preventDefault(); if (file) install.mutate()}}>
      <label className="form-field">Plugin ZIP (maximum 16 MiB)<input key={inputKey} type="file" accept=".zip,application/zip" disabled={install.isPending} onChange={event => {setFile(event.target.files?.[0]); install.reset()}} /></label>
      {file && file.size > 16 * 1024 * 1024 && <p role="alert">Choose a ZIP no larger than 16 MiB.</p>}
      <div><Button type="submit" disabled={!file || file.size > 16 * 1024 * 1024 || install.isPending}>Install ZIP</Button></div>
    </form>}
    {install.isError && <p role="alert">{getErrorMessage(install.error, 'Unable to install plugin.')}</p>}
    {install.isSuccess && <><p role="status">{install.data.manifest.name} installed. <Link to={`/admin/plugins?plugin=${install.data.manifest.id}`}>Review compatibility and manage this plugin</Link>.</p><PluginCompatibility plugin={install.data}/></>}
  </section>
}
