import {useState,type ReactNode} from 'react'
import {useQuery,useQueryClient} from '@tanstack/react-query'
import {Link} from 'react-router-dom'
import api from '../../api/http'
import {projectsApi} from '../../api/projects'
import {useAuthStore} from '../../hooks/useAuthStore'
import './operations.css'

export interface Row {id:string;ownerId:string;projectId:string|null;kind:string;dataJson:string;revision:number;updatedAt:string}
export const today=()=>new Date().toISOString().slice(0,10)
export const monthStart=()=>today().slice(0,8)+'01'
export const isAdmin=(role?:string)=>role==='ADMIN'||role==='SUPER_ADMIN'
export const get=<T,>(path:string,params?:Record<string,unknown>)=>api.get<T>('/plugins/'+path,{params}).then(r=>r.data)
export const post=<T,>(path:string,value:unknown={})=>api.post<T>('/plugins/'+path,value,{timeout:180000}).then(r=>r.data)
export const put=<T,>(path:string,value:unknown)=>api.put<T>('/plugins/'+path,value,{timeout:180000}).then(r=>r.data)
export const remove=(path:string,revision:number)=>api.delete('/plugins/'+path,{params:{revision}}).then(r=>r.data)
export function useRun(){const qc=useQueryClient(),[busy,setBusy]=useState(false),[message,setMessage]=useState(''),[error,setError]=useState('');return {busy,message,error,run:async<T,>(action:()=>Promise<T>,done?:(value:T)=>void)=>{setBusy(true);setError('');setMessage('');try{const result=await action();done?.(result);setMessage('Saved.');await qc.invalidateQueries({queryKey:['operations']});return result}catch(e){const response=e as {response?:{data?:{message?:string;error?:string}}};setError(response.response?.data?.message??response.response?.data?.error??'The operation could not complete. Check permissions, configuration and the selected data.')}finally{setBusy(false)}}}}
export function Notice({state}:{state:ReturnType<typeof useRun>}){return <>{state.error&&<p role="alert">{state.error}</p>}{state.message&&<p role="status">{state.message}</p>}</>}
export function Field({label,children}:{label:string;children:ReactNode}){return <label className="form-field">{label}{children}</label>}
export function ProjectSelect({value,onChange,all=false}:{value:string;onChange:(value:string)=>void;all?:boolean}){const user=useAuthStore(s=>s.user);const q=useQuery({queryKey:['operations','projects',user?.id],queryFn:()=>projectsApi.list()});return <Field label="Project"><select value={value} onChange={e=>onChange(e.target.value)}><option value="">{all?'All my projects':'Choose a project'}</option>{q.data?.map(p=><option key={p.id} value={p.id}>{p.name}</option>)}</select>{q.isError&&<small>Projects could not load.</small>}</Field>}
export function PersonSelect({value,onChange,label='Member'}:{value:string;onChange:(value:string)=>void;label?:string}){const q=useQuery({queryKey:['operations','people'],queryFn:projectsApi.people});return <Field label={label}><select required value={value} onChange={e=>onChange(e.target.value)}><option value="">Choose an internal member</option>{q.data?.map(p=><option key={p.id} value={p.id}>{p.name}</option>)}</select></Field>}
export function DateRange({from,to,setFrom,setTo}:{from:string;to:string;setFrom:(s:string)=>void;setTo:(s:string)=>void}){return <div className="ops-fields"><Field label="From"><input type="date" value={from} onChange={e=>setFrom(e.target.value)}/></Field><Field label="To (inclusive)"><input type="date" value={to} onChange={e=>setTo(e.target.value)}/></Field></div>}
export function SettingsLink({id}:{id:string}){const user=useAuthStore(s=>s.user);return isAdmin(user?.role)?<Link to={`/admin/plugins?plugin=${id}&tab=configuration`}>Plugin settings</Link>:null}
export function LoadState({loading,error}:{loading:boolean;error:boolean}){return <>{loading&&<p role="status">Loading…</p>}{error&&<p role="alert">Unable to load this view. Check access and plugin settings.</p>}</>}
