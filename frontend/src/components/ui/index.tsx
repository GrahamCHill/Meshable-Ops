import { type ReactNode, type ButtonHTMLAttributes, forwardRef } from 'react'
import clsx from 'clsx'

// ─── Button ──────────────────────────────────────────────────────────────────

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
  size?: 'sm' | 'md' | 'lg'
  loading?: boolean
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ variant = 'primary', size = 'md', loading, children, className, disabled, ...props }, ref) => {
    const base = 'inline-flex items-center justify-center gap-2 font-medium rounded-md transition-all duration-150 focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed'
    const variants: Record<string, string> = {
      primary: 'bg-[var(--color-accent)] text-white hover:bg-[var(--color-accent-hover)] focus-visible:ring-[var(--color-accent)]',
      secondary: 'bg-[var(--color-surface-2)] text-[var(--color-text)] border border-[var(--color-border)] hover:border-[var(--color-border-hover)]',
      ghost: 'text-[var(--color-text-secondary)] hover:text-[var(--color-text)] hover:bg-[var(--color-surface-2)]',
      danger: 'bg-[var(--color-danger)] text-white hover:opacity-90',
    }
    const sizes: Record<string, string> = { sm: 'px-3 py-1.5 text-sm', md: 'px-4 py-2 text-sm', lg: 'px-5 py-2.5 text-base' }

    return (
      <button ref={ref} className={clsx(base, variants[variant], sizes[size], className)}
        disabled={disabled || loading} {...props}>
        {loading && <span style={{ width: 14, height: 14, border: '2px solid currentColor', borderTopColor: 'transparent', borderRadius: '50%', display: 'inline-block', animation: 'spin 0.6s linear infinite' }} />}
        {children}
      </button>
    )
  }
)
Button.displayName = 'Button'

// ─── Card ────────────────────────────────────────────────────────────────────

export function Card({ children, className, style }: { children: ReactNode; className?: string; style?: React.CSSProperties }) {
  return (
    <div className={className} style={{
      background: 'var(--color-surface)',
      border: '1px solid var(--color-border)',
      borderRadius: 'var(--radius-md)',
      padding: '1.25rem',
      ...style,
    }}>
      {children}
    </div>
  )
}

// ─── Badge ───────────────────────────────────────────────────────────────────

export function Badge({ children, color = 'default' }: { children: ReactNode; color?: 'default' | 'success' | 'warning' | 'danger' | 'accent' }) {
  const styles: Record<string, React.CSSProperties> = {
    default: { background: 'var(--color-surface-2)', color: 'var(--color-text-secondary)' },
    success: { background: 'rgba(34,197,94,0.12)', color: '#22c55e' },
    warning: { background: 'rgba(245,158,11,0.12)', color: '#f59e0b' },
    danger:  { background: 'rgba(239,68,68,0.12)',  color: '#ef4444' },
    accent:  { background: 'var(--color-accent-muted)', color: 'var(--color-accent)' },
  }
  return (
    <span style={{
      display: 'inline-block', padding: '2px 8px',
      borderRadius: 999, fontSize: '0.75rem', fontWeight: 500,
      ...styles[color],
    }}>
      {children}
    </span>
  )
}

// ─── Spinner ─────────────────────────────────────────────────────────────────

export function Spinner({ size = 20 }: { size?: number }) {
  return (
    <span style={{
      width: size, height: size,
      border: '2px solid var(--color-border)',
      borderTopColor: 'var(--color-accent)',
      borderRadius: '50%',
      display: 'inline-block',
      animation: 'spin 0.6s linear infinite',
    }} />
  )
}

// ─── StatusDot ───────────────────────────────────────────────────────────────

export function StatusDot({ status }: { status: string }) {
  const up = status === 'UP'
  return (
    <span style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: '0.85rem' }}>
      <span style={{
        width: 8, height: 8, borderRadius: '50%',
        background: up ? 'var(--color-success)' : 'var(--color-danger)',
        boxShadow: up ? '0 0 6px var(--color-success)' : '0 0 6px var(--color-danger)',
      }} />
      <span style={{ color: up ? 'var(--color-success)' : 'var(--color-danger)', fontWeight: 500 }}>{status}</span>
    </span>
  )
}
