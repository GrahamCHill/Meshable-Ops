import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { accessApi, type AccessPolicyView, type MemberAccess, type StorageRule } from '../../api/access'
import { adminUsersApi } from '../../api/users'
import { AdminPageHeader } from '../admin/AdminPageHeader'
import { AccessRuleList, AccessStoragePicker } from '../admin/AccessStoragePicker'
import { getErrorMessage } from '../admin/adminUtils'
import { Button } from '../ui'
import '../../styles/access.css'

const emptyMember = (): MemberAccess => ({features: null, restrictStorage: false, grants: [], blocks: []})
const toggle = (values: string[], value: string) => values.includes(value) ? values.filter(v => v !== value) : [...values, value]
const append = (rules: StorageRule[], rule: StorageRule) => rules.some(r => r.folderId === rule.folderId && r.path === rule.path && r.type === rule.type) ? rules : [...rules, rule]

export default function AdminAccessPage() {
  const qc = useQueryClient()
  const query = useQuery({queryKey: ['access-policy'], queryFn: accessApi.get, refetchOnWindowFocus: false})
  const users = useQuery({queryKey: ['admin-users'], queryFn: adminUsersApi.list})
  const roots = useQuery({queryKey: ['access-roots'], queryFn: accessApi.roots})
  const [draft, setDraft] = useState<AccessPolicyView>()
  const [tab, setTab] = useState('roles')
  const [memberId, setMemberId] = useState('')
  const [picker, setPicker] = useState<'grants' | 'blocks' | 'protectedPaths'>()
  const [saved, setSaved] = useState(false)
  useEffect(() => { if (query.data) setDraft(query.data) }, [query.data])
  const save = useMutation({mutationFn: accessApi.save, onSuccess: value => { setDraft(value); qc.setQueryData(['access-policy'], value); qc.invalidateQueries({queryKey: ['my-access']}); setSaved(true) }})
  const change = (value: AccessPolicyView) => { setDraft(value); setSaved(false); save.reset() }
  if (query.isLoading) return <p role="status">Loading access controls…</p>
  if (query.isError || !draft) return <p role="alert">Unable to load access controls. <button onClick={() => query.refetch()}>Retry</button></p>
  const selected = users.data?.find(user => user.id === memberId)
  const member = draft.policy.members[memberId] ?? {...emptyMember(), restrictStorage: selected?.role === 'EXTERNAL'}
  const updateMember = (value: MemberAccess) => change({...draft, policy: {...draft.policy, members: {...draft.policy.members, [memberId]: value}}})
  const setRule = (rule: StorageRule) => {
    if (picker === 'protectedPaths') change({...draft, policy: {...draft.policy, protectedPaths: [...draft.policy.protectedPaths.filter(p => p.rule.folderId !== rule.folderId || p.rule.path !== rule.path || p.rule.type !== rule.type), {rule, externalOnly: true}]}})
    else if (picker) updateMember({...member, [picker]: append(member[picker], rule)})
  }
  const allRoots = roots.data ?? []
  const members = users.data?.filter(user => user.role !== 'SUPER_ADMIN' && (tab !== 'external' || user.role === 'EXTERNAL')) ?? []
  const roleFeatures = selected ? draft.policy.roleFeatures[selected.role] ?? [] : []
  return <div className="page-stack">
    <AdminPageHeader title="Access controls" description="Choose which views and cloud files each role or member can access. Super admins always retain full access." actions={<Button loading={save.isPending} onClick={() => save.mutate({revision: draft.revision, policy: draft.policy})}>Save access controls</Button>} />
    {save.isError && <p className="feedback error" role="alert">{getErrorMessage(save.error)} <Button variant="ghost" size="sm" onClick={() => query.refetch()}>Reload saved controls</Button></p>}
    {saved && <p role="status">Access controls saved.</p>}
    <nav className="inline-actions" aria-label="Access control sections">{[['roles','Role features'], ['members','Member access'], ['external','External sharing'], ['protected','Protected files']].map(([id, label]) => <Button key={id} variant={tab === id ? 'primary' : 'ghost'} onClick={() => { setTab(id); setMemberId(''); setPicker(undefined) }}>{label}</Button>)}</nav>
    {tab === 'roles' && <section className="page-stack"><h2>Views and administration features</h2><p className="field-help">Role settings are the maximum available to a member. Member settings can narrow that access. Only super admins can assign administrator roles or change these controls.</p><div className="access-table-wrap"><table className="access-table"><thead><tr><th>View or feature</th><th>Admin</th><th>Business Manager</th><th>Member</th><th>External member</th></tr></thead><tbody>{draft.features.filter(f => !['admin.access', 'admin.security'].includes(f.id)).map(feature => <tr key={feature.id}><th scope="row">{feature.label}<small>{feature.path}</small></th>{['ADMIN','BUSINESS_MANAGER','USER','EXTERNAL'].map(role => <td key={role}><input type="checkbox" aria-label={`${feature.label} for ${role}`} disabled={feature.admin && role !== 'ADMIN'} checked={draft.policy.roleFeatures[role]?.includes(feature.id) ?? false} onChange={() => change({...draft, policy: {...draft.policy, roleFeatures: {...draft.policy.roleFeatures, [role]: toggle(draft.policy.roleFeatures[role] ?? [], feature.id)}}})} /></td>)}</tr>)}</tbody></table></div></section>}
    {(tab === 'members' || tab === 'external') && <section className="page-stack"><h2>{tab === 'external' ? 'External sharing' : 'Member access'}</h2><p className="field-help">{tab === 'external' ? 'External accounts are read only and see no storage until directories or files are granted. Invitations are not part of this screen.' : 'Restrict a member to selected views and storage directories, or exclude specific files.'} <Link to="/admin/users">Manage accounts</Link></p>
      <label>Member<select value={memberId} onChange={e => { setMemberId(e.target.value); setPicker(undefined) }}><option value="">Choose a member</option>{members.map(user => <option key={user.id} value={user.id}>{user.name || user.username} — {user.email} ({user.role}){!user.active ? ' · inactive' : ''}</option>)}</select></label>
      {users.isError && <p role="alert">Unable to load members.</p>}
      {selected && <>
        <h3>Frontend views</h3><label className="check-label"><input type="checkbox" checked={member.features !== null} onChange={e => updateMember({...member, features: e.target.checked ? [...roleFeatures] : null})} />Limit this member to selected role features</label>
        {member.features !== null && <div className="access-feature-list">{draft.features.filter(f => roleFeatures.includes(f.id)).map(f => <label className="check-label" key={f.id}><input type="checkbox" checked={member.features?.includes(f.id)} onChange={() => updateMember({...member, features: toggle(member.features ?? [], f.id)})} />{f.label}</label>)}</div>}
        <h3>Cloud directories and files</h3><label className="check-label"><input type="checkbox" checked={member.restrictStorage || selected.role === 'EXTERNAL'} disabled={selected.role === 'EXTERNAL'} onChange={e => updateMember({...member, restrictStorage: e.target.checked})} />Only allow selected directories and files</label>
        {(member.restrictStorage || selected.role === 'EXTERNAL') && <><h4>Allowed directories and files</h4><AccessRuleList rules={member.grants} roots={allRoots} remove={index => updateMember({...member, grants: member.grants.filter((_, i) => i !== index)})} /><Button variant="secondary" onClick={() => setPicker(picker === 'grants' ? undefined : 'grants')}>Add allowed directory or file</Button></>}
        <h4>Excluded directories and files</h4><p className="field-help">Exclusions always take priority, including inside an allowed directory.</p><AccessRuleList rules={member.blocks} roots={allRoots} remove={index => updateMember({...member, blocks: member.blocks.filter((_, i) => i !== index)})} /><Button variant="secondary" onClick={() => setPicker(picker === 'blocks' ? undefined : 'blocks')}>Add exclusion</Button>
      </>}
    </section>}
    {tab === 'protected' && <section className="page-stack"><h2>Business-protected paths</h2><p className="field-help">These files and directories are excluded from external members and external file shares. You can also exclude them from internal members and admins. Super admins retain access.</p>
      {draft.policy.protectedPaths.map((entry, index) => <div key={index}><AccessRuleList roots={allRoots} rules={[entry.rule]} remove={() => change({...draft, policy: {...draft.policy, protectedPaths: draft.policy.protectedPaths.filter((_, i) => i !== index)}})} /><label className="check-label"><input type="checkbox" checked={!entry.externalOnly} onChange={e => change({...draft, policy: {...draft.policy, protectedPaths: draft.policy.protectedPaths.map((p, i) => i === index ? {...p, externalOnly: !e.target.checked} : p)}})} />Also block internal members and admins</label></div>)}
      <Button variant="secondary" onClick={() => setPicker(picker === 'protectedPaths' ? undefined : 'protectedPaths')}>Add protected directory or file</Button>
    </section>}
    {picker && <section className="page-stack"><div className="inline-actions"><h3>{picker === 'grants' ? 'Choose an allowed path' : picker === 'blocks' ? 'Choose an excluded path' : 'Choose a protected path'}</h3><Button variant="ghost" size="sm" onClick={() => setPicker(undefined)}>Close picker</Button></div>{roots.isError ? <p role="alert">Unable to load storage roots.</p> : <AccessStoragePicker roots={allRoots} onSelect={setRule} />}<p className="field-help">Directories refer to the actual S3 or Blob prefixes beneath the selected storage root. Selecting a directory includes future files inside it.</p></section>}
  </div>
}
