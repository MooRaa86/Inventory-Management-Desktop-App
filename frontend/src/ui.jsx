import React, { useEffect, useState } from 'react'

export function usePage(fetcher, deps = []) {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [tick, setTick] = useState(0)
  useEffect(() => {
    let alive = true
    setLoading(true)
    fetcher()
      .then((d) => alive && (setData(d), setError('')))
      .catch((e) => alive && setError(e.message))
      .finally(() => alive && setLoading(false))
    return () => { alive = false }
  }, [...deps, tick])
  return { data, error, loading, reload: () => setTick(t => t + 1) }
}

export function Banner({ error, ok }) {
  if (error) return <div className="err-banner">{error}</div>
  if (ok) return <div className="ok-banner">{ok}</div>
  return null
}

export function Modal({ title, onClose, children, wide }) {
  return (
    <div className="modal-overlay" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" style={wide ? { maxWidth: 860 } : undefined}>
        <h3>{title}</h3>
        {children}
      </div>
    </div>
  )
}

export function Pager({ page, totalPages, totalElements, onPage }) {
  if (totalPages <= 1 && !totalElements) return null
  return (
    <div className="pager">
      <button className="secondary small" disabled={page <= 0} onClick={() => onPage(page - 1)}>Prev</button>
      <span>Page {page + 1} of {totalPages} · {totalElements} items</span>
      <button className="secondary small" disabled={page >= totalPages - 1} onClick={() => onPage(page + 1)}>Next</button>
    </div>
  )
}

const STATUS_COLORS = {
  IN_STOCK: 'green', LOW_STOCK: 'orange', OUT_OF_STOCK: 'red',
  PENDING: 'orange', RECEIVED: 'green', CANCELLED: 'gray',
  DRAFT: 'gray', APPROVED: 'blue', COMPLETED: 'green',
  ACTIVE: 'green', SUCCESS: 'green', FAILED: 'red',
}

export function Badge({ value }) {
  const color = STATUS_COLORS[value] || 'blue'
  return <span className={`badge ${color}`}>{String(value).replaceAll('_', ' ')}</span>
}

export function fmtMoney(v) {
  if (v === null || v === undefined || v === '') return ''
  const n = Number(v)
  return Number.isFinite(n) ? n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : v
}

export function fmtDate(s) {
  if (!s) return ''
  return String(s).replace('T', ' ').slice(0, 16)
}
