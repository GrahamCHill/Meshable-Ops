import {readFile} from 'node:fs/promises'
import test from 'node:test'
import assert from 'node:assert/strict'
import ts from 'typescript'
const source=await readFile(new URL('../src/components/projects/reportCoverFiles.ts',import.meta.url),'utf8')
const compiled=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.ES2022,target:ts.ScriptTarget.ES2020}}).outputText
const {coverImageName,coverImageReferences,readCoverHtml}=await import('data:text/javascript;base64,'+Buffer.from(compiled).toString('base64'))

test('relative image and CSS references match uploaded filenames without replacing branding tokens',()=>{
  const html='<img src="images/Banner.PNG"><img src="{{siteLogo}}"><img src="{{clientLogo}}"><img src="data:image/png;base64,abc"><div style="background:url(\'art/my%20cover.webp\')"></div>'
  assert.deepEqual(coverImageReferences(html),['banner.png','my cover.webp'])
  assert.equal(coverImageName('Banner.PNG'),'banner.png')
  assert.throws(()=>coverImageName('../banner.png'))
})
test('HTML import preserves source placeholders and rejects oversized or incorrectly encoded files',async()=>{
  const source='<h1>{{siteName}} — {{companyName}}</h1>'
  const file={name:'cover.html',size:source.length,arrayBuffer:async()=>new TextEncoder().encode(source).buffer}
  assert.equal(await readCoverHtml(file),source)
  await assert.rejects(readCoverHtml({...file,name:'cover.svg'}))
  await assert.rejects(readCoverHtml({...file,size:120001}))
  await assert.rejects(readCoverHtml({...file,arrayBuffer:async()=>new Uint8Array([255]).buffer}))
})
