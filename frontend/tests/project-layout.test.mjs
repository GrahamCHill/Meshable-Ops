import {readFile} from 'node:fs/promises'
import test from 'node:test'
import assert from 'node:assert/strict'
import ts from 'typescript'
const source=await readFile(new URL('../src/components/projects/projectLayout.ts',import.meta.url),'utf8')
const compiled=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.ES2022,target:ts.ScriptTarget.ES2020}}).outputText
const {readProjectLayout}=await import('data:text/javascript;base64,'+Buffer.from(compiled).toString('base64'))
test('layouts are private to each user and inactive categories always start collapsed',()=>{
  const cookie='project-layout-alex='+encodeURIComponent(JSON.stringify({collapsed:['proposals'],clientId:'client-one',category:'active',tags:['tag-one']}))
  assert.deepEqual(readProjectLayout(cookie,'alex','inactive'),{collapsed:['proposals','inactive'],clientId:'client-one',category:'active',tags:['tag-one']})
  assert.deepEqual(readProjectLayout(cookie,'jamie','inactive'),{collapsed:['inactive'],clientId:'',category:'',tags:[]})
})
test('malformed cookies and invalid identifiers cannot alter layout or inject cookie values',()=>{
  assert.deepEqual(readProjectLayout('project-layout-alex=invalid','alex',''),{collapsed:[],clientId:'',category:'',tags:[]})
  const raw={collapsed:['active','<script>'],clientId:'x; Path=/',category:'../../admin',tags:['valid','bad;cookie']}
  assert.deepEqual(readProjectLayout('project-layout-alex='+encodeURIComponent(JSON.stringify(raw)),'alex',''),{collapsed:['active'],clientId:'',category:'',tags:['valid']})
})
