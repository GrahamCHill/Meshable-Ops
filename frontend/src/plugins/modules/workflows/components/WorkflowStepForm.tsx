import { useState } from 'react'
import type { AdvanceInput, WorkflowRun } from '../api'
import { Button } from '../../../../components/ui/index'

const operators = { EQUALS: 'equals', NOT_EQUALS: 'does not equal', CONTAINS: 'contains', GREATER_THAN: 'is greater than', IS_SET: 'has a value' }
export function WorkflowStepForm({ run, busy, advance }: { run: WorkflowRun; busy: boolean; advance: (input: Omit<AdvanceInput, 'revision' | 'nodeId'>) => Promise<void> }) {
  const block = run.current!
  const [value, setValue] = useState(Object.prototype.hasOwnProperty.call(run.values, block.field) ? run.values[block.field] : '')
  const [note, setNote] = useState(''); const [reviewed, setReviewed] = useState(false)
  return <form className="page-stack" onSubmit={e => { e.preventDefault(); void advance({ action: block.kind === 'CONDITION' ? 'EVALUATE' : block.kind === 'REVIEW' ? 'REVIEWED' : 'CONTINUE', value, note }) }}>
    {block.kind === 'CONDITION' && <>
      <p>Continue along the True or False branch using this rule: <strong>{block.field} {operators[block.operator]}{block.operator !== 'IS_SET' && ` ${block.expected}`}</strong>.</p>
      <div className="form-field"><label htmlFor="workflow-condition-value">Value for {block.field}</label><input id="workflow-condition-value" value={value} maxLength={500} inputMode={block.operator === 'GREATER_THAN' ? 'decimal' : 'text'} disabled={busy} onChange={e => setValue(e.target.value)} /></div>
    </>}
    {block.kind === 'REVIEW' && <label className="checkbox-label"><input type="checkbox" required checked={reviewed} disabled={busy} onChange={e => setReviewed(e.target.checked)} />I have completed this review</label>}
    {block.kind === 'REVIEW' && <div className="form-field"><label htmlFor="workflow-review-note">Review note (optional)</label><textarea id="workflow-review-note" maxLength={1000} value={note} disabled={busy} onChange={e => setNote(e.target.value)} /></div>}
    <div><Button type="submit" disabled={busy || block.kind === 'REVIEW' && !reviewed}>{busy ? 'Saving…' : block.kind === 'START' ? 'Begin process' : block.kind === 'END' ? 'Finish workflow' : block.kind === 'REVIEW' ? 'Confirm review & continue' : block.kind === 'CONDITION' ? 'Evaluate & continue' : 'Continue'}</Button></div>
  </form>
}
