import { Handle, Position, type Node, type NodeProps } from '@xyflow/react'
import { blockLabels, type WorkflowBlock } from '../api'
import {Play, FileText, GitBranch, CheckCheck, Code2, ShieldCheck, Send, CircleCheck, Webhook, Merge} from 'lucide-react'
const icons = {START: Play, WEBHOOK: Webhook, JOIN: Merge, DOCUMENT: FileText, CONDITION: GitBranch, REVIEW: CheckCheck, CODE: Code2, AUDIT: ShieldCheck, EMAIL: Send, END: CircleCheck}
export type StepNode = Node<{ block: WorkflowBlock }, 'step'>
export function WorkflowStep({ data, selected }: NodeProps<StepNode>) {
  const block = data.block
  const Icon = icons[block.kind]
  return <div className={`workflow-step workflow-step-${block.kind.toLowerCase()}${selected ? ' is-selected' : ''}`}>
    {!['START','WEBHOOK'].includes(block.kind) && <Handle type="target" position={Position.Top} />}
    <div className="workflow-step-kind"><Icon size={14}/><small>{blockLabels[block.kind]}</small>{block.auditRequired && <ShieldCheck size={13} aria-label="Audit required"/>}</div><strong>{block.label}</strong>
    {block.kind === 'START' && <span>{block.actionSelector ? `Plugin trigger · ${block.actionSelector}` : 'Manual start'}</span>}
    {block.kind === 'WEBHOOK' && <span>{block.webhookEvent || 'Configure incoming webhook'}</span>}
    {block.kind === 'JOIN' && <span>{block.joinInputs === 'ANY' ? 'First input' : 'Wait for every input'} · {block.joinRule === 'ALL_TRUE' ? 'All true' : block.joinRule === 'ANY_TRUE' ? 'Any true' : 'Continue'}</span>}
    {block.kind === 'DOCUMENT'  && <span>{block.documentId ? 'PDF attached' : 'Choose a document'}</span>}
    {block.kind === 'EMAIL' && <span>{block.recipient || 'Configure recipient'} · {block.protection === 'PASSWORD' ? 'Protected PDF' : 'Plain PDF'}</span>}
    {block.kind === 'REVIEW' && <span>{block.allowInitiatorReview === false ? 'Different approver required' : 'Initiator may approve'} · {block.reviewerIds?.length ?? 0} assigned</span>}
    {block.kind === 'CODE' && <span>Restricted module · no external libraries</span>}
    {block.kind === 'AUDIT' && <span>Required note · actor and time recorded</span>}
    {block.kind === 'END' && <span>{block.endOutcome === 'DENY' ? 'Reject action' : 'Allow action'}</span>}
    {(block.kind === 'CONDITION' || block.kind === 'JOIN' && block.joinRule && block.joinRule !== 'PASS') ? <><span className="workflow-branch-label">True / False</span><Handle id="TRUE" type="source" position={Position.Bottom} style={{ left: '30%' }} /><Handle id="FALSE" type="source" position={Position.Bottom} style={{ left: '70%' }} /></> : block.kind !== 'END' && <Handle id="NEXT" type="source" position={Position.Bottom} />}
  </div>
}
