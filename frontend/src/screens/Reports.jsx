import React, { useState } from 'react'
import { api } from '../api'
import { Banner } from '../ui'

const TYPES = [
  ['inventory', 'Inventory', 'Current stock & value'],
  ['low-stock', 'Low stock', 'Reorder list with shortages'],
  ['movements', 'Movements', 'Stock movements in a date range'],
  ['purchases', 'Purchases', 'Purchase orders in a date range'],
  ['issues', 'Issues', 'Issue requests in a date range'],
  ['suppliers', 'Suppliers', 'Supplier directory'],
  ['audit', 'Audit trail', 'Security & activity log'],
  ['assignments', 'Assignments', 'Product assignments to holders'],
]

const FORMATS = ['JSON', 'CSV', 'XLSX', 'PDF']

export default function Reports() {
  const [type, setType] = useState('inventory')
  const [format, setFormat] = useState('JSON')
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [preview, setPreview] = useState(null)
  const [generatedAt, setGeneratedAt] = useState('')

  async function run() {
    setBusy(true); setError(''); setPreview(null)
    const body = { format, dateFrom: dateFrom || null, dateTo: dateTo || null }
    try {
      if (format === 'JSON') {
        const t = await api.post(`/api/reports/${type}`, body)
        setPreview(t)
        setGeneratedAt(new Date().toLocaleString())
      } else {
        await api.postDownload(`/api/reports/${type}`, body)
        setPreview(null)
      }
    } catch (err) { setError(err.message) }
    finally { setBusy(false) }
  }

  function totalRow() {
    if (!preview) return null
    const numeric = preview.columns
      .map((c, i) => ({ c, i }))
      .filter(({ c }) => /(^| )(qty|qty|amount|value|stock|price|cost)$/i.test(c) || /(value|amount|cost|price|total|qty|stock quantity)/i.test(c))
    if (!numeric.length) return null
    const row = preview.columns.map(() => '')
    numeric.forEach(({ c, i }) => {
      let sum = 0
      preview.rows.forEach((r) => {
        const v = parseFloat(String(r[i] || '').replace(/[^0-9.\-]/g, ''))
        if (!isNaN(v)) sum += v
      })
      row[i] = sum.toLocaleString(undefined, { maximumFractionDigits: 2 })
    })
    return row
  }

  return (
    <div className="content">
      <h2 className="page-title">Reports</h2>
      <p className="muted" style={{ marginTop: -8 }}>Generate on-screen previews or export professional documents. Reports are generated locally — nothing leaves this machine.</p>
      <Banner error={error} />

      <div className="panel">
        <div className="toolbar">
          <label className="field" style={{ minWidth: 220 }}>
            Report
            <select value={type} onChange={(e) => { setType(e.target.value); setPreview(null) }}>
              {TYPES.map(([v, l, d]) => <option key={v} value={v}>{l}</option>)}
            </select>
          </label>
          <label className="field">From<input type="date" value={dateFrom} onChange={(e) => setDateFrom(e.target.value)} /></label>
          <label className="field">To<input type="date" value={dateTo} onChange={(e) => setDateTo(e.target.value)} /></label>
          <label className="field">Format
            <select value={format} onChange={(e) => setFormat(e.target.value)}>
              {FORMATS.map((f) => <option key={f}>{f}</option>)}
            </select>
          </label>
          <button style={{ alignSelf: 'flex-end' }} disabled={busy} onClick={run}>{busy ? 'Running…' : 'Run report'}</button>
        </div>

        {TYPES.filter(([v]) => v === type).map(([v, l, d]) => (
          <div key={v} className="muted" style={{ fontSize: 12.5 }}>{d}</div>
        ))}

        {preview && (
          <>
            <div style={{ display: 'flex', gap: 10, margin: '10px 0 8px', flexWrap: 'wrap', alignItems: 'center' }}>
              <span className="chip">{preview.rows.length} row{preview.rows.length === 1 ? '' : 's'}</span>
              <span className="chip">{preview.columns.length} columns</span>
              {generatedAt && <span className="muted" style={{ fontSize: 12 }}>Generated {generatedAt}</span>}
            </div>
            <div style={{ maxHeight: 420, overflow: 'auto', border: '1px solid var(--border)', borderRadius: 6 }}>
              <table className="grid">
                <thead><tr>{preview.columns.map((c) => <th key={c}>{c}</th>)}</tr></thead>
                <tbody>
                  {preview.rows.slice(0, 200).map((r, i) => (
                    <tr key={i}>{r.map((cell, j) => <td key={j}>{cell}</td>)}</tr>
                  ))}
                </tbody>
                {totalRow() && (
                  <tfoot>
                    <tr className="summary-row">
                      {totalRow().map((cell, j) => <td key={j}><b>{cell || ''}</b></td>)}
                    </tr>
                  </tfoot>
                )}
              </table>
            </div>
            {preview.rows.length > 200 && <div className="muted" style={{ marginTop: 6 }}>Showing first 200 rows — export to CSV/XLSX/PDF for the full dataset.</div>}
          </>
        )}
      </div>
    </div>
  )
}