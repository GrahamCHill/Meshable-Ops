import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { adminBackupApi, auditLogsApi } from '../../api/client'
import { Button, Card, Spinner } from '../ui'
import { AdminPageHeader } from '../admin/AdminPageHeader'
import { extractFilename, formatDate, getErrorMessage } from '../admin/adminUtils'

export function AdminAuditPage() {
  const queryClient = useQueryClient()
  const [backupError, setBackupError] = useState('')
  const audit = useQuery({ queryKey: ['admin-audit-logs'], queryFn: () => auditLogsApi.list(0, 50) })
  const backup = useMutation({
    mutationFn: adminBackupApi.export,
    onSuccess: ({ blob, filename }) => {
      const url = window.URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = extractFilename(filename)
      anchor.click()
      window.URL.revokeObjectURL(url)
      setBackupError('')
      queryClient.invalidateQueries({ queryKey: ['admin-audit-logs'] })
    },
    onError: err => setBackupError(getErrorMessage(err, 'Unable to export the backup right now.')),
  })

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
      <AdminPageHeader
        title="Audit & backup"
        description="Review recent administrative activity and export the data managed by this application."

        actions={<Button loading={backup.isPending} onClick={() => backup.mutate()}> Export backup</Button>}
      />
      {backupError && <div style={{ color: 'var(--color-danger)', fontSize: '0.82rem' }}>{backupError}</div>}
      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12, marginBottom: '1rem' }}>
          <div>
            <div style={{ fontWeight: 600 }}>Recent activity</div>
            <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.8rem', marginTop: 3 }}>Backup includes relational data, dashboard links, admin settings, events, and audit logs.</div>
          </div>
          <Button variant="ghost" size="sm" disabled={audit.isFetching} onClick={() => audit.refetch()}> Refresh</Button>
        </div>
        {audit.isLoading ? <div style={{ padding: '2rem', textAlign: 'center' }}><Spinner /></div> : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            {(audit.data?.content ?? []).map(entry => (
              <div key={entry.id} style={{ padding: '0.85rem 1rem',   background: 'var(--color-bg)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }}>
                  <div>
                    <div style={{ fontWeight: 600 }}>{entry.action}</div>
                    <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.82rem' }}>User {entry.userEmail ?? entry.userId}{entry.resourceType ? ` • ${entry.resourceType}` : ''}{entry.resourceId ? ` • ${entry.resourceId}` : ''}</div>
                    {(entry.userName || entry.userContactNumber) && <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.78rem', marginTop: 4 }}>{entry.userName ? `Name: ${entry.userName}` : 'Name: Not provided'}{entry.userContactNumber ? ` • Contact: ${entry.userContactNumber}` : ''}</div>}
                  </div>
                  <div style={{ color: 'var(--color-text-secondary)', fontSize: '0.8rem', textAlign: 'right' }}>{formatDate(entry.timestamp)}<br />{entry.ipAddress ?? 'No IP recorded'}</div>
                </div>
              </div>
            ))}
            {audit.data?.content.length === 0 && <div style={{ color: 'var(--color-text-secondary)' }}>No audit entries have been recorded yet.</div>}
          </div>
        )}
      </Card>
    </div>
  )
}
