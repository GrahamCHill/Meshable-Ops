import {defaultWorkspaceThemes, type BrandingSettings} from '../api/branding'
import {sidebarForeground, validColour} from '../components/layout/brandingColours'

export function workspaceThemeVariables(settings: BrandingSettings | undefined, mode: 'light' | 'dark'): Record<string, string> {
  const themes = settings?.themes ?? defaultWorkspaceThemes
  const p = themes.switchingEnabled ? themes[mode] : themes.custom
  const colour = (value: string | null | undefined) => validColour(value) ? value : undefined
  const background = colour(p.background)
  const surface = colour(p.surface) ?? background
  const text = colour(p.text) ?? (background ? sidebarForeground(background) : undefined)
  const header = colour(p.headerBackground)
  const table = colour(p.tableHeaderBackground)
  const sidebar = colour(p.sidebarBackground) ?? colour(settings?.sidebarBackgroundColor)
  const values = {
    '--color-bg': background, '--color-surface': surface, '--color-surface-2': colour(p.raised) ?? surface,
    '--color-text': text, '--color-text-secondary': colour(p.textSecondary) ?? text, '--color-text-tertiary': colour(p.textSecondary) ?? text,
    '--color-accent': text, '--color-accent-hover': text,
    '--brand-sidebar-background': sidebar, '--brand-sidebar-text': colour(p.sidebarText) ?? colour(settings?.sidebarTextColor) ?? (sidebar ? sidebarForeground(sidebar) : undefined),
    '--brand-link-colour': colour(p.link) ?? colour(settings?.linkColor),
    '--color-header-background': header, '--color-header-text': colour(p.headerText) ?? (header ? sidebarForeground(header) : undefined),
    '--color-table-header-background': table, '--color-table-header-text': colour(p.tableHeaderText) ?? (table ? sidebarForeground(table) : undefined),
  }
  return Object.fromEntries(Object.entries(values).filter((entry): entry is [string, string] => entry[1] !== undefined))
}
