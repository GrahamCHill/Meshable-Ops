import {readFile} from 'node:fs/promises'
import assert from 'node:assert/strict'
import test from 'node:test'
import ts from 'typescript'

const source=await readFile(new URL('../src/lib/timezones.ts',import.meta.url),'utf8')
const compiled=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.ES2022,target:ts.ScriptTarget.ES2020}}).outputText
const {timeZoneOption,timeZoneOptions}=await import('data:text/javascript;base64,'+Buffer.from(compiled).toString('base64'))

test('familiar names show city and current offset, including daylight saving',()=>{
  const winter=timeZoneOption('America/New_York',new Date('2026-01-15T12:00:00Z'))
  const summer=timeZoneOption('America/New_York',new Date('2026-07-15T12:00:00Z'))
  assert.match(winter.label,/Eastern Time.*New York.*UTC-05:00/)
  assert.match(summer.label,/Eastern Time.*New York.*UTC-04:00/)
  assert.match(timeZoneOption('Africa/Johannesburg').label,/South Africa Time \(SAST\).*UTC\+02:00/)
  assert.match(timeZoneOption('UTC').label,/Coordinated Universal Time.*UTC\+00:00/)
})

test('saved aliases and uncommon timezones remain selectable without duplicates',()=>{
  const options=timeZoneOptions('US/Eastern')
  assert.ok(options.some(option=>option.id==='US/Eastern'))
  assert.ok(options.some(option=>option.id==='Pacific/Chatham'))
  assert.equal(new Set(options.map(option=>option.id)).size,options.length)
  assert.equal(options[0].group,'Common timezones')
})
