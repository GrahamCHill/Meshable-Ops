import {Moon, Sun} from 'lucide-react'
import {useTheme} from '../../hooks/useTheme'
import '../../styles/theme-toggle.css'

export function ThemeToggle() {
  const {mode, toggleMode} = useTheme()

  return <button type="button" className="theme-toggle" role="switch" aria-checked={mode === 'dark'}
    title={`Switch to ${mode === 'dark' ? 'light' : 'dark'} mode`} onClick={toggleMode}>
    <span>Dark mode</span>
    <span className="theme-toggle-track" aria-hidden="true">
      <span className="theme-toggle-thumb">
        <Sun className="theme-toggle-sun" size={16} strokeWidth={1.8}/>
        <Moon className="theme-toggle-moon" size={16} strokeWidth={1.8}/>
      </span>
    </span>
  </button>
}
