export interface TimeZoneOption {id:string;label:string;group:string}
const common:Record<string,string>={
  UTC:'Coordinated Universal Time (UTC)',
  'Africa/Johannesburg':'South Africa Time (SAST)',
  'Africa/Lagos':'West Africa Time (WAT)',
  'Africa/Nairobi':'East Africa Time (EAT)',
  'Europe/London':'United Kingdom Time (GMT / BST)',
  'Europe/Paris':'Central European Time (CET / CEST)',
  'Europe/Helsinki':'Eastern European Time (EET / EEST)',
  'America/New_York':'Eastern Time (ET)',
  'America/Chicago':'Central Time (CT)',
  'America/Denver':'Mountain Time (MT)',
  'America/Los_Angeles':'Pacific Time (PT)',
  'America/Anchorage':'Alaska Time',
  'Pacific/Honolulu':'Hawaii Time',
  'America/Sao_Paulo':'Brasilia Time',
  'Asia/Dubai':'Gulf Standard Time (GST)',
  'Asia/Kolkata':'India Standard Time (IST)',
  'Asia/Singapore':'Singapore Time (SGT)',
  'Asia/Shanghai':'China Time',
  'Asia/Tokyo':'Japan Standard Time (JST)',
  'Australia/Perth':'Australian Western Time (AWST)',
  'Australia/Adelaide':'Australian Central Time (ACST / ACDT)',
  'Australia/Sydney':'Australian Eastern Time (AEST / AEDT)',
  'Pacific/Auckland':'New Zealand Time (NZST / NZDT)',
}

export function timeZoneOption(id:string,now=new Date()):TimeZoneOption {
  const city=id.includes('/')?id.slice(id.indexOf('/')+1).replace(/_/g,' ').replace(/\//g,' / '):''
  try {
    const name=common[id]??new Intl.DateTimeFormat('en',{timeZone:id,timeZoneName:'longGeneric'}).formatToParts(now).find(part=>part.type==='timeZoneName')?.value??id
    const rawOffset=new Intl.DateTimeFormat('en',{timeZone:id,timeZoneName:'longOffset'}).formatToParts(now).find(part=>part.type==='timeZoneName')?.value??''
    const offset=rawOffset==='GMT'?'UTC+00:00':rawOffset.replace('GMT','UTC')
    return {id,label:`${name}${city?' — '+city:''}${offset?' ('+offset+')':''}`,group:id in common?'Common timezones':id.split('/')[0]}
  } catch {return {id,label:id,group:'Current timezone'}}
}

/** Runtime identifiers follow the browser's timezone database; saved aliases are retained. */
export function timeZoneOptions(selected:string,now=new Date()):TimeZoneOption[] {
  const intl=Intl as typeof Intl&{supportedValuesOf?:(key:string)=>string[]}
  const supported=intl.supportedValuesOf?.('timeZone')??[]
  const ids=new Set([...Object.keys(common),...supported,Intl.DateTimeFormat().resolvedOptions().timeZone,selected].filter(Boolean))
  return [...ids].map(id=>timeZoneOption(id,now)).sort((a,b)=>(a.group==='Common timezones'?0:1)-(b.group==='Common timezones'?0:1)||a.group.localeCompare(b.group)||a.label.localeCompare(b.label))
}
