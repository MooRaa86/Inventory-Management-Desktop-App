import React, { useState } from 'react'
import { api, qs } from '../api'
import { usePage, Banner, Badge, Pager, Modal, fmtDate } from '../ui'

export default function Issues() {
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const list = usePage(
    () => api.get(`/api/issues${qs({ search, status, page, size: 12 })}`),
    [search, status, page])
  const { data: products } = usePage(() => api.get('/api/products?size=500'), [])

  const [detail, setDetail] = useState(null)
  const [creating, setCreating] = useState(null)
  const [formError, setFormError] = useState('')

  function newDraft() {
    setCreating({
      department: '', requestedBy: '', notes: '',
      lines: [{ productId: '', quantity: 1 }],
    })
    setFormError('')
  }

  async function saveDraft(e) {
    e.preventDefault(); setFormError('')
    const body = {
      department: creating.department,
      requestedBy: creating.requestedBy || '',
      notes: creating.notes || '',
      items: creating.lines.filter((l) => l.productId)
        .map((l) => ({ productId: Number(l.productId), quantity: Number(l.quantity) })),
    }
    try {
      await api.post('/api/issues', body)
      setCreating(null); list.reload()
    } catch (err) { setFormError(err.message) }
  }

  async function action(p, what) {
    try {
      await api.post(`/api/issues/${p.id}/${what}`)
      list.reload()
      if (detail) setDetail(await api.get(`/api/issues/${p.id}`))
    } catch (err) { alert(err.message) }
  }

  async function openDetail(id) {
    try { setDetail(await api.get(`/api/issues/${id}`)) } catch (err) { alert(err.message) }
  }

  return (
    <div className="content">
      <h2 className="page-title">Issues (stock out to departments)</h2>
      <Banner error={list.error} />
      <div className="panel">
        <div className="toolbar">
          <input placeholder="Search number / dept / requester…" value={search} onChange={(e) => { setSearch(e.target.value); setPage(0) }} style={{ width: 260 }} />
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">All statuses</option>
            <option value="DRAFT">Draft</option><option value="APPROVED">Approved</option>
            <option value="COMPLETED">Completed</option><option value="CANCELLED">Cancelled</option>
          </select>
          <div className="spacer" />
          <button onClick={newDraft}>+ New issue</button>
        </div>
        <table className="grid">
          <thead><tr><th>Number</th><th>Department</th><th>Requested by</th><th>Status</th><th>Created</th><th></th></tr></thead>
          <tbody>
            {(list.data?.content || []).map((i) => (
              <tr key={i.id}>
                <td>{i.issueNumber}</td><td>{i.department}</td><td>{i.requestedBy || '—'}</td>
                <td><Badge value={i.status} /></td>
                <td className="muted">{fmtDate(i.createdAt)}</td>
                <td style={{ whiteSpace: 'nowrap' }}>
                  <button className="secondary small" onClick={() => openDetail(i.id)}>Open</button>
                  {i.status === 'DRAFT' && <> {' '}
                    <button className="small" onClick={() => action(i, 'approve')}>Approve</button>{' '}
                    <button className="danger small" onClick={() => action(i, 'cancel')}>Cancel</button>
                  </>}
                  {i.status === 'APPROVED' && <> {' '}
                    <button className="small" onClick={() => action(i, 'complete')}>Complete</button>{' '}
                    <button className="danger small" onClick={() => action(i, 'cancel')}>Cancel</button>
                  </>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <Pager page={list.data?.page ?? 0} totalPages={list.data?.totalPages ?? 1} totalElements={list.data?.totalElements} onPage={setPage} />
      </div>

      {creating && (
        <Modal wide title="New issue" onClose={() => setCreating(null)}>
          <form onSubmit={saveDraft}>
            <Banner error={formError} />
            <div className="row">
              <label className="field">Department *
                <input required value={creating.department} onChange={(e) => setCreating({ ...creating, department: e.target.value })} /></label>
              <label className="field">Requested by
                <input value={creating.requestedBy} onChange={(e) => setCreating({ ...creating, requestedBy: e.target.value })} /></label>
            </div>
            <table className="grid" style={{ margin: '10px 0' }}>
              <thead><tr><th style={{ width: '65%' }}>Product *</th><th>Qty *</th><th>Current stock</th><th></th></tr></thead>
              <tbody>
                {creating.lines.map((l, i) => {
                  const p = (products?.content || []).find((x) => String(x.id) === l.productId)
                  return (
                    <tr key={i}>
                      <td>
                        <select value={l.productId} onChange={(e) => {
                          const lines = [...creating.lines]; lines[i] = { ...l, productId: e.target.value }
                          setCreating({ ...creating, lines })
                        }}>
                          <option value=""></option>
                          {(products?.content || []).filter((x) => x.active).map((p) => (
                            <option key={p.id} value={p.id}>{p.name}</option>))}
                        </select>
                      </td>
                      <td><input type="number" step="any" min="0.001" style={{ width: 90 }} value={l.quantity}
                                 onChange={(e) => { const lines = [...creating.lines]; lines[i] = { ...l, quantity: e.target.value }; setCreating({ ...creating, lines }) }} /></td>
                      <td>{p ? <span className={Number(p.currentStock) < Number(l.quantity) ? 'badge red' : 'badge green'}>{p.currentStock}</span> : '—'}</td>
                      <td>{creating.lines.length > 1 && <button type="button" className="secondary small" onClick={() => setCreating({ ...creating, lines: creating.lines.filter((_, j) => j !== i) })}>✕</button>}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
            <button type="button" className="secondary small"
                    onClick={() => setCreating({ ...creating, lines: [...creating.lines, { productId: '', quantity: 1 }] })}>+ Add line</button>

            <label className="field" style={{ marginTop: 10 }}>Notes<textarea rows={2} value={creating.notes} onChange={(e) => setCreating({ ...creating, notes: e.target.value })} /></label>
            <div className="modal-actions">
              <button type="button" className="secondary" onClick={() => setCreating(null)}>Cancel</button>
              <button type="submit">Create draft</button>
            </div>
          </form>
        </Modal>
      )}

      {detail && (
        <Modal title={`Issue ${detail.issueNumber}`} onClose={() => setDetail(null)} wide>
          <div className="kv"><b>Department</b> {detail.department}</div>
          <div className="kv"><b>Status</b> <Badge value={detail.status} /></div>
          <div className="kv"><b>Requested by</b> {detail.requestedBy || '—'}</div>
          <div className="kv"><b>Created by</b> {detail.createdByName}</div>
          {detail.approvedByName && <div className="kv"><b>Approved by</b> {detail.approvedByName}</div>}
          {detail.completedAt && <div className="kv"><b>Completed</b> {fmtDate(detail.completedAt)} by {detail.completedByName}</div>}
          {detail.notes && <div className="kv"><b>Notes</b> {detail.notes}</div>}
          <table className="grid" style={{ marginTop: 12 }}>
            <thead><tr><th>Product</th><th>Qty</th></tr></thead>
            <tbody>
              {(detail.items || []).map((it) => (
                <tr key={it.id}><td>{it.productName}</td><td>{Number(it.quantity)}</td></tr>
              ))}
            </tbody>
          </table>
          <div className="modal-actions">
            {detail.status === 'DRAFT' && <>
              <button className="small" onClick={() => action(detail, 'approve')}>Approve</button>
              <button className="danger small" onClick={() => action(detail, 'cancel')}>Cancel</button>
            </>}
            {detail.status === 'APPROVED' && <>
              <button className="small" onClick={() => action(detail, 'complete')}>Complete (deduct stock)</button>
              <button className="danger small" onClick={() => action(detail, 'cancel')}>Cancel</button>
            </>}
          </div>
        </Modal>
      )}
    </div>
  )
}
