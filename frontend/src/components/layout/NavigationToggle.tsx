import {PanelLeftClose,PanelLeftOpen} from 'lucide-react'

export function NavigationToggle({collapsed,onToggle}:{collapsed:boolean;onToggle:()=>void}) {
  const Icon=collapsed?PanelLeftOpen:PanelLeftClose
  const label=collapsed?'Expand navigation':'Collapse navigation'
  return <button type="button" className="navigation-toggle" aria-label={label} title={label}
    aria-expanded={!collapsed} aria-controls="app-navigation" onClick={onToggle}>
    <Icon size={18} strokeWidth={1.7} aria-hidden="true"/>
    <span className="navigation-toggle-label">{collapsed?'Menu':'Close menu'}</span>
  </button>
}
