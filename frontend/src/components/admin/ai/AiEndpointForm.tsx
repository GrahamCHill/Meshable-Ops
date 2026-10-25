import { useId } from 'react'
import type { AiEndpointInput, AiProvider, AiSettings } from '../../../api/ai'
import { AiModelPicker } from './AiModelPicker'

const labels: Record<AiProvider, string> = { OPENAI_COMPATIBLE: 'OpenAI-compatible', OLLAMA: 'Ollama', AZURE_OPENAI: 'Azure OpenAI', ANTHROPIC: 'Anthropic', LOCAL: 'Internal text matcher' }
const presets = [
  { name: 'OpenAI', provider: 'OPENAI_COMPATIBLE', baseUrl: 'https://api.openai.com/v1' },
  { name: 'Groq', provider: 'OPENAI_COMPATIBLE', baseUrl: 'https://api.groq.com/openai/v1' },
  { name: 'Ollama', provider: 'OLLAMA', baseUrl: 'https://ollama.example.com/v1' },
  { name: 'Azure OpenAI', provider: 'AZURE_OPENAI', baseUrl: '' },
  { name: 'Anthropic', provider: 'ANTHROPIC', baseUrl: 'https://api.anthropic.com/v1' },
] as const

export function AiEndpointForm({ kind, source, value, configured, readOnly, onChange }: {
  kind: 'chat' | 'embeddings'; source: AiSettings['source']; value: AiEndpointInput; configured: boolean; readOnly: boolean; onChange: (value: AiEndpointInput) => void
}) {
  const id = useId()
  const update = (patch: Partial<AiEndpointInput>) => onChange({ ...value, ...patch })
  const choices: AiProvider[] = kind === 'chat' ? ['OPENAI_COMPATIBLE', 'OLLAMA', 'AZURE_OPENAI', 'ANTHROPIC'] : ['LOCAL', 'OPENAI_COMPATIBLE', 'OLLAMA', 'AZURE_OPENAI']
  return <section className="page-stack">
    <h2 className="card-title">{kind === 'chat' ? 'Answer generation' : 'Search embeddings'}</h2>
    {!readOnly && <div className="inline-actions" aria-label={`${kind} presets`}>{presets.filter(preset => kind === 'chat' || !['Groq', 'Anthropic'].includes(preset.name)).map(preset => <button type="button" className="utility-button" key={preset.name} onClick={() => onChange({ ...value, provider: preset.provider, baseUrl: preset.baseUrl, model: '', apiKey: '', clearApiKey: false, sendDimensions: preset.provider !== 'OLLAMA' })}>{preset.name}</button>)}</div>}
    <div className="form-grid">
      <div className="form-field"><label htmlFor={`${id}-provider`}>Provider</label><select id={`${id}-provider`} disabled={readOnly} value={value.provider} onChange={event => update({ provider: event.target.value as AiProvider, apiKey: '', clearApiKey: false })}>{choices.map(provider => <option key={provider} value={provider}>{labels[provider]}</option>)}</select></div>
      {value.provider !== 'LOCAL' && <>
        <AiModelPicker kind={kind} source={source} value={value} configured={configured} readOnly={readOnly} onChange={update} />
        <div className="form-field full-width"><label htmlFor={`${id}-url`}>API base URL</label><input id={`${id}-url`} type="url" maxLength={512} readOnly={readOnly} placeholder={value.provider === 'AZURE_OPENAI' ? 'https://resource.openai.azure.com/openai/v1' : 'https://provider.example/v1'} value={value.baseUrl} onChange={event => update({ baseUrl: event.target.value, apiKey: '', clearApiKey: false })} /><small>Include the API prefix, without a request route or credentials. Azure uses its /openai/v1 endpoint.</small></div>
        <div className="form-field full-width"><label htmlFor={`${id}-key`}>API key</label><input id={`${id}-key`} type="password" maxLength={4096} disabled={readOnly} value={value.apiKey} onChange={event => update({ apiKey: event.target.value, clearApiKey: false })} autoComplete="new-password" placeholder={configured ? 'Configured; leave blank to retain' : value.provider === 'OLLAMA' ? 'Optional for local Ollama' : 'Enter an API key'} /><small>{configured ? 'A key is configured. Saved keys are never returned to the browser.' : 'No key is configured.'} {value.provider === 'OLLAMA' && 'The server must be able to reach your Ollama installation.'}</small></div>
        {!readOnly && configured && <label className="checkbox-label full-width"><input type="checkbox" checked={value.clearApiKey} onChange={event => update({ clearApiKey: event.target.checked, apiKey: '' })} /> Clear saved key</label>}
        {kind === 'embeddings' && <label className="checkbox-label full-width"><input type="checkbox" disabled={readOnly} checked={value.sendDimensions} onChange={event => update({ sendDimensions: event.target.checked })} /> Request 1536 dimensions from the provider</label>}
      </>}
    </div>
    {kind === 'embeddings' && <p className="field-help">The PostgreSQL index uses 1536 dimensions. External models must return that size. The internal matcher runs locally and uses text features rather than a language model. Reindex after changing the embedding provider or model.</p>}
  </section>
}
