import {useState} from 'react'
import {useQuery, useQueryClient} from '@tanstack/react-query'
import {workflowsApi, type WorkflowBinding, type Workflow} from '../api'
import {Button, Spinner} from '../../../../components/ui'
import {getErrorMessage} from '../../../../components/admin/adminUtils'

export function WorkflowBindingsPanel({workflows}: {workflows: Workflow[]}) {
  const client = useQueryClient()
  const actions = useQuery({queryKey: ['workflow-action-catalogue'], queryFn: workflowsApi.actions})
  const bindings = useQuery({queryKey: ['workflow-bindings'], queryFn: workflowsApi.bindings})
  const [selector, setSelector] = useState('category:external'), [workflowId, setWorkflowId] = useState('')
  const [group, setGroup] = useState(''), [search, setSearch] = useState(''), [field, setField] = useState(''), [match, setMatch] = useState('')
  const [busy, setBusy] = useState(false), [error, setError] = useState('')
  const eligible = workflows.filter(workflow => workflow.state === 'READY' && workflow.published && !workflow.graph.nodes.some(node => node.kind === 'EMAIL'))
  const save = async (row?: WorkflowBinding) => {
    setBusy(true); setError('')
    try {
      await workflowsApi.saveBinding(row?.id, row ? {...row, enabled: !row.enabled} : {selector, workflowId, enabled: true, contextField: field, contextEquals: match, revision: 0})
      await client.invalidateQueries({queryKey: ['workflow-bindings']})
    } catch (failure) { setError(getErrorMessage(failure, 'Unable to save action binding.')) }
    finally { setBusy(false) }
  }
  return <section className="workflow-bindings page-stack">
    <div className="page-heading"><div><h2>Action bindings</h2><p>Require a published process before an action can finish, across core features and installed plugins.</p></div><span className="workflow-status">{bindings.data?.filter(binding => binding.enabled).length ?? 0} enforced</span></div>
    <p className="workflow-action-notice">External deliveries and exports can use one system-wide binding. Direct API calls follow the same rule. The user completes the process, then explicitly resumes the original action.</p>
    {error && <p role="alert">{error}</p>}
    {actions.isLoading || bindings.isLoading ? <Spinner/> : (actions.isError || bindings.isError) && <p role="alert">Unable to load action bindings.</p>}
    <form className="workflow-binding-form" onSubmit={event => {event.preventDefault(); void save()}}>
      <label className="form-field">Plugin or area<select value={group} onChange={event => {setGroup(event.target.value);setSelector('');setField('');setMatch('')}}><option value="">All areas</option>{[...new Set(actions.data?.map(action => action.pluginId))].sort().map(plugin => <option key={plugin}>{plugin}</option>)}</select></label>
      <label className="form-field">Find an action<input value={search} onChange={event => {setSearch(event.target.value);setSelector('');setField('');setMatch('')}} placeholder="Email, export, transition…"/></label>
      <label className="form-field workflow-binding-wide">Action<select required value={selector} onChange={event => {setSelector(event.target.value);setField('');setMatch('')}}><option value="">Choose an action</option>{actions.data?.filter(action => (!group || action.pluginId === group) && (!search || `${action.label} ${action.id}`.toLowerCase().includes(search.toLowerCase()))).map(action => <option key={action.id} value={action.id}>{action.label}</option>)}</select></label>
      <label className="form-field workflow-binding-wide">Required workflow<select required value={workflowId} onChange={event => setWorkflowId(event.target.value)}><option value="">Choose a published workflow</option>{eligible.map(workflow => <option key={workflow.id} value={workflow.id}>{workflow.name}</option>)}</select><small>A prerequisite design must finish before external delivery. Designs with email steps cannot be bound.</small></label>
      <label className="form-field">Scope field (optional)<select value={field} onChange={event => setField(event.target.value)}><option value="">Every matching action</option>{[...new Set(['trigger.path','trigger.plugin',...(actions.data?.find(action => action.id===selector)?.fields ?? []),...(selector === 'plugin:kanban' ? ['trigger.kanban.boardId','trigger.kanban.targetColumnIds','trigger.kanban.projectId'] : [])])].map(key => <option key={key}>{key}</option>)}</select></label>
      {field && <label className="form-field">Required value<input required maxLength={500} value={match} onChange={event => setMatch(event.target.value)} placeholder={field.includes('Column') ? 'Destination column ID' : 'Matching ID or value'}/></label>}
      <div><Button type="submit" disabled={busy || !workflowId || !selector || !!field && !match.trim()}>Attach workflow</Button></div>
    </form>
    {!eligible.length && <p>No published prerequisite workflows are available. Build and publish a process with approval, audit, condition, or code steps first.</p>}
    <div className="workflow-binding-list">{bindings.data?.map(binding => <article key={binding.id}><div><strong>{workflows.find(workflow => workflow.id === binding.workflowId)?.name ?? 'Workflow'}</strong><code>{binding.selector}</code>{binding.contextField && <small>{binding.contextField} = {binding.contextEquals}</small>}</div>{binding.diagramManaged ? <small>{binding.enabled ? 'Enforced' : 'Disabled'} · Edit in Diagram & steps</small> : <Button variant="secondary" size="sm" disabled={busy} onClick={() => void save(binding)}>{binding.enabled ? 'Disable' : 'Enable'}</Button>}</article>)}</div>
  </section>
}
