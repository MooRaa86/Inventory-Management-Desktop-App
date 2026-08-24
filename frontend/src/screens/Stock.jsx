import React, { useState } from 'react'
import { api, qs } from '../api'
import { usePage, Banner, Badge, Pager, fmtDate, fmtMoney } from '../ui'

const OPS = [
  { type: 'STOCK_IN', label: 'Stock In', btn: '+ Stock In', desc: 'Receive stock without a purchase order (donations, returns, found items).' },
  { type: 'STOCK_OUT', label: 'Stock Out', btn: '− Stock Out', desc: 'Remove stock without an issue note (damage, loss, samples).' },
  { type: 'ADJUSTMENT', label: 'Adjustment', btn: '⚖ Adjust', desc: 'Set the counted quantity after a physical count.' },
]

export default function Stock() {
  const products = usePage(() => api.get('/api/products?size=500'), [])
  const [filterProduct, setFilterProduct] = useState('')
  const [filterType, setFilterType] = useState('')
  const [page, setPage] = useState(0)
  const ledger = usePage(
    () => api.get(`/api/inventory/movements${qs({ productId: filterProduct, movementType: filterType, page, size: 15 })}`),
    [filterProduct, filterType, page])

  const [op, setOp] = useState(null)          // {type}
  const [form, setForm] = useState({ productId: '', quantity: '', reason: '', direction: 'IN' })
  const [formError, setFormError] = useState('')

  async function submit(e) {
    e.preventDefault(); setFormError('')
    try {
      if (op.type === 'STOCK_IN') await api.post('/api/inventory/stock-in', { productId: Number(form.productId), quantity: Number(form.quantity), reference: form.reason || null })
      else if (op.type === 'STOCK_OUT') await api.post('/api/inventory/stock-out', { productId: Number(form.productId), quantity: Number(form.quantity), reason: form.reason })
      else await api.post('/api/inventory/adjust', { productId: Number(form.productId), quantity: Number(form.quantity), direction: form.direction, reason: form.reason })
      setOp(null); ledger.reload()
    } catch (err) { setFormError(err.message) }
  }

  return (
    <div className="content">
      <h2 className="page-title">Stock Operations</h2>

      <div className="toolbar" style={{ marginBottom: 16 }}>
        {OPS.map((o) => (
          <button key={o.type} onClick={() => { setOp(o); setForm({ productId: '', quantity: '', reason: '', direction: 'IN' }); setFormError('') }}>{o.btn}</button>
        ))}
      </div>
      {products.error && <Banner error={products.error} />}

      <div className="panel">
        <div className="toolbar">
          <b>Ledger</b>
          <select value={filterProduct} onChange={(e) => { setFilterProduct(e.target.value); setPage(0) }} style={{ maxWidth: 260 }}>
            <option value="">All products</option>
            {(products.data?.content || []).map((p) => (
              <option key={p.id} value={p.id}>{p.name}</option>
            ))}
          </select>
          <select value={filterType} onChange={(e) => { setFilterType(e.target.value); setPage(0) }}>
            <option value="">All types</option>
            <option value="STOCK_IN">IN</option><option value="STOCK_OUT">OUT</option>
            <option value="ADJUSTMENT_IN">Adj +</option><option value="ADJUSTMENT_OUT">Adj −</option>
          </select>
        </div>
        <table className="grid">
          <thead><tr><th>#</th><th>When</th><th>Product</th><th>Type</th><th>Qty</th><th>Before</th><th>After</th><th>Reference</th><th>User</th></tr></thead>
          <tbody>
            {(ledger.data?.content || []).map((m) => (
              <tr key={m.id}>
                <td className="muted">{m.id}</td>
                <td>{fmtDate(m.createdAt)}</td>
                <td>{m.productName}</td>
                <td>{m.movementType === 'STOCK_IN' ? <Badge value="IN_STOCK" /> : m.movementType === 'STOCK_OUT' ? <span className="badge red">OUT</span> : <span className="badge blue">{m.movementType}</span>}</td>
                <td><b>{Number(m.quantity)}</b></td>
                <td>{Number(m.previousStock)}</td><td>{Number(m.newStock)}</td>
                <td>{m.reference || '—'}</td><td>{m.username}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <Pager page={ledger.data?.page ?? 0} totalPages={ledger.data?.totalPages ?? 1} totalElements={ledger.data?.totalElements} onPage={setPage} />
      </div>

      {op && (
        <div className="modal-overlay" onMouseDown={(e) => e.target === e.currentTarget && setOp(null)}>
          <div className="modal">
            <h3>{op.label}</h3>
            <p className="muted">{op.desc}</p>
            <Banner error={formError} />
            <form onSubmit={submit}>
              <label className="field">Product *
                <select required value={form.productId} onChange={(e) => setForm({ ...form, productId: e.target.value })}>
                  <option value=""></option>
                  {(products.data?.content || []).map((p) => (
                    <option key={p.id} value={p.id}>{p.name} (current: {p.currentStock})</option>
                  ))}
                </select></label>
              <label className="field">
                {op.type === 'ADJUSTMENT' ? 'Counted quantity *' : 'Quantity *'}
                <input type="number" step="any" min={op.type === 'ADJUSTMENT' ? undefined : '0.001'} required
                       value={form.quantity} onChange={(e) => setForm({ ...form, quantity: e.target.value })} />
              </label>
              <label className="field">{op.type === 'STOCK_IN' ? 'Reference / note' : 'Reason *'}
                <input required={op.type !== 'STOCK_IN'} value={form.reason} onChange={(e) => setForm({ ...form, reason: e.target.value })} />
              </label>
              {op.type === 'ADJUSTMENT' && (
                <label className="field">Direction
                  <select value={form.direction} onChange={(e) => setForm({ ...form, direction: e.target.value })}>
                    <option value="IN">IN — increase current stock</option>
                    <option value="OUT">OUT — decrease current stock</option>
                  </select></label>
              )}
              <div className="modal-actions">
                <button type="button" className="secondary" onClick={() => setOp(null)}>Cancel</button>
                <button type="submit">Apply</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
