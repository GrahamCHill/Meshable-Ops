import { useEffect, useId, useState } from 'react'
import { aiApi, type AiEndpointInput, type AiModelCheck, type AiModels, type AiSettings } from '../../../api/ai'
import { getErrorMessage } from '../adminUtils'
import { Button } from '../../ui'

export function AiModelPicker({ kind, source, value, configured, readOnly, onChange }: {
  kind: 'chat' | 'embeddings'; source: AiSettings['source']; value: AiEndpointInput; configured: boolean
  readOnly: boolean; onChange: (patch: Partial<AiEndpointInput>) => void
}) {
  const id = useId()
  const { provider, baseUrl, model, apiKey, clearApiKey, sendDimensions } = value
  const [models, setModels] = useState<AiModels | null>(null)
  const [loading, setLoading] = useState(false)
  const [listError, setListError] = useState('')
  const [check, setCheck] = useState<AiModelCheck | null>(null)
  const [checking, setChecking] = useState(false)
  const [checkError, setCheckError] = useState('')
  const [manual, setManual] = useState(false)
  const [refreshModels, setRefreshModels] = useState(0)
  const [refreshCheck, setRefreshCheck] = useState(0)
  let validUrl = false
  try {
    const url = new URL(baseUrl.trim())
    validUrl = ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password && !url.search && !url.hash
  } catch { /* Wait until the endpoint is complete. */ }
  const ready = validUrl && ((!clearApiKey && (!!apiKey.trim() || configured)) || provider === 'OLLAMA')

  useEffect(() => {
    setModels(null); setListError(''); setLoading(false); setManual(false)
    if (!ready) return
    let active = true
    const controller = new AbortController()
    const timer = window.setTimeout(() => {
      setLoading(true)
      // Keep write-only keys and request errors out of the shared query/mutation cache.
      void aiApi.models(kind, { source, endpoint: { provider, baseUrl, apiKey, clearApiKey, model: '', sendDimensions: true } }, controller.signal)
        .then(result => { if (active) setModels(result) })
        .catch(failure => { if (active) setListError(getErrorMessage(failure, 'Unable to load models. Enter a model name manually or retry.')) })
        .finally(() => { if (active) setLoading(false) })
    }, 800)
    return () => { active = false; window.clearTimeout(timer); controller.abort() }
  }, [kind, source, provider, baseUrl, apiKey, clearApiKey, configured, ready, refreshModels])

  useEffect(() => {
    setCheck(null); setCheckError(''); setChecking(false)
    if (!ready || !model.trim()) return
    let active = true
    const controller = new AbortController()
    const timer = window.setTimeout(() => {
      setChecking(true)
      void aiApi.check(kind, { source, endpoint: { provider, baseUrl, apiKey, clearApiKey, model, sendDimensions } }, controller.signal)
        .then(result => { if (active) setCheck(result) })
        .catch(failure => { if (active) setCheckError(getErrorMessage(failure, 'Unable to verify this model right now. Check the connection and retry.')) })
        .finally(() => { if (active) setChecking(false) })
    }, 1000)
    return () => { active = false; window.clearTimeout(timer); controller.abort() }
  }, [kind, source, provider, baseUrl, model, apiKey, clearApiKey, sendDimensions, configured, ready, refreshCheck])

  const choices = models?.models ?? []
  const hasChoices = choices.length > 0 && !readOnly
  const custom = manual || (!!model && !choices.some(choice => choice.id === model))
  const title = provider === 'AZURE_OPENAI' ? 'Model or deployment name' : 'Model name'
  const suggestedSendDimensions = check?.suggestedSendDimensions
  const modelInput = (inputId: string) => <input id={inputId} maxLength={200} readOnly={readOnly} value={model} onChange={event => onChange({ model: event.target.value })} autoComplete="off" />
  return <div className="form-field full-width">
    <label htmlFor={`${id}-model`}>{title}</label>
    {hasChoices ? <>
      <select id={`${id}-model`} value={custom ? 'manual' : model ? `model:${model}` : ''} onChange={event => {
        const customName = event.target.value === 'manual'
        setManual(customName)
        onChange({ model: customName ? '' : event.target.value.slice(6) })
      }}>
        <option value="" disabled>Choose an available model</option>
        {choices.map(choice => <option key={choice.id} value={`model:${choice.id}`}>{choice.name === choice.id ? choice.id : `${choice.name} (${choice.id})`}</option>)}
        <option value="manual">Enter a model name manually</option>
      </select>
      {custom && <><label htmlFor={`${id}-custom`}>Custom {provider === 'AZURE_OPENAI' ? 'deployment or model' : 'model'} name</label>{modelInput(`${id}-custom`)}</>}
    </> : modelInput(`${id}-model`)}
    <div className="inline-actions">
      <Button type="button" variant="ghost" disabled={!ready || loading} loading={loading} onClick={() => setRefreshModels(count => count + 1)}>{models ? 'Refresh models' : 'Load models'}</Button>
      <Button type="button" variant="ghost" disabled={!ready || !model.trim() || checking} loading={checking} onClick={() => setRefreshCheck(count => count + 1)}>Check selected model</Button>
    </div>
    {!ready && <small>Enter the API URL and an API key to load available models. A saved key can be used; local Ollama does not require one.</small>}
    {loading && <small role="status">Loading available models…</small>}
    {models && <small>{models.message}{models.truncated && ' The list is limited; enter a model name manually if it is missing.'}</small>}
    {listError && <small role="status">{listError}</small>}
    <small>Model checks run automatically after selection and use short test text. No storage documents are sent.</small>
    {checking && <p role="status" className="field-help">{kind === 'embeddings' ? 'Checking whether this model returns 1,536 dimensions…' : 'Checking whether this model can answer a chat request…'}</p>}
    {check && <div role="status" className={`feedback ${check.status === 'COMPATIBLE' ? 'success' : 'warning'}`}>
      <p>{check.message}</p>
      {check.status === 'SETTING_CHANGE_REQUIRED' && typeof suggestedSendDimensions === 'boolean' && !readOnly && <Button type="button" variant="secondary" onClick={() => onChange({ sendDimensions: suggestedSendDimensions })}>Apply suggested dimensions setting</Button>}
    </div>}
    {checkError && <p role="status" className="feedback warning">Compatibility could not be verified. {checkError}</p>}
  </div>
}
