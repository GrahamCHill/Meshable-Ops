import {useState, type DragEvent} from 'react'
import {GripVertical, PlugZap} from 'lucide-react'
import {blockLabels, type BlockKind, type WorkflowActionDescriptor} from '../api'

export const workflowModuleMime = 'application/meshable-workflow-module'
export type WorkflowModule = {kind: BlockKind; actionSelector?: string}
export function WorkflowModulePalette({actions, hasDocuments, disabled, onAdd}: {actions: WorkflowActionDescriptor[]; hasDocuments: boolean; disabled: boolean; onAdd: (module: WorkflowModule) => void}) {
  const [search,setSearch]=useState(''), [routes,setRoutes]=useState(false)
  const [section,setSection]=useState<'steps'|'plugins'>('steps')
  const startDrag=(event: DragEvent, module: WorkflowModule) => {
    event.dataTransfer.setData(workflowModuleMime,JSON.stringify(module));event.dataTransfer.effectAllowed='copy'
  }
  const filtered=actions.filter(action => (routes || !action.id.startsWith('route:')) && `${action.label} ${action.pluginId} ${action.id}`.toLowerCase().includes(search.toLowerCase()))
  return <div className="workflow-module-palette page-stack">
    <h2>Modules</h2><nav className="workflow-palette-tabs" aria-label="Module library"><button type="button" aria-pressed={section==='steps'} onClick={() => setSection('steps')}>Steps</button><button type="button" aria-pressed={section==='plugins'} onClick={() => setSection('plugins')}>Plugins</button></nav>
    {section==='steps' && <><p className="field-help">Drag onto the canvas, or click to add.</p>
    {(Object.keys(blockLabels) as BlockKind[]).filter(kind => hasDocuments || !['DOCUMENT','EMAIL'].includes(kind)).map(kind => <button type="button" className="workflow-module" key={kind} draggable={!disabled} disabled={disabled} onDragStart={event => startDrag(event,{kind})} onClick={() => onAdd({kind})}><GripVertical size={14}/>{blockLabels[kind]}</button>)}
    </>}
    {section==='plugins' && <><h2>Plugin triggers</h2><p className="field-help">Drag onto empty canvas to connect the start step, or click to connect. Publishing enforces the process before that action.</p>
    <input aria-label="Find plugin module" placeholder="Find plugin or export…" value={search} onChange={event => setSearch(event.target.value)}/>
    <label className="checkbox-label"><input type="checkbox" checked={routes} onChange={event => setRoutes(event.target.checked)}/>Individual server routes</label>
    <div className="workflow-plugin-modules">{[...new Set(filtered.map(action => action.pluginId))].sort().map(plugin => <section key={plugin}><h3>{plugin}</h3>{filtered.filter(action => action.pluginId===plugin).map(action => <button type="button" className="workflow-module workflow-plugin-module" key={action.id} title={action.id} draggable={!disabled} disabled={disabled} onDragStart={event => startDrag(event,{kind:'START',actionSelector:action.id})} onClick={() => onAdd({kind:'START',actionSelector:action.id})}><PlugZap size={14}/><span>{action.label}{action.external && <small>Delivery / export</small>}</span><GripVertical size={12}/></button>)}</section>)}</div>
    {!filtered.length && <p className="field-help">No matching modules.</p>}
    </>}
  </div>
}
