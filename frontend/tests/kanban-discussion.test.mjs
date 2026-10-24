import {readFile} from 'node:fs/promises'
import assert from 'node:assert/strict'
import test from 'node:test'
import ts from 'typescript'
const source=await readFile(new URL('../src/plugins/modules/kanban/mentionText.ts',import.meta.url),'utf8')
const compiled=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.ES2022,target:ts.ScriptTarget.ES2020}}).outputText
const {mentionTrigger,serializeMentions,mentionPattern}=await import('data:text/javascript;base64,'+Buffer.from(compiled).toString('base64'))
const id='11111111-1111-4111-8111-111111111111'
test('typing @ searches at the caret without consuming the rest of the comment',()=>{
  assert.deepEqual(mentionTrigger('Please @Sam review this',11),{start:7,end:11,query:'Sam'})
  assert.equal(mentionTrigger('email@example.test',18),undefined)
})
test('selected mentions serialize stable identities and unselected text stays ordinary text',()=>{
  const result=serializeMentions('Thanks @Alex Morgan, please ask @Sam.',[{id,label:'Alex Morgan'}])
  assert.equal(result,`Thanks @[Alex Morgan](user:${id}), please ask @Sam.`)
  assert.equal([...result.matchAll(new RegExp(mentionPattern.source,'g'))][0][2],id)
})
test('duplicate names and regexp characters retain the selected identity',()=>{
  const label='Alex (alex+work@example.test)'
  assert.equal(serializeMentions('@'+label+' thanks',[{id,label}]),`@[${label}](user:${id}) thanks`)
  assert.equal(serializeMentions('@Alexander thanks',[{id,label:'Alex'}]),'@Alexander thanks')
})
