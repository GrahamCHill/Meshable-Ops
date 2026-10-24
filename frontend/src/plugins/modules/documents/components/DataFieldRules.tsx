import type { FieldRule } from '../api'
import type { DocumentTemplate } from '../../../../vendor/weasycanvas/src/templates'

export function DataFieldRules({ template, rules, onChange }: { template: DocumentTemplate; rules: FieldRule[]; onChange: (rules: FieldRule[]) => void }) {
  const fields = template.apiFields ?? []
  const change = (key: string, patch: Partial<FieldRule>) => { const indexed = new Map(rules.filter(rule => fields.some(field => field.key === rule.key)).map(rule => [rule.key, rule])); indexed.set(key, { key, type: 'TEXT', inputs: [], ...indexed.get(key), ...patch }); onChange([...indexed.values()]) }
  return <details className="page-stack"><summary>Data field types and calculations ({fields.length})</summary><p className="field-help">Define named fields in the designer's Data sources or Inline data fields panels. These settings control the document form. Calculations use two numeric fields and round to two decimal places.</p>
    {fields.length === 0 && <p>No named fields yet. Add a field in the designer below.</p>}
    {fields.map(field => {
      const rule = rules.find(rule => rule.key === field.key) ?? { key: field.key, type: 'TEXT', inputs: [] }
      const numeric = fields.filter(other => other.key !== field.key && ['NUMBER', 'MONEY', 'DERIVED'].includes(rules.find(r => r.key === other.key)?.type ?? 'TEXT'))
      return <div key={field.key} className="form-grid"><div><strong>{field.label}</strong><small style={{ display: 'block' }}>{field.key}</small></div><div className="form-field"><label htmlFor={`field-type-${field.key}`}>Input type</label><select id={`field-type-${field.key}`} value={rule.type} onChange={e => change(field.key, { type: e.target.value as FieldRule['type'], operation: e.target.value === 'DERIVED' ? 'ADD' : undefined, inputs: e.target.value === 'DERIVED' ? ['', ''] : [] })}>{(['TEXT','MULTILINE','NUMBER','MONEY','DATE','EMAIL','DERIVED'] as const).map(type => <option key={type} value={type}>{type === 'DERIVED' ? 'Calculated value' : type.toLowerCase()}</option>)}</select></div>
        {rule.type === 'DERIVED' && <><div className="form-field"><label htmlFor={`field-operation-${field.key}`}>Calculation</label><select id={`field-operation-${field.key}`} value={rule.operation ?? 'ADD'} onChange={e => change(field.key, { operation: e.target.value as FieldRule['operation'] })}>{(['ADD','SUBTRACT','MULTIPLY','DIVIDE','PERCENT'] as const).map(op => <option key={op}>{op}</option>)}</select></div>{[0,1].map(index => <div key={index} className="form-field"><label htmlFor={`field-input-${field.key}-${index}`}>{index === 0 ? 'First value' : 'Second value'}</label><select id={`field-input-${field.key}-${index}`} value={rule.inputs[index] ?? ''} onChange={e => { const inputs = [...rule.inputs]; inputs[index] = e.target.value; change(field.key, { inputs }) }}><option value="">Choose a field</option>{numeric.map(other => <option key={other.key} value={other.key}>{other.label}</option>)}</select></div>)}</>}
      </div>
    })}
  </details>
}
