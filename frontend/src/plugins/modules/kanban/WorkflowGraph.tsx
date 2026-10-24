import {useEffect, useState} from 'react'
import {ReactFlow, Background, Controls, MarkerType, Position, useNodesState, type Connection, type Node} from '@xyflow/react'
import type {Person} from '../../../api/projects'
import {Button} from '../../../components/ui'
import {Permission} from './ColumnSettings'
import type {BoardColumn, BoardWorkflow, BoardTransition} from './api'
import '@xyflow/react/dist/style.css'

export const emptyWorkflow: BoardWorkflow = {enabled:false,transitions:[],positions:{}}
export function WorkflowGraph({columns,value,onChange,people,disabled=false}: {columns:BoardColumn[];value:BoardWorkflow;onChange:(value:BoardWorkflow)=>void;people:Person[];disabled?:boolean}) {
  const makeNodes = ():Node[] => columns.map((column,index)=>({id:column.id,position:value.positions[column.id]??{x:60+(index%3)*280,y:60+Math.floor(index/3)*200},sourcePosition:Position.Right,targetPosition:Position.Left,
    deletable:false,data:{label:column.title},className:'kanban-workflow-node state-'+(column.status??'TODO').toLowerCase()}))
  const [nodes,setNodes,onNodesChange]=useNodesState(makeNodes())
  const [selected,setSelected]=useState(''),[from,setFrom]=useState(columns[0]?.id??''),[to,setTo]=useState(columns[1]?.id??'')
  useEffect(()=>{setNodes(makeNodes())},[columns,value.positions]) // Saved positions are part of the configuration draft.
  const edge=value.transitions.find(item=>item.id===selected)
  const patch=(next:Partial<BoardWorkflow>)=>onChange({...value,...next})
  const connect=(connection:Connection|{source:string;target:string})=>{
    if(disabled||!connection.source||!connection.target||connection.source===connection.target||value.transitions.some(item=>item.source===connection.source&&item.target===connection.target))return
    const transition:BoardTransition={id:crypto.randomUUID(),source:connection.source,target:connection.target,name:'Move to '+(columns.find(column=>column.id===connection.target)?.title??'column'),permission:{mode:'EDITORS',userIds:[]}}
    patch({transitions:[...value.transitions,transition]});setSelected(transition.id)
  }
  const updateEdge=(fields:Partial<BoardTransition>)=>patch({transitions:value.transitions.map(item=>item.id===selected?{...item,...fields}:item)})
  return <div className="page-stack kanban-workflow-editor">
    <label className="checkbox-label"><input type="checkbox" disabled={disabled} checked={value.enabled} onChange={event=>patch({enabled:event.target.checked})}/>Enforce this workflow</label>
    <p className="field-help">Connect a column's right handle to another column's left handle. Select an arrow to name it and set who may use it. Column entry and exit permissions also apply. With enforcement off, existing column permissions control movement.</p>
    <div className="kanban-workflow-layout"><div className="kanban-workflow-canvas" aria-label="Column transition graph">
      <ReactFlow nodes={nodes} edges={value.transitions.map(item=>({...item,label:item.name,type:'smoothstep',selected:item.id===selected,markerEnd:{type:MarkerType.ArrowClosed},style:{stroke:item.id===selected?'#579dff':'#8796ad',strokeWidth:2},labelStyle:{fill:'var(--color-text)'},labelBgStyle:{fill:'var(--color-surface-2)'}}))}
        onNodesChange={onNodesChange} onNodeDragStop={(_,node)=>patch({positions:{...value.positions,[node.id]:node.position}})} onConnect={connect}
        onEdgeClick={(_,item)=>setSelected(item.id)} onPaneClick={()=>setSelected('')} nodesDraggable={!disabled} nodesConnectable={!disabled} edgesFocusable elementsSelectable
        onEdgesDelete={removed=>patch({transitions:value.transitions.filter(item=>!removed.some(edge=>edge.id===item.id))})} deleteKeyCode={disabled?null:['Delete','Backspace']}
        fitView minZoom={.25} maxZoom={2} colorMode="system"><Background/><Controls/></ReactFlow>
    </div><aside className="page-stack kanban-workflow-inspector">
      <h3>{edge?'Transition':'Add a transition'}</h3>
      {edge?<><label className="form-field">Transition name<input maxLength={80} required value={edge.name} onChange={event=>updateEdge({name:event.target.value})}/></label>
        <p>{columns.find(item=>item.id===edge.source)?.title} → {columns.find(item=>item.id===edge.target)?.title}</p>
        <Permission label="Who can use this transition" value={edge.permission} people={people} onChange={permission=>updateEdge({permission})}/>
        <Button type="button" variant="ghost" onClick={()=>{patch({transitions:value.transitions.filter(item=>item.id!==edge.id)});setSelected('')}}>Remove transition</Button></>:
        <><label className="form-field">From column<select value={from} onChange={event=>setFrom(event.target.value)}>{columns.map(column=><option value={column.id} key={column.id}>{column.title}</option>)}</select></label>
        <label className="form-field">To column<select value={to} onChange={event=>setTo(event.target.value)}>{columns.map(column=><option value={column.id} key={column.id}>{column.title}</option>)}</select></label>
        <Button type="button" variant="secondary" disabled={disabled||from===to||!from||!to} onClick={()=>connect({source:from,target:to})}>Add transition</Button></>}
      <Button type="button" variant="ghost" disabled={disabled} onClick={()=>patch({positions:Object.fromEntries(columns.map((column,index)=>[column.id,{x:60+(index%3)*280,y:60+Math.floor(index/3)*200}]))})}>Arrange columns</Button>
    </aside></div>
    <div className="kanban-transition-list">{value.transitions.map(item=><button type="button" key={item.id} onClick={()=>setSelected(item.id)} aria-pressed={selected===item.id}>{columns.find(column=>column.id===item.source)?.title} → {columns.find(column=>column.id===item.target)?.title}: {item.name}</button>)}</div>
    {value.enabled&&!value.transitions.length&&<p role="status">No transitions are connected. Tickets can be reordered in their column, but cannot leave it until you add a transition.</p>}
  </div>
}
