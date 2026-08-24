import React, { useState } from 'react'
import { api, qs } from '../api'
import { usePage, Banner, Badge, Pager, Modal, fmtDate, fmtMoney } from '../ui'

const EMPTY_LINE = { productId: '', quantity: 1, unitCostPrice: 0 }

export default function Purchases() {
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const list = usePage(
    () => api.get(`/api/purchases${qs({ search, status, page, size: 12 })}`),
    [search, status, page])
  const { data: sups } = usePage(() => api.get('/api/suppliers?size=200'), [])
  const { data: products } = usePage(() => api.get('/api/products?size=500'), [])

  const [detail, setDetail] = useState(null)
  const [creating, setCreating] = useState(null) // draft object
  const [formError, setFormError] = useState('')

  function newDraft() {
    setCreating({
      supplierId: '', purchaseDate: new Date().toISOString().slice(0, 10), notes: '',
      lines: [{ ...EMPTY_LINE }],
    })
    setFormError('')
  }

  const total = (creating?.lines || []).reduce((s, l) => s + (Number(l.quantity) || 0) * (Number(l.unitCostPrice) || 0), 0)

  async function saveDraft(e) {
    e.preventDefault(); setFormError('')
    const body = {
      supplierId: Number(creating.supplierId),
      purchaseDate: creating.purchaseDate,
      notes: creating.notes || '',
      items: creating.lines
        .filter((l) => l.productId)
        .map((l) => ({ productId: Number(l.productId), quantity: Number(l.quantity), unitCostPrice: Number(l.unitCostPrice) })),
    }
    try {
      await api.post('/api/purchases', body)
      setCreating(null); list.reload()
    } catch (err) { setFormError(err.message) }
  }

  async function action(p, what) {
    try {
      await api.post(`/api/purchases/${p.id}/${what}`)
      list.reload(); if (detail) openDetail(p.id)
    } catch (err) { alert(err.message) }
  }

  async function openDetail(id) {
    try { setDetail(await api.get(`/api/purchases/${id}`)) } catch (err) { alert(err.message) }
  }

  return (
    <div className="content">
      <h2 className="page-title">Purchases</h2>
      <Banner error={list.error} />
      <div className="panel">
        <div className="toolbar">
          <input placeholder="Search number / supplier…" value={search} onChange={(e) => { setSearch(e.target.value); setPage(0) }} style={{ width: 240 }} />
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">All statuses</option>
            <option value="PENDING">Pending</option>
            <option value="RECEIVED">Received</option>
            <option value="CANCELLED">Cancelled</option>
          </select>
          <div className="spacer" />
          <button onClick={newDraft}>+ New purchase</button>
        </div>
        <table className="grid">
          <thead><tr><th>Number</th><th>Date</th><th>Supplier</th><th>Total</th><th>Status</th><th></th></tr></thead>
          <tbody>
            {(list.data?.content || []).map((p) => (
              <tr key={p.id}>
                <td>{p.purchaseNumber}</td>
                <td>{p.purchaseDate}</td>
                <td>{p.supplierName}</td>
                <td>{fmtMoney(p.totalAmount)}</td>
                <td><Badge value={p.status} /></td>
                <td style={{ whiteSpace: 'nowrap' }}>
                  <button className="secondary small" onClick={() => openDetail(p.id)}>Open</button>
                  {p.status === 'PENDING' && <> {' '}
                    <button className="small" onClick={() => action(p, 'receive')}>Receive</button>{' '}
                    <button className="danger small" onClick={() => confirm(`Cancel ${p.purchaseNumber}?`) && action(p, 'cancel')}>Cancel</button>
                  </>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <Pager page={list.data?.page ?? 0} totalPages={list.data?.totalPages ?? 1} totalElements={list.data?.totalElements} onPage={setPage} />
      </div>

      {creating && (
        <Modal wide title="New purchase" onClose={() => setCreating(null)}>
          <form onSubmit={saveDraft}>
            <Banner error={formError} />
            <div className="row">
              <label className="field">Supplier *
                <select required value={creating.supplierId} onChange={(e) => setCreating({ ...creating, supplierId: e.target.value })}>
                  <option value=""></option>
                  {(sups?.content || []).filter((s) => s.active).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                </select></label>
              <label className="field">Date<input type="date" value={creating.purchaseDate} onChange={(e) => setCreating({ ...creating, purchaseDate: e.target.value })} /></label>
            </div>

            <table className="grid" style={{ margin: '10px 0' }}>
              <thead><tr><th style={{ width: '45%' }}>Product *</th><th>Qty *</th><th>Unit cost *</th><th>Line total</th><th></th></tr></thead>
              <tbody>
                {creating.lines.map((l, i) => (
                  <tr key={i}>
                    <td>
                      <select value={l.productId} onChange={(e) => {
                        const p = (products?.content || []).find((x) => String(x.id) === e.target.value)
                        const lines = [...creating.lines]
                        lines[i] = { ...l, productId: e.target.value, unitCostPrice: p?.costPrice ?? l.unitCostPrice }
                        setCreating({ ...creating, lines })
                      }}>
                        <option value=""></option>
                        {(products?.content || []).map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
                      </select>
                    </td>
                    <td><input type="number" step="any" min="0.001" style={{ width: 90 }} value={l.quantity}
                               onChange={(e) => { const lines = [...creating.lines]; lines[i] = { ...l, quantity: e.target.value }; setCreating({ ...creating, lines }) }} /></td>
                    <td><input type="number" step="0.01" min="0" style={{ width: 100 }} value={l.unitCostPrice}
                               onChange={(e) => { const lines = [...creating.lines]; lines[i] = { ...l, unitCostPrice: e.target.value }; setCreating({ ...creating, lines }) }} /></td>
                    <td>{fmtMoney((Number(l.quantity) || 0) * (Number(l.unitCostPrice) || 0))}</td>
                    <td>{creating.lines.length > 1 && <button type="button" className="secondary small" onClick={() => setCreating({ ...creating, lines: creating.lines.filter((_, j) => j !== i) })}>✕</button>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <button type="button" className="secondary small" onClick={() => setCreating({ ...creating, lines: [...creating.lines, { ...EMPTY_LINE }] })}>+ Add line</button>

            <label className="field" style={{ marginTop: 10 }}>Notes<textarea rows={2} value={creating.notes} onChange={(e) => setCreating({ ...creating, notes: e.target.value })} /></label>
            <div className="modal-actions">
              <span style={{ marginRight: 'auto', alignSelf: 'center', fontWeight: 700 }}>Total: {fmtMoney(total)}</span>
              <button type="button" className="secondary" onClick={() => setCreating(null)}>Cancel</button>
              <button type="submit">Create (PENDING)</button>
            </div>
          </form>
        </Modal>
      )}

      {detail && (
        <Modal title={`Purchase ${detail.purchaseNumber}`} onClose={() => setDetail(null)} wide>
          <div className="kv"><b>Supplier</b> {detail.supplierName}</div>
          <div className="kv"><b>Date</b> {detail.purchaseDate}</div>
          <div className="kv"><b>Status</b> <Badge value={detail.status} /></div>
          <div className="kv"><b>Created by</b> {detail.createdByName}</div>
          {detail.receivedAt && <div className="kv"><b>Received</b> {fmtDate(detail.receivedAt)} by {detail.receivedByName}</div>}
          {detail.notes && <div className="kv"><b>Notes</b> {detail.notes}</div>}
          <table className="grid" style={{ marginTop: 12 }}>
            <thead><tr><th>Product</th><th>Qty</th><th>Unit cost</th><th>Line total</th></tr></thead>
            <tbody>
              {(detail.items || []).map((it) => (
                <tr key={it.id}>
                  <td>{it.productName}</td>
                  <td>{Number(it.quantity)}</td>
                  <td>{fmtMoney(it.unitCostPrice)}</td>
                  <td>{fmtMoney(it.lineTotal)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div style={{ textAlign: 'right', fontWeight: 700, marginTop: 8 }}>Total: {fmtMoney(detail.totalAmount)}</div>
          <div className="modal-actions">
            <button type="button" className="secondary small" onClick={() => api.download(`/api/purchases/${detail.id}/export`)}>Export PDF</button>
            <div style={{ flex: 1 }} />
            {detail.status === 'PENDING' && (
              <>
                <button className="small" onClick={() => action(detail, 'receive')}>Receive into stock</button>
                <button className="danger small" onClick={() => action(detail, 'cancel')}>Cancel purchase</button>
              </>
            )}
          </div>
        </Modal>
      )}
    </div>
  )
}
