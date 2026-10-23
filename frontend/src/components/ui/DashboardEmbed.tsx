import { useDashboardEmbed, type DashboardEmbedOptions } from '../../hooks/useDashboardEmbed'
import { Button } from './index'

interface DashboardEmbedProps extends DashboardEmbedOptions {
  height?: string | number
}

export function DashboardEmbed({ height = 600, ...options }: DashboardEmbedProps) {
  const { containerRef, status, errorMsg, retry } = useDashboardEmbed(options)
  return (
    <div style={{ position: 'relative', width: '100%', height: typeof height === 'number' ? `${height}px` : height, minHeight: 0 }}>
      {status === 'loading' && (
        <div style={{
          position: 'absolute',
          inset: 0,
          zIndex: 1,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          background: 'var(--color-surface)',

          gap: 12,
          color: 'var(--color-text-secondary)',
          fontSize: '0.875rem',
        }}>

          Loading dashboard...
        </div>
      )}
      {status === 'error' && (
        <div role="alert" style={{ position: 'absolute', inset: 0, padding: '2rem', textAlign: 'center', background: 'var(--color-surface)' }}>
          <div style={{ color: 'var(--color-danger)', fontWeight: 600, marginBottom: 8 }}>Dashboard failed to load</div>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.8rem', fontFamily: 'monospace' }}>{errorMsg}</div>
          <Button variant="secondary" style={{ marginTop: 16 }} onClick={retry}>Try again</Button>
        </div>
      )}
      <div
        ref={containerRef}
        style={{
          width: '100%',
          height: '100%',

          overflow: 'hidden',

          visibility: status === 'error' ? 'hidden' : 'visible',
        }}
      />
    </div>
  )
}
