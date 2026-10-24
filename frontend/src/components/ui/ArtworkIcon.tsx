import {Folder,Briefcase,Code,Building2,Layers,Rocket,Target,ChartNoAxesColumn,Globe,CalendarDays,Users,Shield,Settings,Plug,Database,Mail,Workflow,Search,FileText,LayoutGrid,Cloud,Lock,Activity} from 'lucide-react'
import type {Artwork} from '../../api/artwork'
export const artworkIcons={folder:Folder,briefcase:Briefcase,code:Code,building:Building2,layers:Layers,rocket:Rocket,target:Target,chart:ChartNoAxesColumn,globe:Globe,calendar:CalendarDays,users:Users,shield:Shield,settings:Settings,plug:Plug,database:Database,mail:Mail,workflow:Workflow,search:Search,file:FileText,layout:LayoutGrid,cloud:Cloud,lock:Lock,activity:Activity}
export function ArtworkIcon({value,imageUrl,size=32}:{value?:Pick<Artwork,'icon'|'imageVersion'>;imageUrl?:string;size?:number}) {
  const Icon=artworkIcons[value?.icon as keyof typeof artworkIcons]??Folder
  return imageUrl?<img className="workspace-artwork" src={imageUrl} alt="" width={size} height={size}/>:<Icon size={size} strokeWidth={1.6} aria-hidden="true"/>
}
