export const coverImageLimit=1024*1024
export function coverImageName(name:string) {
  if(!/^[A-Za-z0-9][A-Za-z0-9._ -]{0,119}$/.test(name)||name!==name.trim()||name==='.'||name==='..')throw new Error('Use image filenames of up to 120 letters, numbers, spaces, dots, underscores or hyphens.')
  return name.toLowerCase()
}
export function coverImageReferences(html:string) {
  const references=[...html.matchAll(/<img\b[^>]*\bsrc\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))/gi)].map(match=>match[1]??match[2]??match[3])
  references.push(...[...html.matchAll(/url\s*\(\s*(?:"([^"]*)"|'([^']*)'|([^)]*))\s*\)/gi)].map(match=>match[1]??match[2]??match[3]))
  return [...new Set(references.map(value=>value.trim()).filter(value=>value&&!value.startsWith('data:')&&!/^\{\{\s*siteLogo\s*\}\}$/.test(value)).map(value=>{
    try {return decodeURIComponent(value.split('/').pop()??value).toLowerCase()}catch{return value.toLowerCase()}
  }))]
}
export async function readCoverHtml(file:File) {
  if(!/\.html?$/i.test(file.name)||file.size>120000)throw new Error('Choose an HTML file containing up to 30,000 characters.')
  const html=new TextDecoder('utf-8',{fatal:true}).decode(await file.arrayBuffer())
  if(!html.trim()||html.length>30000)throw new Error('Use a UTF-8 HTML template of up to 30,000 characters.')
  return html
}
export async function readCoverImages(files:File[],existing:Record<string,string>) {
  const result={...existing}
  if(files.reduce((sum,file)=>sum+file.size,0)>coverImageLimit)throw new Error('Upload at most 1 MiB of images at a time.')
  const names=files.map(file=>coverImageName(file.name))
  if(new Set(names).size!==names.length)throw new Error('Choose unique image filenames, ignoring case.')
  for(const [index,file] of files.entries()) {
    const extension=file.name.split('.').pop()?.toLowerCase()
    const type=extension==='png'?'image/png':extension==='jpg'||extension==='jpeg'?'image/jpeg':extension==='webp'?'image/webp':''
    if(!type||!file.size||file.size>coverImageLimit)throw new Error('Choose PNG, JPEG or WebP images up to 1 MiB.')
    result[names[index]]=await new Promise<string>((resolve,reject)=>{const reader=new FileReader();reader.onload=()=>resolve(String(reader.result));reader.onerror=()=>reject(new Error('Unable to read an image.'));reader.readAsDataURL(new Blob([file],{type}))})
  }
  if(Object.keys(result).length>8||Object.values(result).reduce((sum,value)=>sum+value.length,0)>1_400_000)throw new Error('Use up to eight cover images totalling at most 1 MiB. Remove or optimise existing images first.')
  return result
}
export function coverImageBlob(value:string) {
  const [header,encoded]=value.split(',');const raw=atob(encoded)
  return new Blob([Uint8Array.from(raw,char=>char.charCodeAt(0))],{type:header.slice(5).split(';')[0]})
}
