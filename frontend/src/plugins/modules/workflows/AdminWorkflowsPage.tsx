import {useAccess} from '../../../api/access'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ReactFlow, Background, Controls, MiniMap, MarkerType, addEdge, useNodesState, useEdgesState, type Connection, type Edge, type ReactFlowInstance } from '@xyflow/react'
import { documentsApi, type GeneratedDocument, type EmailDocumentInput } from '../documents/api'
import { workflowsApi, blockLabels, newBlock, type Workflow, type WorkflowInput } from './api'
import { useDocumentAccess } from '../../../api/documentAccess'
import { WorkflowStep, type StepNode } from './components/WorkflowStep'
import { WorkflowInspector } from './components/WorkflowInspector'
import {WorkflowModulePalette, workflowModuleMime, type WorkflowModule} from './components/WorkflowModulePalette'
import {WorkflowBindingsPanel} from './components/WorkflowBindingsPanel'
import { arrangeWorkflow } from './components/arrangeWorkflow'
import { EmailDocumentDialog } from '../documents/components/EmailDocumentDialog'
import { getErrorMessage } from '../../../components/admin/adminUtils'
import { Button, Spinner } from '../../../components/ui/index'
import { AdminPageHeader } from '../../../components/admin/AdminPageHeader'
import { useAuthStore } from '../../../hooks/useAuthStore'
import '@xyflow/react/dist/style.css'
import './workflows.css'

const nodeTypes = { step: WorkflowStep }
function initialNodes(): StepNode[] {
  return (['START','END'] as const).map((kind, index) => { const block = { ...newBlock(kind, index), x: 160, y: 60 + index * 300 }; return { id: block.id, type: 'step', position: { x: block.x, y: block.y }, data: { block } } })
}
export default function AdminWorkflowsPage() {
  const access = useAccess(); const hasDocuments = access.can('documents')
  const client = useQueryClient(); const [params] = useSearchParams()
  const user = useAuthStore(state => state.user)
  const list = useQuery({ queryKey: ['admin-workflows', user?.id], queryFn: workflowsApi.list })
  const actions = useQuery({queryKey: ['workflow-action-catalogue'], queryFn: workflowsApi.actions})
  const webhookEndpoints = useQuery({queryKey:['workflow-webhook-endpoints'],queryFn:workflowsApi.webhookEndpoints})
  const reviewers = useQuery({queryKey: ['workflow-reviewers'], queryFn: workflowsApi.reviewers})
  const [tab, setTab] = useState<'design' | 'bindings'>('design')
  const documents = useQuery({ queryKey: ['generated-documents'], queryFn: documentsApi.list, enabled: hasDocuments })
  const policy = useDocumentAccess()
  const [nodes, setNodes, changeNodes] = useNodesState<StepNode>(initialNodes())
  const [edges, setEdges, changeEdges] = useEdgesState<Edge>([])
  const [instance, setInstance] = useState<ReactFlowInstance<StepNode, Edge>>()
  const [id, setId] = useState<string>(); const [revision, setRevision] = useState(0)
  const [published, setPublished] = useState(false)
  const [name, setName] = useState('New workflow'); const [state, setState] = useState<WorkflowInput['state']>('DRAFT')
  const [documentIds, setDocumentIds] = useState<string[]>(params.get('attach') ? [params.get('attach')!] : [])
  const [dirty, setDirty] = useState(true); const [busy, setBusy] = useState(false)
  const [error, setError] = useState(''); const [notice, setNotice] = useState('')
  const [email, setEmail] = useState<{ document: GeneratedDocument; defaults: Partial<EmailDocumentInput> }>()
  const selected = nodes.find(node => node.selected)?.data.block
  const attached = (documents.data ?? []).filter(document => documentIds.includes(document.id))
  const load = (workflow?: Workflow) => {
    setPublished(workflow?.published ?? false); setId(workflow?.id); setRevision(workflow?.revision ?? 0); setName(workflow?.name ?? 'New workflow'); setState(workflow?.state ?? 'DRAFT')
    const requested = params.get('attach')
    setDocumentIds([...new Set([...(workflow?.documentIds ?? []), ...(requested ? [requested] : [])])])
    setNodes(workflow ? workflow.graph.nodes.map(block => ({ id: block.id, type: 'step', position: { x: block.x, y: block.y }, data: { block } })) : initialNodes())
    setEdges(workflow ? workflow.graph.edges.map(edge => ({ ...edge, sourceHandle: edge.branch, label: edge.branch === 'NEXT' ? '' : edge.branch === 'TRUE' ? 'True' : 'False' })) : [])
    setDirty(!workflow || !!requested && !workflow.documentIds.includes(requested)); setError(''); setNotice(''); requestAnimationFrame(() => void instance?.fitView({ padding: .3 }))
  }
  const add = (module: WorkflowModule, position?: {x: number; y: number}) => {
    if (busy || !Object.prototype.hasOwnProperty.call(blockLabels,module.kind) || module.actionSelector && !actions.data?.some(action => action.id === module.actionSelector)) return
    const existing = module.actionSelector ? nodes.find(node => node.data.block.kind === 'START' && node.data.block.actionSelector) ?? nodes.find(node => node.data.block.kind === 'START') : undefined
    if (nodes.length >= 50 && !existing) {setError('Use at most 50 steps in a workflow.');return}
    const action = actions.data?.find(action => action.id === module.actionSelector)
    const block = {...(existing?.data.block ?? newBlock(module.kind,nodes.length)), ...(action ? {actionSelector:action.id,label:action.label.slice(0,80),contextField:'',contextEquals:''} : {})}
    const nextPosition = position ?? existing?.position ?? {x:block.x,y:block.y}
    const point = {x:Math.max(-10000,Math.min(10000,nextPosition.x)),y:Math.max(-10000,Math.min(10000,nextPosition.y))}
    setNodes(current => [...current.filter(node => node.id !== existing?.id).map(node => ({...node,selected:false})),{id:block.id,type:'step',position:point,data:{block},selected:true}])
    setDirty(true);setError('');if(action)setNotice('Trigger connected. Validate and publish to enforce this process before the selected action.')
  }
  const connect = (connection: Connection) => { if (!connection.source || !connection.target || connection.source === connection.target) return; const branch = connection.sourceHandle ?? 'NEXT'; setEdges(current => addEdge({ ...connection, id: `edge-${crypto.randomUUID()}`, sourceHandle: branch, label: branch === 'NEXT' ? '' : branch === 'TRUE' ? 'True' : 'False' }, current.filter(edge => !(edge.source === connection.source && edge.target === connection.target && edge.sourceHandle === branch)))); setDirty(true) }
  const save = async (nextState: WorkflowInput['state']) => {
    setBusy(true); setError(''); setNotice('')
    try {
      const graph: WorkflowInput['graph'] = { nodes: nodes.map(node => ({ ...node.data.block, x: node.position.x, y: node.position.y })), edges: edges.map(edge => ({ id: edge.id, source: edge.source, target: edge.target, branch: edge.sourceHandle === 'TRUE' ? 'TRUE' : edge.sourceHandle === 'FALSE' ? 'FALSE' : 'NEXT' })) }
      const saved = await workflowsApi.save(id, { name, state: nextState, revision, graph, documentIds, published: nextState === 'READY' && published })
      setPublished(saved.published); setId(saved.id); setRevision(saved.revision); setState(saved.state); setDirty(false); setNotice(saved.published ? 'Workflow published. Connected triggers are enforced and members can start it from Workflows.' : 'Workflow saved privately.')
      await Promise.all([client.invalidateQueries({ queryKey: ['admin-workflows'] }), client.invalidateQueries({ queryKey: ['workflow-catalogue'] }), client.invalidateQueries({queryKey: ['workflow-bindings']})])
    } catch (failure) { setError(getErrorMessage(failure, 'Unable to save workflow.')) }
    finally { setBusy(false) }
  }
  const detach = (documentId: string) => { setDocumentIds(current => current.filter(value => value !== documentId)); setNodes(current => current.map(node => node.data.block.documentId === documentId ? { ...node, data: { block: { ...node.data.block, documentId: undefined } } } : node)); setDirty(true) }
  return <div className="page-stack workflow-builder">
    <AdminPageHeader title="Workflow builder" description="Design, validate, and publish the processes members follow in Workflows." actions={<div className="inline-actions"><Link className="button button-secondary button-sm" to="/workflows">Run workflows</Link>{hasDocuments && <Link className="button button-ghost button-sm" to="/documents">Document library</Link>}</div>} />
    <nav className="workflow-tabs" aria-label="Workflow builder sections"><button type="button" aria-current={tab === 'design' ? 'page' : undefined} onClick={() => setTab('design')}>Diagram & steps</button><button type="button" aria-current={tab === 'bindings' ? 'page' : undefined} onClick={() => setTab('bindings')}>Action bindings</button></nav>
    {tab === 'bindings' && <WorkflowBindingsPanel workflows={list.data ?? []}/>}
    <div hidden={tab !== 'design'} className="page-stack">
    <p className="field-help">Connect steps, configure approvals and audit rules, then publish. Bind the finished process to a button's server action in Action bindings. Existing runs retain their saved design.</p>
    <div className="inline-actions"><Button variant="secondary" disabled={busy} onClick={() => load()}>New workflow</Button>{list.isLoading && <Spinner />}{list.isError && <p role="alert">Unable to load workflows.</p>}{!!list.data?.length && <select aria-label="Saved workflow" value={id ?? ''} disabled={busy} onChange={e => { const workflow = list.data.find(item => item.id === e.target.value); if (workflow) load(workflow) }}><option value="">Unsaved workflow</option>{list.data.map(item => <option key={item.id} value={item.id}>{item.name} · {item.state.toLowerCase()}</option>)}</select>}</div>
    <div className="form-grid"><div className="form-field"><label htmlFor="workflow-name">Workflow name</label><input id="workflow-name" maxLength={120} value={name} disabled={busy} onChange={e => { setName(e.target.value); setDirty(true) }} /></div><div className="inline-actions"><span>{state.toLowerCase()} · {dirty ? 'Unsaved changes' : 'Up to date'}</span><Button variant="secondary" disabled={busy || !name.trim()} onClick={() => void save('DRAFT')}>Save draft</Button><Button disabled={busy || !name.trim()} onClick={() => void save('READY')}>Validate & mark ready</Button>{id && <Button variant="ghost" disabled={busy} onClick={() => void save('ARCHIVED')}>Archive</Button>}</div></div>
    <label className="checkbox-label"><input type="checkbox" checked={published} disabled={busy} onChange={e => { setPublished(e.target.checked); setDirty(true) }} />Publish for members when saved as ready</label>
    <p className="field-help">Save draft or Archive removes it from the member catalogue and pauses existing runs. To validate privately, leave publishing off.</p>
    {error && <p role="alert" className="feedback error">{error}</p>}{notice && <p role="status">{notice}</p>}
    <div><Button variant="ghost" disabled={busy || !nodes.length} onClick={() => { setNodes(current => arrangeWorkflow(current,edges)); setDirty(true); requestAnimationFrame(() => void instance?.fitView({ padding: .2 })) }}>Arrange blocks</Button></div>
    <div className="workflow-workspace"><aside className="page-stack"><WorkflowModulePalette actions={actions.data ?? []} hasDocuments={hasDocuments} disabled={busy} onAdd={add}/>{actions.isError && <p role="alert">Unable to load plugin modules.</p>}{hasDocuments ? <><h2>Documents</h2><p className="field-help">Attach PDFs from your generated-document library.</p>{documents.isLoading && <Spinner />}{documents.isError && <p role="alert">Unable to load documents.</p>}{documents.data?.length === 0 && <Link to="/documents">Generate a document</Link>}{documents.data?.map(document => <label key={document.id} className="checkbox-label"><input type="checkbox" checked={documentIds.includes(document.id)} onChange={e => { if (e.target.checked) { setDocumentIds(current => [...current, document.id]); setDirty(true) } else detach(document.id) }} />{document.name}</label>)}</> : <p className="field-help">Enable the Document generator plugin and grant document access to add PDFs and email steps.</p>}</aside>
      <div className="workflow-canvas" onDragOverCapture={event => {if(!busy && event.dataTransfer.types.includes(workflowModuleMime)){event.preventDefault();event.dataTransfer.dropEffect='copy'}}} onDropCapture={event => {
        if(busy || !instance || !event.dataTransfer.types.includes(workflowModuleMime))return
        event.preventDefault()
        try {const module=JSON.parse(event.dataTransfer.getData(workflowModuleMime)) as WorkflowModule;add(module,instance.screenToFlowPosition({x:event.clientX,y:event.clientY}))} catch {setError('Unable to add that module. Choose one from the palette.')}
      }}><div className="workflow-canvas-toolbar"><span>{nodes.length} steps · {edges.length} transitions</span><div className="inline-actions"><Button variant="ghost" size="sm" onClick={() => void instance?.fitView({ padding: .25 })}>Fit diagram</Button><span className="workflow-status">{state.toLowerCase()}{dirty ? ' · edited' : ''}</span></div></div><ReactFlow<StepNode, Edge> nodes={nodes} edges={edges} nodeTypes={nodeTypes} defaultEdgeOptions={{type: 'smoothstep', markerEnd: {type: MarkerType.ArrowClosed}, style: {strokeWidth: 2}}} onInit={setInstance} onNodesChange={changes => { changeNodes(changes); if (changes.some(change => change.type === 'position' || change.type === 'remove')) setDirty(true) }} onEdgesChange={changes => { changeEdges(changes); if (changes.some(change => change.type === 'remove')) setDirty(true) }} onConnect={connect} nodeExtent={[[-10000,-10000],[10000,10000]]} fitView minZoom={.2} maxZoom={2} deleteKeyCode={['Backspace','Delete']} nodesDraggable={!busy} nodesConnectable={!busy} elementsSelectable={!busy} colorMode="system"><Background gap={24}/><Controls/><MiniMap pannable zoomable/></ReactFlow></div>
      <WorkflowInspector block={selected} documents={attached} reviewers={reviewers.data ?? []} actions={actions.data ?? []} webhookEndpoints={webhookEndpoints.data ?? []} triggerAction={actions.data?.find(action => action.id===nodes.find(node => node.data.block.kind==='START')?.data.block.actionSelector)} onChange={patch => { setNodes(current => current.map(node => node.id === selected?.id ? { ...node, data: { block: { ...node.data.block, ...patch } } } : node)); setDirty(true) }} onRemove={() => { setNodes(current => current.filter(node => node.id !== selected?.id)); setEdges(current => current.filter(edge => edge.source !== selected?.id && edge.target !== selected?.id)); setDirty(true) }} onEmail={() => { const document = attached.find(item => item.id === selected?.documentId); if (document && selected && policy.data?.downloadMode !== 'DISABLED') setEmail({ document, defaults: { recipient: selected.recipient, subject: selected.subject || document.name, text: selected.note || `Please find ${document.name} attached.`, protection: selected.protection } }); else setError('Select an accessible document and enable downloads before sending.') }} />
    </div>
    </div>
    {email && <EmailDocumentDialog document={email.document} defaults={email.defaults} onClose={() => setEmail(undefined)} />}
  </div>
}
