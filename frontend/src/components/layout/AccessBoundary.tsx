import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAccess } from '../../api/access'

export function AccessBoundary({ children }: { children: ReactNode }) {
  const access = useAccess()
  const { pathname } = useLocation()
  if (access.isLoading) return <p role="status">Loading your workspace permissions…</p>
  if (access.isError) return <div className="page-stack"><p role="alert">Unable to load access controls.</p><button onClick={() => access.refetch()}>Try again</button><a href="/login">Sign in again</a></div>
  if (!access.canPath(pathname)) {
    const landing = access.data?.views.find(view => !view.admin && access.can(view.id))?.path ?? '/account/security'
    return <Navigate to={landing} replace />
  }
  return <>{children}</>
}
