import { useState } from 'react'
import type { AdvanceInput, WorkflowRun } from '../api'
import { Button } from '../../../../components/ui/index'

const operators = { EQUALS: 'equals', NOT_EQUALS: 'does not equal', CONTAINS: 'contains', GREATER_THAN: 'is greater than', IS_SET: 'has a value' }
export function WorkflowStepForm({ run, busy, advance }: { run: WorkflowRun; busy: boolean; advance: (input: Omit<AdvanceInput, 'revision' | 'nodeId'>) => Promise<void> }) {
  const block = run.current!
  const [value, setValue] = useState(Object.prototype.hasOwnProperty.call(run.values, block.field) ? run.values[block.field] : '')
  const [note, setNote] = useState(''); const [reviewed, setReviewed] = useState(false)
  const requiresNote = block.auditRequired || block.kind === 'AUDIT'
  const trusted = block.conditionSource === 'VALUE' || block.field.startsWith('trigger.')
  return <form className="page-stack" onSubmit={e => { e.preventDefault(); void advance({ action: block.kind === 'CONDITION' ? 'EVALUATE' : block.kind === 'REVIEW' ? 'REVIEWED' : 'CONTINUE', value, note }) }}>
    {block.kind === 'CONDITION' && <>
      <p>Continue along the True or False branch using this rule: <strong>{block.field} {operators[block.operator]}{block.operator !== 'IS_SET' && ` ${block.expected}`}</strong>.</p>
      <div className="form-field"><label htmlFor="workflow-condition-value">Value for {block.field}</label><input id="workflow-condition-value" value={value} maxLength={500} inputMode={block.operator === 'GREATER_THAN' ? 'decimal' : 'text'} disabled={busy || trusted} onChange={e => setValue(e.target.value)} />{trusted && <small>Read from saved workflow data. The server chooses the branch.</small>}</div>
    </>}
    {block.kind === 'REVIEW' && <label className="checkbox-label"><input type="checkbox" required checked={reviewed} disabled={busy} onChange={e => setReviewed(e.target.checked)} />I have completed this review</label>}
    {block.kind === 'CODE' && <p className="field-help">Run the saved restricted module against this process's data. It cannot use libraries, files, network calls, or system commands.</p>}
    {(block.kind === 'REVIEW' || requiresNote) && <div className="form-field"><label htmlFor="workflow-review-note">{requiresNote ? 'Required audit note' : 'Review note (optional)'}</label><textarea id="workflow-review-note" required={requiresNote} maxLength={1000} value={note} disabled={busy} onChange={e => setNote(e.target.value)} /></div>}
    {block.kind === 'REVIEW' && <div><Button type="button" variant="secondary" disabled={busy || run.canAdvance === false || !note.trim()} onClick={() => void advance({action: 'REJECT', note})}>Reject action</Button><small> A rejection reason is required.</small></div>}
    <div><Button type="submit" disabled={busy || run.canAdvance === false || requiresNote && !note.trim() || block.kind === 'REVIEW' && !reviewed}>{busy ? 'Saving…' : block.kind === 'START' ? 'Begin process' : block.kind === 'END' ? 'Finish workflow' : block.kind === 'REVIEW' ? 'Approve & continue' : block.kind === 'CODE' ? 'Run code & continue' : block.kind === 'AUDIT' ? 'Record audit & continue' : block.kind === 'CONDITION' ? 'Evaluate & continue' : 'Continue'}</Button></div>
  </form>
}
