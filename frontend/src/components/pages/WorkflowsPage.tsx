import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ReactFlow, addEdge, useNodesState, useEdgesState, type Connection, type Edge, type ReactFlowInstance } from '@xyflow/react'
import { documentsApi, type GeneratedDocument, type EmailDocumentInput } from '../../api/documents'
import { workflowsApi, blockLabels, newBlock, type BlockKind, type Workflow, type WorkflowInput } from '../../api/workflows'
import { useDocumentAccess } from '../../api/documentAccess'
import { WorkflowStep, type StepNode } from '../workflows/WorkflowStep'
import { WorkflowInspector } from '../workflows/WorkflowInspector'
import { arrangeWorkflow } from '../workflows/arrangeWorkflow'
import { EmailDocumentDialog } from '../documents/EmailDocumentDialog'
import { getErrorMessage } from '../admin/adminUtils'
import { Button, Spinner } from '../ui'
import '@xyflow/react/dist/style.css'
import '../../styles/workflows.css'

const nodeTypes = { step: WorkflowStep }
function initialNodes(): StepNode[] {
  return (['START','END'] as const).map((kind, index) => { const block = { ...newBlock(kind, index), x: 160, y: 60 + index * 300 }; return { id: block.id, type: 'step', position: { x: block.x, y: block.y }, data: { block } } })
}
export default function WorkflowsPage() {
  const client = useQueryClient(); const [params] = useSearchParams()
  const list = useQuery({ queryKey: ['workflows'], queryFn: workflowsApi.list })
  const documents = useQuery({ queryKey: ['generated-documents'], queryFn: documentsApi.list })
  const policy = useDocumentAccess()
  const [nodes, setNodes, changeNodes] = useNodesState<StepNode>(initialNodes())
  const [edges, setEdges, changeEdges] = useEdgesState<Edge>([])
  const [instance, setInstance] = useState<ReactFlowInstance<StepNode, Edge>>()
  const [id, setId] = useState<string>(); const [revision, setRevision] = useState(0)
  const [name, setName] = useState('New workflow'); const [state, setState] = useState<WorkflowInput['state']>('DRAFT')
  const [documentIds, setDocumentIds] = useState<string[]>(params.get('attach') ? [params.get('attach')!] : [])
  const [dirty, setDirty] = useState(true); const [busy, setBusy] = useState(false)
  const [error, setError] = useState(''); const [notice, setNotice] = useState('')
  const [email, setEmail] = useState<{ document: GeneratedDocument; defaults: Partial<EmailDocumentInput> }>()
  const selected = nodes.find(node => node.selected)?.data.block
  const attached = (documents.data ?? []).filter(document => documentIds.includes(document.id))
  const load = (workflow?: Workflow) => {
    setId(workflow?.id); setRevision(workflow?.revision ?? 0); setName(workflow?.name ?? 'New workflow'); setState(workflow?.state ?? 'DRAFT')
    const requested = params.get('attach')
    setDocumentIds([...new Set([...(workflow?.documentIds ?? []), ...(requested ? [requested] : [])])])
    setNodes(workflow ? workflow.graph.nodes.map(block => ({ id: block.id, type: 'step', position: { x: block.x, y: block.y }, data: { block } })) : initialNodes())
    setEdges(workflow ? workflow.graph.edges.map(edge => ({ ...edge, sourceHandle: edge.branch, label: edge.branch === 'NEXT' ? '' : edge.branch === 'TRUE' ? 'True' : 'False' })) : [])
    setDirty(!workflow || !!requested && !workflow.documentIds.includes(requested)); setError(''); setNotice(''); requestAnimationFrame(() => void instance?.fitView({ padding: .3 }))
  }
  const add = (kind: BlockKind) => { const block = newBlock(kind, nodes.length); setNodes(current => [...current, { id: block.id, type: 'step', position: { x: block.x, y: block.y }, data: { block } }]); setDirty(true) }
  const connect = (connection: Connection) => { if (!connection.source || !connection.target || connection.source === connection.target) return; const branch = connection.sourceHandle ?? 'NEXT'; setEdges(current => addEdge({ ...connection, id: `edge-${crypto.randomUUID()}`, sourceHandle: branch, label: branch === 'NEXT' ? '' : branch === 'TRUE' ? 'True' : 'False' }, current.filter(edge => edge.source !== connection.source || edge.sourceHandle !== branch))); setDirty(true) }
  const save = async (nextState: WorkflowInput['state']) => {
    setBusy(true); setError(''); setNotice('')
    try {
      const graph: WorkflowInput['graph'] = { nodes: nodes.map(node => ({ ...node.data.block, x: node.position.x, y: node.position.y })), edges: edges.map(edge => ({ id: edge.id, source: edge.source, target: edge.target, branch: edge.sourceHandle === 'TRUE' ? 'TRUE' : edge.sourceHandle === 'FALSE' ? 'FALSE' : 'NEXT' })) }
      const saved = await workflowsApi.save(id, { name, state: nextState, revision, graph, documentIds })
      setId(saved.id); setRevision(saved.revision); setState(saved.state); setDirty(false); setNotice(nextState === 'READY' ? 'Workflow saved and graph validated. Steps are not executed automatically.' : 'Workflow saved.')
      await client.invalidateQueries({ queryKey: ['workflows'] })
    } catch (failure) { setError(getErrorMessage(failure, 'Unable to save workflow.')) }
    finally { setBusy(false) }
  }
  const detach = (documentId: string) => { setDocumentIds(current => current.filter(value => value !== documentId)); setNodes(current => current.map(node => node.data.block.documentId === documentId ? { ...node, data: { block: { ...node.data.block, documentId: undefined } } } : node)); setDirty(true) }
  return <div className="page-stack">
    <div className="page-heading"><div><h1>Workflows</h1><p>Build a process from basic steps and attach generated documents.</p></div><Link className="button button-secondary button-sm" to="/documents">Document library</Link></div>
    <p className="field-help">This version saves and validates flowcharts. Reviews, conditions, and email blocks describe the process; automation will be added later. Prepare email opens an explicit send form.</p>
    <div className="inline-actions"><Button variant="secondary" disabled={busy} onClick={() => load()}>New workflow</Button>{list.isLoading && <Spinner />}{list.isError && <p role="alert">Unable to load workflows.</p>}{!!list.data?.length && <select aria-label="Saved workflow" value={id ?? ''} disabled={busy} onChange={e => { const workflow = list.data.find(item => item.id === e.target.value); if (workflow) load(workflow) }}><option value="">Unsaved workflow</option>{list.data.map(item => <option key={item.id} value={item.id}>{item.name} · {item.state.toLowerCase()}</option>)}</select>}</div>
    <div className="form-grid"><div className="form-field"><label htmlFor="workflow-name">Workflow name</label><input id="workflow-name" maxLength={120} value={name} disabled={busy} onChange={e => { setName(e.target.value); setDirty(true) }} /></div><div className="inline-actions"><span>{state.toLowerCase()} · {dirty ? 'Unsaved changes' : 'Up to date'}</span><Button variant="secondary" disabled={busy || !name.trim()} onClick={() => void save('DRAFT')}>Save draft</Button><Button disabled={busy || !name.trim()} onClick={() => void save('READY')}>Validate & mark ready</Button>{id && <Button variant="ghost" disabled={busy} onClick={() => void save('ARCHIVED')}>Archive</Button>}</div></div>
    {error && <p role="alert" className="feedback error">{error}</p>}{notice && <p role="status">{notice}</p>}
    <div><Button variant="ghost" disabled={busy || !nodes.length} onClick={() => { setNodes(current => arrangeWorkflow(current,edges)); setDirty(true); requestAnimationFrame(() => void instance?.fitView({ padding: .2 })) }}>Arrange blocks</Button></div>
    <div className="workflow-workspace"><aside className="page-stack"><h2>Blocks</h2>{(Object.keys(blockLabels) as BlockKind[]).map(kind => <Button key={kind} variant="ghost" disabled={busy || nodes.length >= 50} onClick={() => add(kind)}>Add {blockLabels[kind].toLowerCase()}</Button>)}<h2>Documents</h2><p className="field-help">Attach PDFs from your generated-document library.</p>{documents.isLoading && <Spinner />}{documents.isError && <p role="alert">Unable to load documents.</p>}{documents.data?.length === 0 && <Link to="/documents">Generate a document</Link>}{documents.data?.map(document => <label key={document.id} className="checkbox-label"><input type="checkbox" checked={documentIds.includes(document.id)} onChange={e => { if (e.target.checked) { setDocumentIds(current => [...current, document.id]); setDirty(true) } else detach(document.id) }} />{document.name}</label>)}</aside>
      <div className="workflow-canvas"><div className="inline-actions"><Button variant="ghost" size="sm" onClick={() => void instance?.fitView({ padding: .25 })}>Fit diagram</Button><Button variant="ghost" size="sm" onClick={() => void instance?.zoomIn()}>Zoom in</Button><Button variant="ghost" size="sm" onClick={() => void instance?.zoomOut()}>Zoom out</Button></div><ReactFlow<StepNode, Edge> nodes={nodes} edges={edges} nodeTypes={nodeTypes} onInit={setInstance} onNodesChange={changes => { changeNodes(changes); if (changes.some(change => change.type === 'position' || change.type === 'remove')) setDirty(true) }} onEdgesChange={changes => { changeEdges(changes); if (changes.some(change => change.type === 'remove')) setDirty(true) }} onConnect={connect} nodeExtent={[[-10000,-10000],[10000,10000]]} fitView minZoom={.2} maxZoom={2} deleteKeyCode={['Backspace','Delete']} nodesDraggable={!busy} nodesConnectable={!busy} elementsSelectable={!busy} /></div>
      <WorkflowInspector block={selected} documents={attached} onChange={patch => { setNodes(current => current.map(node => node.id === selected?.id ? { ...node, data: { block: { ...node.data.block, ...patch } } } : node)); setDirty(true) }} onRemove={() => { setNodes(current => current.filter(node => node.id !== selected?.id)); setEdges(current => current.filter(edge => edge.source !== selected?.id && edge.target !== selected?.id)); setDirty(true) }} onEmail={() => { const document = attached.find(item => item.id === selected?.documentId); if (document && selected && policy.data?.downloadMode !== 'DISABLED') setEmail({ document, defaults: { recipient: selected.recipient, subject: selected.subject || document.name, text: selected.note || `Please find ${document.name} attached.`, protection: selected.protection } }); else setError('Select an accessible document and enable downloads before sending.') }} />
    </div>
    {email && <EmailDocumentDialog document={email.document} defaults={email.defaults} onClose={() => setEmail(undefined)} />}
  </div>
}
