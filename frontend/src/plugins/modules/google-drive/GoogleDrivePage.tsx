import {useState} from 'react'
import {useQuery} from '@tanstack/react-query'
import {createPluginApi} from '../../sdk'
import {Button} from '../../../components/ui'
import {getErrorMessage} from '../../../components/admin/adminUtils'
import {useAuthStore} from '../../../hooks/useAuthStore'
import '../../../styles/projects.css'
interface DriveFile {id:string;name:string;folder:boolean;size:number;modifiedAt:string|null;url:string|null}
interface DriveFiles {items:DriveFile[];nextPageToken:string|null;folderId:string;rootId:string}
const api=createPluginApi('google-drive')
export default function GoogleDrivePage() {
  const actor=useAuthStore(state=>state.user?.id),[trail,setTrail]=useState<{id:string;name:string}[]>([]),[pages,setPages]=useState<string[]>([])
  const folder=trail[trail.length-1]?.id,pageToken=pages[pages.length-1]
  const query=useQuery({queryKey:['google-drive-files',actor,folder,pageToken],queryFn:()=>api.get<DriveFiles>('/files',{folder,pageToken}),retry:false})
  return <div className="page-stack"><h1>Google Drive</h1><p>Browse the folder your administrator connected. Opening a file uses your Google account's own permissions.</p>
    <nav className="inline-actions" aria-label="Drive folders"><button onClick={()=>{setTrail([]);setPages([])}}>Connected folder</button>{trail.map((part,index)=><button key={part.id} onClick={()=>{setTrail(trail.slice(0,index+1));setPages([])}}>{part.name}</button>)}</nav>
    {query.isLoading&&<p role="status">Loading files…</p>}{query.isError&&<p role="alert">{getErrorMessage(query.error)} <button onClick={()=>void query.refetch()}>Retry</button></p>}
    {query.data&&<><div className="project-table-wrap"><table className="project-table"><thead><tr><th>Name</th><th>Type</th><th>Size</th><th>Modified</th></tr></thead><tbody>{query.data.items.map(file=><tr key={file.id}><td>{file.folder?<button onClick={()=>{setTrail([...trail,{id:file.id,name:file.name}]);setPages([])}}>{file.name}</button>:file.url?<a href={file.url} target="_blank" rel="noreferrer">{file.name}</a>:file.name}</td><td>{file.folder?'Folder':'File'}</td><td>{file.folder?'—':`${file.size.toLocaleString()} bytes`}</td><td>{file.modifiedAt?new Date(file.modifiedAt).toLocaleString():'—'}</td></tr>)}</tbody></table></div>{!query.data.items.length&&<p>This folder is empty.</p>}<div className="inline-actions"><Button variant="ghost" disabled={!pages.length} onClick={()=>setPages(pages.slice(0,-1))}>Previous page</Button><Button variant="ghost" disabled={!query.data.nextPageToken} onClick={()=>setPages([...pages,query.data!.nextPageToken!])}>Next page</Button></div></>}
  </div>
}
