import { Link, useParams } from 'react-router-dom'
import { useAccess } from '../../api/access'
import { adminGroups } from '../admin/adminNavigation'
import { AdminPageHeader } from '../admin/AdminPageHeader'

export function AdminSectionPage() {
  const {sectionId} = useParams(); const access = useAccess()
  const group = adminGroups.find(item => item.id === sectionId)
  const items = group?.items.filter(item => access.canPath(item.to)) ?? []
  if (!group || !items.length) return <div className="page-stack"><h1>Section unavailable</h1><Link to="/admin">Back to admin menu</Link></div>
  return <div className="page-stack"><AdminPageHeader title={group.label} description={group.description} />
    <nav className="admin-cards" aria-label={`${group.label} settings`}>{items.map(item => <Link className="admin-area-link" key={item.to} to={item.to}><h3>{item.label}</h3><p>{item.description}</p></Link>)}</nav>
  </div>
}
