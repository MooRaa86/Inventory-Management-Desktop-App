import React, { useState } from 'react'
import { api } from '../api'
import { usePage, Banner, Badge, fmtDate } from '../ui'

export default function Backups() {
  const list = usePage(() => api.get('/api/backups'), [])
  const [error, setError] = useState('')
  const [ok, setOk] = useState('')
  const [note, setNote] = useState('')
  const [importPath, setImportPath] = useState('')
  const [exportDir, setExportDir] = useState('')
  const [restoreTarget, setRestoreTarget] = useState(null)

  async function run(fn) {
    setError(''); setOk('')
    try { await fn(); list.reload() }
    catch (err) { setError(err.message) }
  }

  return (
    <div className="content">
      <h2 className="page-title">Backups</h2>
      <Banner error={error} ok={ok} />

      <div className="panel">
        <div className="toolbar">
          <input placeholder="Note for this backup (optional)" value={note} onChange={(e) => setNote(e.target.value)} style={{ width: 280 }} />
          <button disabled={list.loading}
                  onClick={() => run(async () => {
                    await api.post('/api/backups', { note })
                    setOk('Backup created and verified.')
                  })}>+ Create backup now</button>
        </div>
        <table className="grid">
          <thead><tr><th>#</th><th>File</th><th>Type</th><th>Size</th><th>Verified</th><th>By</th><th>When</th><th></th></tr></thead>
          <tbody>
            {(list.data || []).map((b) => (
              <tr key={b.id}>
                <td className="muted">{b.id}</td>
                <td style={{ maxWidth: 320, overflow: 'hidden', textOverflow: 'ellipsis' }}>{b.filename}</td>
                <td><span className={`badge ${b.backupType === 'SAFETY' ? 'orange' : 'blue'}`}>{b.backupType}</span></td>
                <td>{(b.sizeBytes / 1024).toFixed(1)} KB</td>
                <td>{b.verified ? <span className="badge green">✓</span> : <span className="badge red">?</span>}</td>
                <td>{b.createdByName}</td>
                <td className="muted">{fmtDate(b.createdAt)}</td>
                <td style={{ whiteSpace: 'nowrap' }}>
                  <button className="secondary small" onClick={() => run(() => api.download(`/api/backups/${b.id}/file`))}>Download</button>{' '}
                  <button className="secondary small" onClick={() =>
                    run(async () => { await api.post(`/api/backups/${b.id}/verify`); setOk('Verification passed.') })}>Verify</button>{' '}
                  <button className="danger small" onClick={() =>
                    confirm(`Delete backup ${b.filename}?`) && run(() => api.del(`/api/backups/${b.id}`))}>Delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!list.loading && (list.data || []).length === 0 && <div className="muted" style={{ marginTop: 8 }}>No backups yet — create one now.</div>}
      </div>

      <div className="two-col" style={{ marginTop: 16 }}>
        <div className="panel">
          <h3 style={{ marginTop: 0 }}>Import backup file</h3>
          <p className="muted">Copy a backup zip from a folder (e.g. USB drive) into the manual backups folder. The archive is verified before it is accepted.</p>
          <label className="field">Full path to .zip
            <input value={importPath} onChange={(e) => setImportPath(e.target.value)} placeholder="D:\backups\inventory-backup-….zip" /></label>
          <button disabled={!importPath} onClick={() => run(async () => {
            await api.post('/api/backups/import', { sourcePath: importPath, note: '' })
            setImportPath(''); setOk('Imported & verified.')
          })}>Import</button>
        </div>

        <div className="panel">
          <h3 style={{ marginTop: 0 }}>Export latest to folder</h3>
          <p className="muted">Copy a backup zip out of the app folder (e.g. to a network share or USB).</p>
          <label className="field">Target directory
            <input value={exportDir} onChange={(e) => setExportDir(e.target.value)} placeholder="E:\Backups" /></label>
          <select id="bk-select" defaultValue="" style={{ marginRight: 8 }}>
            <option value="">— pick backup —</option>
            {(list.data || []).map((b) => <option key={b.id} value={b.id}>{b.filename}</option>)}
          </select>
          <button onClick={() => {
            const id = document.getElementById('bk-select').value
            if (!id) return alert('Pick a backup first')
            if (!exportDir) return alert('Enter target directory')
            run(async () => {
              await api.post(`/api/backups/${id}/export`, { targetDir: exportDir })
              setOk('Exported.')
            })
          }}>Export</button>
        </div>
      </div>

      {restoreTarget && (
        <div className="modal-overlay" onMouseDown={(e) => e.target === e.currentTarget && setRestoreTarget(null)}>
          <div className="modal">
            <h3>⚠ Restore database?</h3>
            <p>This will replace the current database with the content of:</p>
            <p><b>{restoreTarget.filename}</b></p>
            <p className="muted">
              A safety backup of the current state is created automatically first.
              The system pauses briefly while files are swapped. Data created after this
              backup was taken will be lost.
            </p>
            <div className="modal-actions">
              <button className="secondary" onClick={() => setRestoreTarget(null)}>Keep current data</button>
              <button className="danger" onClick={() => {
                const t = restoreTarget
                setRestoreTarget(null)
                run(async () => {
                  await api.post(`/api/backups/${t.id}/restore`)
                  setOk('Database restored successfully.')
                })
              }}>Yes, restore this backup</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
