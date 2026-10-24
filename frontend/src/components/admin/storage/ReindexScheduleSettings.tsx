import type { StorageSettingsState } from './useStorageSettings'
import { Button, Card } from '../../ui'
import { formatDate } from '../adminUtils'
import {TimeZoneSelect} from '../../ui/TimeZoneSelect'

const days = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

export function ReindexScheduleSettings({ state }: { state: StorageSettingsState }) {
  const { fieldId, form, settings, update, reindex, dirty } = state
  const scheduled = form.vectorReindexSchedule !== 'INTERVAL'
  const changed = form.vectorReindexSchedule === 'ON_CHANGE'
  const nextRun = settings.data?.vectorReindexNextRunAt
  const nextLabel = nextRun ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short', timeZone: settings.data?.vectorReindexTimeZone }).format(new Date(nextRun)) : 'Waiting for file changes'
  return <Card>
    <h2 className="card-title">Search indexing job</h2>
    <p className="field-help">Keep folder metadata and enabled document content up to date. This job works without an AI provider.</p>
    <label className="checkbox-label"><input type="checkbox" checked={form.vectorReindexEnabled} onChange={event => update({ vectorReindexEnabled: event.target.checked })} /> Enable scheduled indexing</label>
    <fieldset disabled={!form.vectorReindexEnabled} className="form-grid" style={{ marginTop: 16 }}>
      <div className="form-field"><label htmlFor={`${fieldId}-schedule`}>Run</label><select id={`${fieldId}-schedule`} value={form.vectorReindexSchedule} onChange={event => update({ vectorReindexSchedule: event.target.value as typeof form.vectorReindexSchedule })}><option value="DAILY">Every day</option><option value="WEEKLY">Every week</option><option value="ON_CHANGE">At the next scheduled time when files change</option><option value="INTERVAL">At a fixed interval</option></select></div>
      {form.vectorReindexSchedule === 'WEEKLY' && <div className="form-field"><label htmlFor={`${fieldId}-day`}>Day</label><select id={`${fieldId}-day`} value={form.vectorReindexDayOfWeek} onChange={event => update({ vectorReindexDayOfWeek: event.target.value })}>{days.map(day => <option key={day} value={day}>{day[0] + day.slice(1).toLowerCase()}</option>)}</select></div>}
      {scheduled ? <><div className="form-field"><label htmlFor={`${fieldId}-time`}>{changed ? 'Run after a change at' : 'Time'}</label><input id={`${fieldId}-time`} type="time" required value={form.vectorReindexTime} onChange={event => update({ vectorReindexTime: event.target.value })} /></div><TimeZoneSelect id={`${fieldId}-zone`} value={form.vectorReindexTimeZone} onChange={timeZone => update({vectorReindexTimeZone: timeZone})} /></> : <div className="form-field"><label htmlFor={`${fieldId}-interval`}>Interval in minutes</label><input id={`${fieldId}-interval`} type="number" min={15} max={10080} required value={form.vectorReindexIntervalMinutes} onChange={event => update({ vectorReindexIntervalMinutes: Number(event.target.value) })} /></div>}
    </fieldset>
    {changed && <p className="field-help">Checks shared storage every minute for new, changed, or removed files. Changes queue one run at the next chosen time; choose 00:00 for the next midnight.</p>}
    <p className="field-help">Save to apply this schedule. The application server must be running for jobs to execute.</p>
    <p className="field-help">Next run: {settings.data?.vectorReindexEnabled ? nextLabel : 'Disabled'}{settings.data?.vectorReindexEnabled && ` · ${settings.data.vectorReindexTimeZone}`}<br />{settings.data?.vectorReindexPendingChanges && 'File changes are queued for indexing.'}<br />Last run: {formatDate(settings.data?.lastVectorReindexAt)}<br />{settings.data?.lastVectorReindexStatus ?? 'No runs recorded yet.'}</p>
    <Button type="button" variant="secondary" disabled={dirty} loading={reindex.isPending} onClick={() => reindex.mutate()}>Reindex now</Button>
    {dirty && <p className="field-help">Save or discard changes before running the index.</p>}
  </Card>
}
