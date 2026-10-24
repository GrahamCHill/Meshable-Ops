import { Link, useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useAdminGroups } from './useAdminGroups'

export function AdminPageHeader({ title, description, actions }: Readonly<{
  title: string
  description: string
  actions?: ReactNode
}>) {
  const location = useLocation(); const path = location.pathname
  const plugin = new URLSearchParams(location.search).get('plugin')
  const adminGroups = useAdminGroups()
  const isOverview = path === '/admin'
  const group = adminGroups.find(group => group.items.some(item => plugin ? item.to.split('?')[0] === path && new URLSearchParams(item.to.split('?')[1]).get('plugin') === plugin : item.to === path))
  return (
    <header className="admin-page-header">
      <nav className="breadcrumbs" aria-label="Back navigation">
        <Link to={isOverview ? '/' : '/admin'}>{isOverview ? 'Back to workspace' : 'Back to admin menu'}</Link>
        {group && <><span aria-hidden="true">/</span><Link to={`/admin/sections/${group.id}`}>{group.label}</Link></>}
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
