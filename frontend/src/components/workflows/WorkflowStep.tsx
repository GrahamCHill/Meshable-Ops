import { Handle, Position, type Node, type NodeProps } from '@xyflow/react'
import { blockLabels, type WorkflowBlock } from '../../api/workflows'
export type StepNode = Node<{ block: WorkflowBlock }, 'step'>
export function WorkflowStep({ data, selected }: NodeProps<StepNode>) {
  const block = data.block
  return <div className={`workflow-step${selected ? ' is-selected' : ''}`}>
    {block.kind !== 'START' && <Handle type="target" position={Position.Top} />}
    <small>{blockLabels[block.kind]}</small><strong>{block.label}</strong>
    {block.kind === 'DOCUMENT' && <span>{block.documentId ? 'PDF attached' : 'Choose a document'}</span>}
    {block.kind === 'EMAIL' && <span>{block.recipient || 'Configure recipient'} · {block.protection === 'PASSWORD' ? 'Protected PDF' : 'Plain PDF'}</span>}
    {block.kind === 'CONDITION' ? <><span className="workflow-branch-label">True / False</span><Handle id="TRUE" type="source" position={Position.Bottom} style={{ left: '30%' }} /><Handle id="FALSE" type="source" position={Position.Bottom} style={{ left: '70%' }} /></> : block.kind !== 'END' && <Handle id="NEXT" type="source" position={Position.Bottom} />}
  </div>
}
