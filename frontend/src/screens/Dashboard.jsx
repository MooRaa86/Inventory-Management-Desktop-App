import React from 'react'
import { api } from '../api'
import { usePage, Badge, fmtDate } from '../ui'

function Stat({ label, value, tone }) {
  return (
    <div className={`statcard ${tone || ''}`}>
      <div className="lbl">{label}</div>
      <div className="num">{value ?? '—'}</div>
    </div>
  )
}

export default function Dashboard() {
  const { data: d, error, reload } = usePage(() => api.get('/api/dashboard'))
  const { data: settings } = usePage(() => api.get('/api/settings'), [])
  const companyName = settings?.editable?.['company.name'] || 'Company'

  if (error) return <div className="content"><div className="err-banner">{error}</div></div>
  if (!d) return <div className="content">Loading…</div>

  return (
    <div className="content">
      <div className="toolbar" style={{ marginBottom: 4 }}>
        <h2 className="page-title" style={{ flex: 1 }}>{companyName} — Dashboard</h2>
        <button className="secondary small" onClick={reload}>↻ Refresh</button>
      </div>
      <div className="cards">
        <Stat label="Products (active)" value={`${d.activeProducts} / ${d.totalProducts}`} />
        <Stat label="Total stock quantity" value={d.totalStockQuantity} />
        <Stat label="Low stock" value={d.lowStockCount} tone={d.lowStockCount > 0 ? 'warn' : ''} />
        <Stat label="Out of stock" value={d.outOfStockCount} tone={d.outOfStockCount > 0 ? 'err' : ''} />
        <Stat label="Suppliers (active)" value={`${d.activeSuppliers} / ${d.totalSuppliers}`} />
        <Stat label="Pending purchases" value={d.pendingPurchases} />
      </div>

      <div className="cards">
        <Stat label="Today stock IN" value={d.todayStockIn} />
        <Stat label="Today stock OUT" value={d.todayStockOut} />
        <Stat label={`Month IN · ${new Date().toLocaleString(undefined, { month: 'short' })}`} value={d.monthStockIn} />
        <Stat label="Month OUT" value={d.monthStockOut} />
        <Stat label="Last backup"
              value={d.backup?.lastSuccessfulBackupAt ? fmtDate(d.backup.lastSuccessfulBackupAt) : 'never'}
              tone={d.backup?.overdueWarning ? 'warn' : ''} />
      </div>

      <div className="two-col">
        <div className="panel">
          <h3 style={{ marginTop: 0 }}>Low stock alerts</h3>
          {d.lowStockProducts.length === 0 && <div className="muted">Nothing to reorder 🎉</div>}
          <table className="grid">
            <thead><tr><th>Name</th><th>Min</th><th>Current</th><th>Status</th></tr></thead>
            <tbody>
              {d.lowStockProducts.slice(0, 8).map((p) => (
                <tr key={p.id}>
                  <td>{p.name}</td>
                  <td>{p.minStock}</td><td><b>{p.currentStock}</b></td>
                  <td><Badge value={p.status} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="panel">
          <h3 style={{ marginTop: 0 }}>Recent movements</h3>
          <table className="grid">
            <thead><tr><th>Product</th><th>Type</th><th>Qty</th><th>New</th><th>When</th></tr></thead>
            <tbody>
              {d.recentMovements.map((m) => (
                <tr key={m.id}>
                  <td>{m.productName}</td>
                  <td>{m.movementType === 'STOCK_IN'
                    ? <span className="badge green">IN</span>
                    : m.movementType === 'STOCK_OUT'
                      ? <span className="badge red">OUT</span>
                      : <span className="badge blue">ADJ</span>}</td>
                  <td>{Number(m.quantity)}</td>
                  <td>{Number(m.newStock)}</td>
                  <td className="muted">{fmtDate(m.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
