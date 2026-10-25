import { AdminPageHeader } from '../admin/AdminPageHeader'
import { ReportCoverEditor } from '../admin/ReportCoverEditor'
import '../../styles/projects.css'

export default function AdminReportCoversPage() {
  return <div className="page-stack project-details">
    <AdminPageHeader title="Report covers" description="Redesign the shared cover for project reports, preview it, and manage report delivery." />
    <ReportCoverEditor />
  </div>
}
