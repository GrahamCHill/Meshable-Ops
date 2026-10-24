import {useEffect,useState} from 'react'
import {useMutation} from '@tanstack/react-query'
import {artworkApi,type Artwork} from '../../api/artwork'
import {ArtworkIcon,artworkIcons} from './ArtworkIcon'
import {getErrorMessage} from '../admin/adminUtils'
import {Button} from '../ui'

export function ArtworkPicker({label,value,root,imageUrl,onSaved,defaultIcon='folder'}:{label:string;value:Artwork;root:string;imageUrl?:string;onSaved:(value:Artwork)=>void;defaultIcon?:string}) {
  const [icon,setIcon]=useState(value.icon),[file,setFile]=useState<File>(),[preview,setPreview]=useState<string>(),[preset,setPreset]=useState(false),[inputKey,setInputKey]=useState(0)
  useEffect(()=>{if(!file){setPreview(undefined);return}const url=URL.createObjectURL(file);setPreview(url);return()=>URL.revokeObjectURL(url)},[file])
  useEffect(()=>{setIcon(value.icon);setFile(undefined);setPreset(false);setInputKey(key=>key+1)},[value.revision,value.icon])
  const save=useMutation({mutationFn:()=>artworkApi.save(root,value,icon,file),onSuccess:onSaved})
  return <section className="page-stack artwork-picker" aria-label={label}><h3>{label}</h3><div className="inline-actions"><ArtworkIcon value={{icon,imageVersion:null}} imageUrl={preview??(!preset?imageUrl:undefined)} size={40}/>
    <label className="form-field">Built-in icon<select value={icon} disabled={save.isPending} onChange={e=>{setIcon(e.target.value);setFile(undefined);setPreset(true);save.reset()}}>{Object.keys(artworkIcons).map(name=><option key={name} value={name}>{name[0].toUpperCase()+name.slice(1)}</option>)}</select></label>
    <label className="form-field">Upload icon<input key={inputKey} type="file" accept="image/webp,image/png,image/jpeg" disabled={save.isPending} onChange={e=>{setFile(e.target.files?.[0]);setPreset(false);save.reset()}}/><small>Square, 128 × 128 px recommended. WebP, PNG or JPEG, up to 1 MiB.</small></label>
  </div>{file&&file.size>1024*1024&&<p role="alert">Choose an icon up to 1 MiB.</p>}<div className="inline-actions"><Button type="button" size="sm" disabled={save.isPending||!file&&!preset||!!file&&file.size>1024*1024} onClick={()=>save.mutate()}>Save icon</Button><Button type="button" size="sm" variant="ghost" disabled={save.isPending} onClick={()=>{setIcon(defaultIcon);setFile(undefined);setPreset(true);save.reset()}}>Use default icon</Button></div>
  {save.isError&&<p role="alert">{getErrorMessage(save.error)}</p>}{save.isSuccess&&<p role="status">Icon saved.</p>}</section>
}
