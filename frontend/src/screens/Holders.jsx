import React, { useState } from 'react'
import { api, qs } from '../api'
import { usePage, Banner, Badge, Modal, Pager, fmtMoney, fmtDate } from '../ui'

const TYPES = ['USER', 'DEPARTMENT', 'PLACE', 'OTHER']
const EMPTY = { name: '', type: 'USER', contact: '', notes: '' }

export default function Holders() {
  const [search, setSearch] = useState('')
  const [type, setType] = useState('')
  const [page, setPage] = useState(0)
  const { data, error, reload } = usePage(
    () => api.get(`/api/holders${qs({ search, type, page, size: 15 })}`),
    [search, type, page])

  const [editing, setEditing] = useState(null)
  const [formError, setFormError] = useState('')

  const [viewing, setViewing] = useState(null)
  const [assignments, setAssignments] = useState(null)
  const [loadErr, setLoadErr] = useState('')

  async function viewProducts(h) {
    setViewing(h); setAssignments(null); setLoadErr('')
    try {
      const list = await api.get(`/api/assignments/holder?holderId=${h.id}`)
      setAssignments(list)
    } catch (err) { setLoadErr(err.message) }
  }

  async function exportHolder(fmt) {
    try { await api.postDownload('/api/reports/assignments', { format: fmt, holderId: viewing.id }) }
    catch (err) { setLoadErr(err.message) }
  }

  async function save(e) {
    e.preventDefault()
    setFormError('')
    const body = {
      name: editing.name, type: editing.type, contact: editing.contact || '',
      notes: editing.notes || '',
    }
    try {
      if (editing.id) await api.put(`/api/holders/${editing.id}`, body)
      else await api.post('/api/holders', body)
      setEditing(null); reload()
    } catch (err) { setFormError(err.message) }
  }

  async function toggleActive(h) {
    try {
      if (h.active) await api.post(`/api/holders/${h.id}/deactivate`)
      else await api.post(`/api/holders/${h.id}/activate`)
      reload()
    } catch (err) { alert(err.message) }
  }

  async function remove(h) {
    if (!confirm(`Delete holder "${h.name}"? Holders that are linked to assignments cannot be deleted — deactivate them instead.`)) return
    try {
      await api.del(`/api/holders/${h.id}`)
      reload()
    } catch (err) { alert(err.message) }
  }

  return (
    <div className="content">
      <h2 className="page-title">Holders</h2>
      <p className="muted">Holders are users, departments or places that products are assigned to. Assigning moves stock out of central inventory into a holder's hands.</p>
      <Banner error={error} />
      <div className="panel">
        <div className="toolbar">
          <input placeholder="Search holders…" value={search}
                 onChange={(e) => { setSearch(e.target.value); setPage(0) }} style={{ width: 240 }} />
          <select value={type} onChange={(e) => setType(e.target.value)}>
            <option value="">All types</option>
            {TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
          <div className="spacer" />
          <button onClick={() => { setEditing({ ...EMPTY }); setFormError('') }}>+ New holder</button>
        </div>
        <table className="grid">
          <thead><tr><th>Name</th><th>Type</th><th>Contact</th><th>Status</th><th></th></tr></thead>
          <tbody>
            {(data?.content || []).map((h) => (
              <tr key={h.id} style={!h.active ? { opacity: .5 } : undefined}>
                <td><b>{h.name}</b></td>
                <td><Badge value={h.type} /></td>
                <td>{h.contact || '—'}</td>
                <td>{h.active ? 'ACTIVE' : 'INACTIVE'}</td>
                <td style={{ whiteSpace: 'nowrap' }}>
                  <button className="secondary small" onClick={() => viewProducts(h)}>Products</button>{' '}
                  <button className="secondary small" onClick={() => { setEditing({ ...h }); setFormError('') }}>Edit</button>{' '}
                  <button className="secondary small" onClick={() => toggleActive(h)}>{h.active ? 'Deactivate' : 'Reactivate'}</button>{' '}
                  <button className="danger small" onClick={() => remove(h)}>Delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <Pager page={data?.page ?? 0} totalPages={data?.totalPages ?? 1} totalElements={data?.totalElements} onPage={setPage} />
      </div>

      {editing && (
        <Modal title={editing.id ? `Edit ${editing.name}` : 'New holder'} onClose={() => setEditing(null)}>
          <form onSubmit={save}>
            <Banner error={formError} />
            <label className="field">Name *<input required value={editing.name || ''} onChange={(e) => setEditing({ ...editing, name: e.target.value })} /></label>
            <label className="field">Type *
              <select required value={editing.type} onChange={(e) => setEditing({ ...editing, type: e.target.value })}>
                {TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
              </select></label>
            <label className="field">Contact<textarea rows={2} value={editing.contact || ''} onChange={(e) => setEditing({ ...editing, contact: e.target.value })} /></label>
            <label className="field">Notes<textarea rows={2} value={editing.notes || ''} onChange={(e) => setEditing({ ...editing, notes: e.target.value })} /></label>
            <div className="modal-actions">
              <button type="button" className="secondary" onClick={() => setEditing(null)}>Cancel</button>
              <button type="submit">Save</button>
            </div>
          </form>
        </Modal>
      )}
    {viewing && (
        <Modal title={`Products with ${viewing.name}`} onClose={() => setViewing(null)} wide>
          <Banner error={loadErr} />
          <p className="muted">All items currently assigned to this holder. Use the Assignments screen to adjust them.</p>
          <div className="row" style={{ marginBottom: 10 }}>
            {['CSV', 'XLSX', 'PDF'].map((fmt) => (
              <button key={fmt} className="secondary small" onClick={() => exportHolder(fmt)}>Export {fmt}</button>
            ))}
            <div className="spacer" />
          </div>
          {assignments === null ? (
            <div>Loading…</div>
          ) : assignments.length === 0 ? (
            <div className="ok-banner">No products are currently assigned to this holder.</div>
          ) : (
            <>
              <table className="grid">
                <thead><tr><th>Product</th><th>Qty assigned</th><th>Assigned date</th><th>Assigned by</th><th>Notes</th></tr></thead>
                <tbody>
                  {assignments.map((a) => (
                    <tr key={a.id}>
                      <td><b>{a.productName}</b></td>
                      <td>{fmtMoney(a.quantity)}</td>
                      <td>{fmtDate(a.assignedAt)}</td>
                      <td>{a.assignedBy || '—'}</td>
                      <td>{a.notes || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <div className="muted" style={{ marginTop: 8 }}>
                {assignments.length} item{assignments.length === 1 ? '' : 's'}
              </div>
            </>
          )}
        </Modal>
      )}
    </div>
  )
}