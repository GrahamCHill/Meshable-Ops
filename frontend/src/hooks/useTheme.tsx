import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import {useQuery} from '@tanstack/react-query'
import {brandingApi, defaultWorkspaceThemes} from '../api/branding'
import {workspaceThemeVariables} from '../lib/brandingTheme'

type ThemeMode = 'dark' | 'light'

interface ThemeContextValue {
  mode: ThemeMode
  setMode: (mode: ThemeMode) => void
  toggleMode: () => void
  switchingEnabled: boolean
}

const ThemeContext = createContext<ThemeContextValue | null>(null)
const STORAGE_KEY = 'app-theme-mode'

function resolveInitialMode(): ThemeMode {
  if (typeof window === 'undefined') return 'dark'
  const saved = window.localStorage.getItem(STORAGE_KEY)
  if (saved === 'dark' || saved === 'light') return saved
  return window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark'
}

export function ThemeProvider({ children }: Readonly<{ children: ReactNode }>) {
  const [preferredMode, setPreferredMode] = useState<ThemeMode>(() => resolveInitialMode())
  const {data} = useQuery({queryKey: ['branding'], queryFn: brandingApi.current, staleTime: 60_000, refetchInterval: 60_000, retry: false})
  const themes = data?.settings?.themes ?? defaultWorkspaceThemes
  const switchingEnabled = themes.switchingEnabled
  const mode: ThemeMode = switchingEnabled ? preferredMode : themes.fixedMode === 'DARK' ? 'dark' : 'light'

  useEffect(() => {
    document.documentElement.dataset.theme = mode
    document.documentElement.style.colorScheme = mode
    window.localStorage.setItem(STORAGE_KEY, preferredMode)
    const variables = workspaceThemeVariables(data?.settings, mode)
    for (const [key, value] of Object.entries(variables)) document.documentElement.style.setProperty(key, value)
    return () => { for (const key of Object.keys(variables)) document.documentElement.style.removeProperty(key) }
  }, [mode, preferredMode, data?.settings])

  const value = useMemo<ThemeContextValue>(() => ({
    mode,
    switchingEnabled,
    setMode: value => { if (switchingEnabled) setPreferredMode(value) },
    toggleMode: () => { if (switchingEnabled) setPreferredMode(current => current === 'dark' ? 'light' : 'dark') },
  }), [mode, switchingEnabled])

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export function useTheme() {
  const context = useContext(ThemeContext)
  if (!context) {
    throw new Error('useTheme must be used within ThemeProvider')
  }
  return context
}
