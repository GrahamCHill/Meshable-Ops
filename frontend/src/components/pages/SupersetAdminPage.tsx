import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ArrowLeft, Workflow } from 'lucide-react'
import { Card } from '../ui'
import { authApi } from '../../api/client'
import { useTheme } from '../../hooks/useTheme'
import { applySupersetTheme } from '../../lib/supersetTheme'

export function SupersetAdminPage() {
  const navigate = useNavigate()
  const [isPreparingSession, setIsPreparingSession] = useState(true)
  const supersetEntryUrl = '/dashboard/list/'
  const iframeRef = useRef<HTMLIFrameElement | null>(null)
  const observerRef = useRef<MutationObserver | null>(null)
  const { mode } = useTheme()

  const syncIframeTheme = () => {
    const iframe = iframeRef.current
    if (!iframe) return

    let doc: Document | null = null
    try {
      doc = iframe.contentDocument
    } catch {
      return
    }
    if (!doc) return

    applySupersetTheme(doc, mode)
  }

  useEffect(() => {
    let cancelled = false

    authApi.refreshSessionCookie()
      .catch(() => {
        if (!cancelled) {
          navigate('/login', { replace: true })
        }
      })
      .finally(() => {
        if (!cancelled) {
          setIsPreparingSession(false)
        }
      })

    return () => {
      cancelled = true
    }
  }, [navigate])

  useEffect(() => {
    syncIframeTheme()
  }, [mode, isPreparingSession])

  useEffect(() => () => observerRef.current?.disconnect(), [])

  return (
    <div style={{ width: '100%', maxWidth: 'none', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '1rem', flexWrap: 'wrap' }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
            <Workflow size={18} color="var(--color-accent)" />
            <h1 style={{ fontSize: '1.4rem', fontWeight: 700 }}>Superset Graph Builder</h1>
          </div>
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.875rem' }}>
            Use the embedded Superset workspace for datasets, charts, and dashboard authoring. The wider admin tools stay on the main admin page.
          </p>
        </div>
        <Link
          to="/admin"
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 8,
            padding: '0.7rem 0.95rem',
            borderRadius: 'var(--radius-md)',
            background: 'var(--color-surface)',
            border: '1px solid var(--color-border)',
            color: 'var(--color-text)',
            textDecoration: 'none',
            fontWeight: 500,
          }}
        >
          <ArrowLeft size={15} /> Back to Admin
        </Link>
      </div>

      <Card style={{ padding: '0.75rem' }}>
        <div style={{ marginBottom: '0.9rem', padding: '0.25rem 0.25rem 0 0.25rem' }}>
          <div style={{ fontWeight: 600 }}>Embedded workspace</div>
          <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.82rem' }}>
            Use the Superset menu inside the frame for navigation. The frame opens on the proxied dashboard workspace directly, and Superset will present its own sign-in screen there when a Superset session is still needed.
          </div>
        </div>
        <div style={{
          border: '1px solid var(--color-border)',
          borderRadius: 'var(--radius-lg)',
          overflow: 'hidden',
          height: 'calc(100vh - 12rem)',
          minHeight: 720,
          background: 'var(--color-surface)',
        }}>
          {isPreparingSession ? (
            <div style={{
              width: '100%',
              height: '100%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'var(--color-text-secondary)',
              fontSize: '0.9rem',
              background: 'var(--color-surface)',
            }}>
              Preparing Superset session...
            </div>
          ) : (
            <iframe
              ref={iframeRef}
              src={supersetEntryUrl}
              title="Superset Graph Builder"
              onLoad={() => {
                syncIframeTheme()
                const doc = iframeRef.current?.contentDocument
                if (doc?.body) {
                  observerRef.current?.disconnect()
                  observerRef.current = new MutationObserver(() => syncIframeTheme())
                  observerRef.current.observe(doc.body, { childList: true, subtree: true })
                }
              }}
              style={{ width: '100%', height: '100%', border: 0, display: 'block' }}
            />
          )}
        </div>
      </Card>
    </div>
  )
}
