import {readFile} from 'node:fs/promises'
import assert from 'node:assert/strict'
import test from 'node:test'
import ts from 'typescript'
const source=(await readFile(new URL('../src/api/projectDevelopment.ts',import.meta.url),'utf8')).replace("import api from './http'",'')
const compiled=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.ES2022,target:ts.ScriptTarget.ES2020}}).outputText
const {developmentKinds}=await import('data:text/javascript;base64,'+Buffer.from(compiled).toString('base64'))
test('repository disabling suppresses dependent tracking choices while preserving preferences',()=>{
  const preferences={repositories:false,branches:true,pullRequests:true,deployments:true}
  assert.deepEqual(developmentKinds(preferences),[])
  assert.deepEqual(developmentKinds({...preferences,repositories:true}).map(item=>item.id),['BRANCH','PULL_REQUEST','DEPLOYMENT'])
  assert.equal(preferences.branches,true)
})
test('branches requests and deployments can each be selected independently',()=>{
  assert.deepEqual(developmentKinds({repositories:true,branches:false,pullRequests:true,deployments:false}).map(item=>item.id),['PULL_REQUEST'])
  assert.deepEqual(developmentKinds({repositories:true,branches:true,pullRequests:false,deployments:true}).map(item=>item.id),['BRANCH','DEPLOYMENT'])
  assert.deepEqual(developmentKinds({repositories:true,branches:false,pullRequests:false,deployments:false}),[])
})
