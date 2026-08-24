import React, { useState } from 'react'
import { api, qs } from '../api'
import { Banner } from '../ui'

const TYPES = [
  ['inventory', 'Inventory — current stock & value'],
  ['low-stock', 'Low stock / reorder list'],
  ['movements', 'Stock movements (range)'],
  ['purchases', 'Purchases (range)'],
  ['issues', 'Issues (range)'],
  ['suppliers', 'Suppliers directory'],
  ['audit', 'Audit trail'],
]

export default function Reports() {
  const [type, setType] = useState('inventory')
  const [format, setFormat] = useState('JSON')
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [preview, setPreview] = useState(null)

  async function run() {
    setBusy(true); setError(''); setPreview(null)
    const body = { format, dateFrom: dateFrom || null, dateTo: dateTo || null }
    try {
      if (format === 'JSON') {
        const t = await api.post(`/api/reports/${type}`, body)
        setPreview(t)
      } else {
        // file formats return GeneratedFile JSON; use download endpoint afterwards
        const gen = await api.post(`/api/reports/${type}`, body)
        await api.download(`/api/reports/files/${gen.fileName}`)
      }
    } catch (err) { setError(err.message) }
    finally { setBusy(false) }
  }

  return (
    <div className="content">
      <h2 className="page-title">Reports</h2>
      <Banner error={error} />
      <div className="panel">
        <div className="toolbar">
          <label className="field" style={{ minWidth: 300 }}>
            Report
            <select value={type} onChange={(e) => setType(e.target.value)}>
              {TYPES.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
            </select>
          </label>
          <label className="field">From<input type="date" value={dateFrom} onChange={(e) => setDateFrom(e.target.value)} /></label>
          <label className="field">To<input type="date" value={dateTo} onChange={(e) => setDateTo(e.target.value)} /></label>
          <label className="field">Format
            <select value={format} onChange={(e) => setFormat(e.target.value)}>
              <option>JSON</option><option>CSV</option><option>XLSX</option><option>PDF</option>
            </select></label>
          <button style={{ alignSelf: 'flex-end' }} disabled={busy} onClick={run}>{busy ? 'Running…' : 'Run report'}</button>
        </div>

        {preview && (
          <>
            <div className="muted" style={{ margin: '8px 0' }}>{preview.rows.length} rows · saved to exports\reports for CSV/XLSX/PDF exports</div>
            <div style={{ maxHeight: 420, overflow: 'auto', border: '1px solid var(--border)', borderRadius: 6 }}>
              <table className="grid">
                <thead><tr>{preview.columns.map((c) => <th key={c}>{c}</th>)}</tr></thead>
                <tbody>
                  {preview.rows.slice(0, 200).map((r, i) => (
                    <tr key={i}>{r.map((cell, j) => <td key={j}>{cell}</td>)}</tr>
                  ))}
                </tbody>
              </table>
            </div>
            {preview.rows.length > 200 && <div className="muted" style={{ marginTop: 6 }}>Showing first 200 rows — export to CSV/XLSX/PDF for the full dataset.</div>}
          </>
        )}
      </div>
    </div>
  )
}
