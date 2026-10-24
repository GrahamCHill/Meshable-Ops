import {useEffect,useState} from 'react'
import {useSearchParams} from 'react-router-dom'
import {useQueryClient} from '@tanstack/react-query'
import {profileApi,useProfile,type Profile} from '../../api/profile'
import {useAuthStore} from '../../hooks/useAuthStore'
import {useTheme} from '../../hooks/useTheme'
import {AccountSecurityPage} from './AccountSecurityPage'
import NotificationPreferencesPage from './NotificationPreferencesPage'
import {getErrorMessage} from '../admin/adminUtils'
import {Button} from '../ui'

export default function UserSettingsPage() {
  const query=useProfile();const client=useQueryClient();const {user,refreshUser}=useAuthStore();const {mode,toggleMode}=useTheme()
  const [params,setParams]=useSearchParams();const tab=params.get('tab') ?? 'profile'
  const [draft,setDraft]=useState<Profile>();const [dirty,setDirty]=useState(false);const [busy,setBusy]=useState(false);const [error,setError]=useState('');const [notice,setNotice]=useState('')
  useEffect(()=>{if(query.data&&!dirty)setDraft(query.data)},[query.data,dirty])
  const run=async(action:()=>Promise<Profile>)=>{setBusy(true);setError('');setNotice('');try{const value=await action();client.setQueryData(['account-profile',user?.id],value);setDraft(value);setDirty(false);if(user)refreshUser({...user,name:value.name,contactNumber:value.contactNumber});setNotice('Your settings are saved.')}catch(e){setError(getErrorMessage(e))}finally{setBusy(false)}}
  return <div className="page-stack" style={{maxWidth:950}}><h1>User settings</h1><nav className="workspace-tabs" aria-label="User settings">{[['profile','Profile & appearance'],['security','Security & MFA'],['notifications','Notification preferences']].map(([key,label])=><button key={key} aria-current={tab===key?'page':undefined} onClick={()=>setParams({tab:key})}>{label}</button>)}</nav>
    {tab==='security'?<AccountSecurityPage embedded/>:tab==='notifications'?<NotificationPreferencesPage embedded/>:<>
      {query.isLoading&&<p role="status">Loading profile…</p>}{query.isError&&<p role="alert">Unable to load your profile.</p>}
      {draft&&<form className="page-stack" onSubmit={e=>{e.preventDefault();void run(()=>profileApi.save(draft))}}><fieldset disabled={busy} className="page-stack"><div className="inline-actions">{draft.avatarVersion?<img className="profile-preview" src={`/api/account/profile/avatar?v=${draft.avatarVersion}`} alt="Your profile"/>:<span className="profile-preview profile-initials">{(draft.name||draft.username).slice(0,2).toUpperCase()}</span>}<label className="form-field">Profile image<input type="file" accept="image/png,image/jpeg,image/webp" disabled={dirty} onChange={e=>{const file=e.target.files?.[0];if(file)void run(()=>profileApi.avatar(file));e.target.value=''}}/><small>Recommended: square, 256 × 256 px. PNG, JPEG or WebP, up to 1 MiB. Save profile edits before changing the image.</small></label>{draft.avatarVersion&&<Button type="button" variant="ghost" disabled={dirty} onClick={()=>void run(profileApi.removeAvatar)}>Remove image</Button>}</div>
      <div className="profile-fields"><label className="form-field">Display name<input maxLength={120} value={draft.name} onChange={e=>{setDraft({...draft,name:e.target.value});setDirty(true)}}/></label><label className="form-field">Contact number<input type="tel" maxLength={32} value={draft.contactNumber} onChange={e=>{setDraft({...draft,contactNumber:e.target.value});setDirty(true)}}/></label><label className="form-field">Timezone<input required value={draft.timeZone} onChange={e=>{setDraft({...draft,timeZone:e.target.value});setDirty(true)}}/></label></div><p>{draft.email} · {draft.username} · {draft.role.replace('_',' ').toLowerCase()}</p><p className="field-help">Login identity and role are managed by your administrator. Calendar grids display your browser's timezone; your saved timezone is available to workspace extensions.</p><div><Button type="submit" loading={busy} disabled={!dirty}>Save profile</Button></div></fieldset></form>}
      <section className="page-stack"><h2>Appearance</h2><p>Current theme: {mode}.</p><div><Button variant="secondary" onClick={toggleMode}>Use {mode==='dark'?'light':'dark'} theme</Button></div></section>{error&&<p role="alert">{error}</p>}{notice&&<p role="status">{notice}</p>}
    </>}
  </div>
}
