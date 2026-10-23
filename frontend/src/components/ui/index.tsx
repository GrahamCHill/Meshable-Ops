import { type ReactNode, type ButtonHTMLAttributes, forwardRef } from 'react'
import clsx from 'clsx'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
  size?: 'sm' | 'md' | 'lg'
  loading?: boolean
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ variant = 'primary', size = 'md', loading, children, className, disabled, ...props }, ref) => (
    <button ref={ref} className={clsx('button', `button-${variant}`, `button-${size}`, className)}
      disabled={disabled || loading} aria-busy={loading || undefined} {...props}>
      {loading ? 'Working…' : children}
    </button>
  )
)
Button.displayName = 'Button'

export function Card({ children, className, style }: { children: ReactNode; className?: string; style?: React.CSSProperties }) {
  return <div className={clsx('content-section', className)} style={style}>{children}</div>
}

export function Badge({ children, color = 'default' }: { children: ReactNode; color?: 'default' | 'success' | 'warning' | 'danger' | 'accent' }) {
  return <span className={`status-label status-${color}`}>{children}</span>
}

export function Spinner({ size: _size }: { size?: number }) {
  return <span className="loading-indicator" role="status" aria-label="Loading">Loading…</span>
}

export function StatusDot({ status }: { status: string }) {
  return <span className={`status-label status-${status === 'UP' ? 'success' : 'danger'}`}>{status}</span>
}
