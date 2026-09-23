import React, { useState } from 'react'
import { api, qs } from '../api'
import { usePage, Banner, Badge, Modal, Pager, fmtDate } from '../ui'

export default function Assignments() {
  const [search, setSearch] = useState('')
  const [holderId, setHolderId] = useState('')
  const [page, setPage] = useState(0)
  const [tab, setTab] = useState('assignments')

  const { data: products } = usePage(() => api.get('/api/products?size=500'), [])
  const { data: holders } = usePage(() => api.get('/api/holders/all?active=true'), [])
  const { data, error, reload } = usePage(
    () => api.get(`/api/assignments${qs({ search, holderId, page, size: 15 })}`),
    [search, holderId, page])
  const { data: history, error: histError, reload: reloadHist } = usePage(
    () => api.get(`/api/assignments/history${qs({ search, holderId, page, size: 15 })}`),
    [search, holderId, page, tab])

  const [mode, setMode] = useState(null) // 'assign' | 'unassign' | 'transfer'
  const [form, setForm] = useState({ productId: '', holderId: '', toHolderId: '', quantity: '', notes: '' })
  const [formError, setFormError] = useState('')

  const can = () => true

  function open(m) {
    setMode(m); setForm({ productId: '', holderId: '', toHolderId: '', quantity: '', notes: '' }); setFormError('')
  }

  async function submit() {
    setFormError('')
    const qty = Number(form.quantity)
    const base = { productId: Number(form.productId), quantity: qty, notes: form.notes }
    try {
      if (mode === 'assign') {
        await api.post('/api/assignments/assign', { ...base, holderId: Number(form.holderId) })
      } else if (mode === 'unassign') {
        await api.post('/api/assignments/unassign', { ...base, holderId: Number(form.holderId) })
      } else if (mode === 'transfer') {
        await api.post('/api/assignments/transfer', { ...base, fromHolderId: Number(form.holderId), toHolderId: Number(form.toHolderId) })
      }
      setMode(null); reload(); reloadHist()
    } catch (err) { setFormError(err.message) }
  }

  return (
    <div className="content">
      <h2 className="page-title">Product Assignments</h2>
      <p className="muted">Assigning moves stock from central inventory to a holder (user, department or place). Unassigning returns it. Transferring moves it between holders.</p>
      <Banner error={error || histError} />
      <div className="panel">
        <div className="toolbar">
          <button className={`tab ${tab === 'assignments' ? 'active' : ''}`} onClick={() => setTab('assignments')}>Assignments</button>
          <button className={`tab ${tab === 'history' ? 'active' : ''}`} onClick={() => setTab('history')}>History</button>
          <span style={{ width: 12 }} />
          <input placeholder="Search product/holder…" value={search}
                 onChange={(e) => { setSearch(e.target.value); setPage(0) }} style={{ width: 220 }} />
          <select value={holderId} onChange={(e) => { setHolderId(e.target.value); setPage(0) }}>
            <option value="">All holders</option>
            {(holders || []).map((h) => <option key={h.id} value={h.id}>{h.name}</option>)}
          </select>
          <div className="spacer" />
          <button onClick={() => open('assign')}>+ Assign</button>
          <button className="secondary" onClick={() => open('unassign')}>Return</button>
          <button className="secondary" onClick={() => open('transfer')}>Transfer</button>
        </div>

        {tab === 'assignments' ? (
          <>
            <table className="grid">
              <thead><tr><th>Product</th><th>Holder</th><th>Type</th><th>Assigned Qty</th><th>Assigned Date</th><th>By</th></tr></thead>
              <tbody>
                {(data?.content || []).map((a) => (
                  <tr key={a.id}>
                    <td><b>{a.productName}</b></td>
                    <td>{a.holderName}</td>
                    <td><Badge value={a.holderType} /></td>
                    <td><b>{a.quantity}</b></td>
                    <td>{fmtDate(a.assignedAt)}</td>
                    <td>{a.assignedBy}</td>
                  </tr>
                ))}
                {!(data?.content || []).length && <tr><td colSpan={6} className="muted">No assignments yet. Use “+ Assign” to give stock to a holder.</td></tr>}
              </tbody>
            </table>
            <Pager page={data?.page ?? 0} totalPages={data?.totalPages ?? 1} totalElements={data?.totalElements} onPage={setPage} />
          </>
        ) : (
          <>
            <table className="grid">
              <thead><tr><th>Date</th><th>Product</th><th>Action</th><th>From</th><th>To</th><th>Qty</th><th>By</th></tr></thead>
              <tbody>
                {(history?.content || []).map((t) => (
                  <tr key={t.id}>
                    <td>{fmtDate(t.createdAt)}</td>
                    <td><b>{t.productName}</b></td>
                    <td><Badge value={t.action} /></td>
                    <td>{t.fromHolderName || '—'}</td>
                    <td>{t.toHolderName || '—'}</td>
                    <td>{t.quantity}</td>
                    <td>{t.transferBy}</td>
                  </tr>
                ))}
                {!(history?.content || []).length && <tr><td colSpan={7} className="muted">No transfer history.</td></tr>}
              </tbody>
            </table>
            <Pager page={history?.page ?? 0} totalPages={history?.totalPages ?? 1} totalElements={history?.totalElements} onPage={setPage} />
          </>
        )}
      </div>

      {mode && (
        <Modal title={{ assign: 'Assign product to holder', unassign: 'Return product from holder', transfer: 'Transfer between holders' }[mode]} onClose={() => setMode(null)}>
          <Banner error={formError} />
          <label className="field">Product *
            <select required value={form.productId} onChange={(e) => setForm({ ...form, productId: e.target.value })}>
              <option value="">— select product —</option>
              {(products?.content || []).filter((p) => p.active).map((p) =>
                <option key={p.id} value={p.id}>{p.name} (in stock: {p.currentStock})</option>)}
            </select></label>
          <label className="field">{mode === 'transfer' ? 'From holder *' : 'Holder *'}
            <select required value={form.holderId} onChange={(e) => setForm({ ...form, holderId: e.target.value })}>
              <option value="">— select holder —</option>
              {(holders || []).map((h) => <option key={h.id} value={h.id}>{h.name} ({h.type})</option>)}
            </select></label>
          {mode === 'transfer' && (
            <label className="field">To holder *
              <select required value={form.toHolderId} onChange={(e) => setForm({ ...form, toHolderId: e.target.value })}>
                <option value="">— select holder —</option>
                {(holders || []).filter((h) => String(h.id) !== String(form.holderId)).map((h) =>
                  <option key={h.id} value={h.id}>{h.name} ({h.type})</option>)}
              </select></label>
          )}
          <label className="field">Quantity *
            <input type="number" step="any" min="0" required value={form.quantity}
                   onChange={(e) => setForm({ ...form, quantity: e.target.value })} /></label>
          <label className="field">Notes<textarea rows={2} value={form.notes || ''} onChange={(e) => setForm({ ...form, notes: e.target.value })} /></label>
          <div className="modal-actions">
            <button type="button" className="secondary" onClick={() => setMode(null)}>Cancel</button>
            <button onClick={submit}>Confirm</button>
          </div>
        </Modal>
      )}
    </div>
  )
}