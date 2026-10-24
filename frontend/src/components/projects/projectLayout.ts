export interface ProjectLayout {collapsed:string[];clientId:string;category:string;tags:string[]}
export function readProjectLayout(cookie:string,userId:string,inactiveId:string):ProjectLayout {
  const empty={collapsed:inactiveId?[inactiveId]:[],clientId:'',category:'',tags:[]}
  try {
    const raw=cookie.split('; ').find(row=>row.startsWith('project-layout-'+userId+'='))?.split('=').slice(1).join('=')
    if(!raw)return empty
    const parsed=JSON.parse(decodeURIComponent(raw))
    const valid=(value:unknown):value is string=>typeof value==='string'&&/^[A-Za-z0-9_-]{0,40}$/.test(value)
    return {collapsed:[...new Set([...(Array.isArray(parsed.collapsed)?parsed.collapsed.filter(valid).slice(0,20):[]),...(inactiveId?[inactiveId]:[])])],clientId:valid(parsed.clientId)?parsed.clientId:'',category:valid(parsed.category)?parsed.category:'',tags:Array.isArray(parsed.tags)?parsed.tags.filter(valid).slice(0,20):[]}
  }catch{return empty}
}
export function writeProjectLayout(userId:string,layout:ProjectLayout) {
  document.cookie='project-layout-'+userId+'='+encodeURIComponent(JSON.stringify(layout))+'; Path=/; Max-Age=31536000; SameSite=Strict'+(location.protocol==='https:'?'; Secure':'')
}
