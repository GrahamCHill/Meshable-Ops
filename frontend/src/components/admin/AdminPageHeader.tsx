import { Link, useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'

export function AdminPageHeader({ title, description, actions }: Readonly<{
  title: string
  description: string
  actions?: ReactNode
}>) {
  const isOverview = useLocation().pathname === '/admin'
  return (
    <header className="admin-page-header">
      <nav className="breadcrumbs" aria-label="Back navigation">
        <Link to={isOverview ? '/' : '/admin'}>{isOverview ? 'Back to workspace' : 'Back to admin menu'}</Link>
      </nav>
      <div className="page-heading">
        <div>
          <h1>{title}</h1>
          <p>{description}</p>
        </div>
        {actions}
      </div>
    </header>
  )
}
