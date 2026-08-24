import React, { useState } from 'react'
import { api, qs } from '../api'
import { usePage, Banner, Modal, Pager, Badge, fmtMoney } from '../ui'

const EMPTY = { name: '', description: '', categoryId: '', unitId: '', supplierId: '', minStock: 0, maxStock: 0, costPrice: 0, sellingPrice: 0, openingQuantity: 0 }

export default function Products() {
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const { data: cats } = usePage(() => api.get('/api/categories?size=200'), [])
  const { data: units } = usePage(() => api.get('/api/units?size=200'), [])
  const { data: sups } = usePage(() => api.get('/api/suppliers?size=200'), [])
  const { data, error, reload } = usePage(
    () => api.get(`/api/products${qs({ search, status, page, size: 15 })}`),
    [search, status, page])

  const [editing, setEditing] = useState(null)
  const [formError, setFormError] = useState('')

  async function save(e) {
    e.preventDefault()
    setFormError('')
    const body = {
      name: editing.name,
      description: editing.description,
      categoryId: editing.categoryId || null,
      unitId: editing.unitId,
      supplierId: editing.supplierId || null,
      minStock: Number(editing.minStock || 0),
      maxStock: Number(editing.maxStock || 0),
      costPrice: Number(editing.costPrice || 0),
      sellingPrice: Number(editing.sellingPrice || 0),
    }
    if (!editing.id) body.openingQuantity = Number(editing.openingQuantity || 0)
    try {
      if (editing.id) await api.put(`/api/products/${editing.id}`, body)
      else await api.post('/api/products', body)
      setEditing(null); reload()
    } catch (err) { setFormError(err.message) }
  }

  async function toggleActive(p) {
    try {
      if (p.active) await api.del(`/api/products/${p.id}`)
      else await api.post(`/api/products/${p.id}/activate`)
      reload()
    } catch (err) { alert(err.message) }
  }

  async function deletePermanent(p) {
    if (!confirm(`Permanently delete "${p.name}"?\n\nThis only works if the product has no stock movements, purchases or issues.`)) return
    try {
      await api.del(`/api/products/${p.id}/permanent`)
      reload()
    } catch (err) { alert(err.message) }
  }

  return (
    <div className="content">
      <h2 className="page-title">Products</h2>
      <Banner error={error} />
      <div className="panel">
        <div className="toolbar">
          <input placeholder="Search products…" value={search}
                 onChange={(e) => { setSearch(e.target.value); setPage(0) }} style={{ width: 260 }} />
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">All statuses</option>
            <option value="IN_STOCK">In stock</option>
            <option value="LOW_STOCK">Low stock</option>
            <option value="OUT_OF_STOCK">Out of stock</option>
          </select>
          <div className="spacer" />
          <button onClick={() => { setEditing({ ...EMPTY }); setFormError('') }}>+ New product</button>
        </div>

        <table className="grid">
          <thead><tr><th>Name</th><th>Category</th><th>Unit</th><th>Current</th><th>Min</th><th>Cost</th><th>Sell</th><th>Status</th><th></th></tr></thead>
          <tbody>
            {(data?.content || []).map((p) => (
              <tr key={p.id} style={!p.active ? { opacity: .5 } : undefined}>
                <td>{p.name}</td><td>{p.categoryName || '—'}</td>
                <td>{p.unitSymbol}</td>
                <td><b>{p.currentStock}</b></td><td>{p.minStock}</td>
                <td>{fmtMoney(p.costPrice)}</td><td>{fmtMoney(p.sellingPrice)}</td>
                <td><Badge value={p.stockStatus} /></td>
                <td style={{ whiteSpace: 'nowrap' }}>
                  <button className="secondary small" onClick={() => { setEditing({ ...p }); setFormError('') }}>Edit</button>{' '}
                  <button className="secondary small" onClick={() => toggleActive(p)}>{p.active ? 'Deactivate' : 'Activate'}</button>{' '}
                  <button className="danger small" onClick={() => deletePermanent(p)}>Delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <Pager page={data?.page ?? 0} totalPages={data?.totalPages ?? 1} totalElements={data?.totalElements} onPage={setPage} />
      </div>

      {editing && (
        <Modal title={editing.id ? `Edit ${editing.name}` : 'New product'} onClose={() => setEditing(null)}>
          <form onSubmit={save}>
            <Banner error={formError} />
            <label className="field">Name *<input required value={editing.name || ''} onChange={(e) => setEditing({ ...editing, name: e.target.value })} /></label>
            <div className="row">
              <label className="field">Category
                <select value={editing.categoryId || ''} onChange={(e) => setEditing({ ...editing, categoryId: e.target.value })}>
                  <option value="">— none —</option>
                  {(cats?.content || []).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                </select></label>
              <label className="field">Unit *
                <select required value={editing.unitId || ''} onChange={(e) => setEditing({ ...editing, unitId: e.target.value })}>
                  <option value=""></option>
                  {(units?.content || []).map((u) => <option key={u.id} value={u.id}>{u.name} ({u.symbol})</option>)}
                </select></label>
              <label className="field">Default supplier
                <select value={editing.supplierId || ''} onChange={(e) => setEditing({ ...editing, supplierId: e.target.value })}>
                  <option value="">— none —</option>
                  {(sups?.content || []).map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                </select></label>
            </div>
            <div className="row">
              <label className="field">Min stock<input type="number" step="any" value={editing.minStock ?? 0} onChange={(e) => setEditing({ ...editing, minStock: e.target.value })} /></label>
              <label className="field">Max stock<input type="number" step="any" value={editing.maxStock ?? 0} onChange={(e) => setEditing({ ...editing, maxStock: e.target.value })} /></label>
            </div>
            <div className="row">
              <label className="field">Cost price<input type="number" step="0.01" min="0" value={editing.costPrice ?? 0} onChange={(e) => setEditing({ ...editing, costPrice: e.target.value })} /></label>
              <label className="field">Selling price<input type="number" step="0.01" min="0" value={editing.sellingPrice ?? 0} onChange={(e) => setEditing({ ...editing, sellingPrice: e.target.value })} /></label>
            </div>
            {!editing.id && (
              <label className="field">Opening stock
                <input type="number" step="any" min="0" value={editing.openingQuantity ?? 0} onChange={(e) => setEditing({ ...editing, openingQuantity: e.target.value })} />
              </label>
            )}
            <label className="field">Description<textarea rows={2} value={editing.description || ''} onChange={(e) => setEditing({ ...editing, description: e.target.value })} /></label>
            <div className="modal-actions">
              <button type="button" className="secondary" onClick={() => setEditing(null)}>Cancel</button>
              <button type="submit">Save</button>
            </div>
          </form>
        </Modal>
      )}
    </div>
  )
}
