import { pluginRole } from '../../../api/access'
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAvailablePlugins } from '../../../api/plugins'
import { useAuthStore } from '../../../hooks/useAuthStore'
import { Button } from '../../../components/ui'
import { getErrorMessage } from '../../../components/admin/adminUtils'
import { createPluginApi } from '../../sdk'
import type { BoardCard } from './api'
import {RepositorySelect} from '../../../components/projects/RepositorySelect'
import {developmentApi,type DevelopmentFeatures} from '../../../api/projectDevelopment'

interface DevelopmentLink {id: string; providerId: string; repository: string | null; number: number | null; cardId: string | null; title: string; url: string | null; state: string | null; checkedAt: string | null; available: boolean}
interface Pull {number: number; title: string}
const api = createPluginApi('kanban')
export function DevelopmentLinks({boardId,cards,locked,projectId}: {boardId: string; cards: BoardCard[]; locked: boolean;projectId?:string|null}) {
  const plugins = useAvailablePlugins()
  const role = useAuthStore(state => pluginRole(state.user?.role ?? '')) ?? ''
  const policy = plugins.data?.find(plugin => plugin.manifest.id === 'kanban')?.tableAccess.plugin_kanban_development_links
  const providers = plugins.data?.filter(plugin => ['github','gitlab','gitea'].includes(plugin.manifest.id)) ?? []
  const settings=useQuery({queryKey:['project-development-settings',useAuthStore(state=>state.user?.id),projectId],queryFn:()=>developmentApi.settings(projectId!),enabled:!!projectId})
  if (!policy?.readRoles.includes(role)) return null
  if(projectId&&!settings.data?.features.repositories)return null
  return <DevelopmentLinkEditor key={boardId} boardId={boardId} cards={cards} locked={locked || !policy.writeRoles.includes(role)} features={settings.data?.features} providers={providers.map(plugin => ({id:plugin.manifest.id,name:plugin.manifest.name}))} />
}
function DevelopmentLinkEditor({boardId,cards,locked,providers,features}: {boardId: string; cards: BoardCard[]; locked: boolean; providers: {id: string; name: string}[];features?:DevelopmentFeatures}) {
  const client = useQueryClient()
  const scope = useAuthStore(state => `${state.user?.id}:${state.user?.role}`)
  const [provider, setProvider] = useState('')
  const [repository, setRepository] = useState('')
  const [requestedKind, setKind] = useState('repository')
  const kind=features?.pullRequests===false?'repository':requestedKind
  const [number, setNumber] = useState('')
  const [cardId, setCardId] = useState('')
  const selected = providers.find(item => item.id === provider)
  const source = selected ? createPluginApi(selected.id) : undefined
  const pulls = useQuery({queryKey:['development-pulls',scope,provider,repository],queryFn:() => source!.get<Pull[]>('/pulls',{repository}),enabled:!!source && !!repository && kind === 'pull',retry:false})
  const route = `/boards/${boardId}/development-links`
  const links = useQuery({queryKey:['plugin-kanban-development-links',boardId,scope,providers.map(item => item.id).sort((a,b)=>a.localeCompare(b)).join(',')],queryFn:() => api.get<DevelopmentLink[]>(route)})
  const action = useMutation({mutationFn:({type,id}: {type: 'add' | 'refresh' | 'remove'; id?: string}) => {
    if (type === 'remove') return api.delete(`${route}/${id}`)
    if (type === 'refresh') return api.post(`${route}/${id}/refresh`,{})
    return api.post(route,{providerId:provider,repository,number:kind === 'pull' ? Number(number) : null,cardId:cardId || null})
  },onSuccess:() => client.invalidateQueries({queryKey:['plugin-kanban-development-links',boardId]})})
  if (!providers.length && links.isSuccess && !links.data.length) return null
  return <section className="page-stack" aria-label="Repository and pull request links">
    <h2>Repository and pull request links</h2>
    <p className="field-help">Link repositories or pull/merge requests to this board or a specific card. Status is a saved snapshot; use Refresh to check the provider.</p>
    {locked && <p>Save board changes before editing links. Your role must also allow writing development links.</p>}
    {links.isLoading && <p role="status">Loading links…</p>}
    {links.isError && <p role="alert">Unable to load links. <button onClick={() => void links.refetch()}>Retry</button></p>}
    {links.data?.map(link => <article key={link.id} className="page-stack">
      <div>{link.url ? <a href={link.url} target="_blank" rel="noreferrer">{link.title}</a> : link.title} {link.state && <small>· {link.state}</small>}</div>
      <small>{link.cardId ? `Card: ${cards.find(card => card.id === link.cardId)?.title ?? 'Unavailable card'}` : 'Board'}{link.checkedAt && ` · Checked ${new Date(link.checkedAt).toLocaleString()}`}</small>
      <div className="inline-actions">{link.available && <Button variant="ghost" size="sm" disabled={locked || action.isPending} onClick={() => action.mutate({type:'refresh',id:link.id})}>Refresh</Button>}<Button variant="ghost" size="sm" disabled={locked || action.isPending} onClick={() => action.mutate({type:'remove',id:link.id})}>Remove link</Button></div>
    </article>)}
    {providers.length > 0 ? <fieldset disabled={locked || action.isPending} className="page-stack">
      <label className="form-field">Source control provider<select value={selected?.id ?? ''} onChange={event => {setProvider(event.target.value);setRepository('');setNumber('')}}><option value="">Choose provider</option>{providers.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
      {selected && <><RepositorySelect provider={selected.id} value={repository} onChange={value=>{setRepository(value);setNumber('')}} disabled={locked||action.isPending}/>
      <label className="form-field">Resource<select value={kind} onChange={event => setKind(event.target.value)}><option value="repository">Repository</option>{features?.pullRequests!==false&&<option value="pull">Pull / merge request</option>}</select></label>
      {kind === 'pull' && <label className="form-field">Request<select value={number} onChange={event => setNumber(event.target.value)}><option value="">Choose an open request</option>{pulls.data?.map(pull => <option key={pull.number} value={pull.number}>#{pull.number} {pull.title}</option>)}</select><span className="field-help">Or enter an existing request number, including closed requests.</span><input aria-label="Request number" type="number" min="1" value={number} onChange={event => setNumber(event.target.value)} /></label>}
      <label className="form-field">Attach to<select value={cardId} onChange={event => setCardId(event.target.value)}><option value="">This board</option>{cards.map(card => <option key={card.id} value={card.id}>{card.title}</option>)}</select></label>
      <Button disabled={!repository || (kind === 'pull' && !(Number(number) > 0))} onClick={() => action.mutate({type:'add'})}>Add link</Button></>}
    </fieldset> : <p className="field-help">An administrator can enable GitHub, GitLab, or Gitea in Plugins to add links.</p>}
    {(pulls.isError || action.isError) && <p role="alert">{getErrorMessage(action.error ?? pulls.error,'Unable to contact the source control plugin.')}</p>}
  </section>
}
