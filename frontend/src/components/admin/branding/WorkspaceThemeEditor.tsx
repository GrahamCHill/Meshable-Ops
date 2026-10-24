import {useState} from 'react'
import {defaultWorkspaceThemes, type WorkspacePalette, type WorkspaceThemes} from '../../../api/branding'
import {validColour} from '../../layout/brandingColours'

const fields: [keyof WorkspacePalette, string][] = [['background', 'Page background'], ['surface', 'Surface background'], ['raised', 'Raised surface'], ['text', 'Main text'], ['textSecondary', 'Secondary text'], ['link', 'Content links'], ['sidebarBackground', 'Sidebar background'], ['sidebarText', 'Sidebar text'], ['headerBackground', 'Header background'], ['headerText', 'Header text'], ['tableHeaderBackground', 'Table header background'], ['tableHeaderText', 'Table header text']]
export function WorkspaceThemeEditor({value = defaultWorkspaceThemes, onChange}: {value?: WorkspaceThemes; onChange: (value: WorkspaceThemes) => void}) {
  const [tab, setTab] = useState<'light' | 'dark'>('light')
  const key = value.switchingEnabled ? tab : 'custom', palette = value[key]
  const fallback = (value.switchingEnabled ? tab : value.fixedMode.toLowerCase()) === 'dark' ? '#181818' : '#ffffff'
  return <section className="page-stack"><h2>Workspace colours</h2>
    <label className="checkbox-label"><input type="checkbox" checked={value.switchingEnabled} onChange={e => onChange({...value, switchingEnabled: e.target.checked})}/>Allow members to switch between light and dark mode</label>
    {value.switchingEnabled ? <div className="inline-actions" role="group" aria-label="Palette to edit"><button type="button" aria-pressed={tab === 'light'} onClick={() => setTab('light')}>Light palette</button><button type="button" aria-pressed={tab === 'dark'} onClick={() => setTab('dark')}>Dark palette</button></div> : <><p>One custom palette applies to everyone. The theme switch is hidden.</p><label className="form-field">Base appearance<select value={value.fixedMode} onChange={e => onChange({...value, fixedMode: e.target.value as WorkspaceThemes['fixedMode']})}><option value="LIGHT">Light</option><option value="DARK">Dark</option></select></label></>}
    <h3>{key === 'custom' ? 'Single custom palette' : `${tab === 'light' ? 'Light' : 'Dark'} palette`}</h3>
    <div className="branding-colours">{fields.map(([field, label]) => <div key={field}><label className="checkbox-label"><input type="checkbox" checked={palette[field] != null} onChange={e => onChange({...value, [key]: {...palette, [field]: e.target.checked ? fallback : null}})}/>Custom {label.toLowerCase()}</label><div className="branding-colour-input"><input type="color" aria-label={`${label} picker`} value={validColour(palette[field]) ? palette[field] : fallback} disabled={palette[field] == null} onChange={e => onChange({...value, [key]: {...palette, [field]: e.target.value}})}/><input aria-label={label} value={palette[field] ?? ''} placeholder="Automatic" disabled={palette[field] == null} pattern="#[0-9a-fA-F]{6}" maxLength={7} onChange={e => onChange({...value, [key]: {...palette, [field]: e.target.value}})}/></div></div>)}</div>
    <p className="field-help">Unchecked colours use the base appearance. Background changes also choose contrasting automatic text; explicit text colours override it. Save branding settings to apply.</p>
  </section>
}
