import React from 'react'
import { api, qs } from '../api'
import { usePage, Pager } from '../ui'

export default function AuditLogs() {
  const [username, setUsername] = React.useState('')
  const [action, setAction] = React.useState('')
  const [page, setPage] = React.useState(0)
  const { data, error } = usePage(
    () => api.get(`/api/audit-logs${qs({ username, action, page, size: 30 })}`),
    [username, action, page])

  return (
    <div className="content">
      <h2 className="page-title">Audit log</h2>
      {error && <div className="err-banner">{error}</div>}
      <div className="panel">
        <div className="toolbar">
          <input placeholder="Filter by user…" value={username} onChange={(e) => { setUsername(e.target.value); setPage(0) }} style={{ width: 200 }} />
          <input placeholder="Action e.g. STOCK_IN…" value={action} onChange={(e) => { setAction(e.target.value); setPage(0) }} style={{ width: 220 }} />
        </div>
        <table className="grid">
          <thead><tr><th>#</th><th>When</th><th>User</th><th>Action</th><th>Entity</th><th>Description</th></tr></thead>
          <tbody>
            {(data?.content || []).map((a) => (
              <tr key={a.id}>
                <td className="muted">{a.id}</td>
                <td style={{ whiteSpace: 'nowrap' }}>{String(a.createdAt).replace('T', ' ').slice(0, 19)}</td>
                <td>{a.username}</td>
                <td><span className="badge blue">{a.action}</span></td>
                <td>{a.entityType}{a.entityId ? ` #${a.entityId}` : ''}</td>
                <td>{a.description}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <Pager page={data?.page ?? 0} totalPages={data?.totalPages ?? 1} totalElements={data?.totalElements} onPage={setPage} />
      </div>
    </div>
  )
}
