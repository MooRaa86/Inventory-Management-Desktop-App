import React, { useState } from 'react'
import { api, qs } from '../api'
import { usePage, Banner, Modal } from '../ui'

export default function CrudScreen({ title, listUrl, columns, fields, toForm }) {
  const [search, setSearch] = useState('')
  const { data, error, reload } = usePage(() => api.get(`${listUrl}${qs({ search, size: 200 })}`), [search])
  const [editing, setEditing] = useState(null)
  const [formError, setFormError] = useState('')

  async function save(e) {
    e.preventDefault(); setFormError('')
    const body = toForm(editing)
    try {
      if (editing.id) await api.put(`${listUrl}/${editing.id}`, body)
      else await api.post(listUrl, body)
      setEditing(null); reload()
    } catch (err) { setFormError(err.message) }
  }

  async function remove(row) {
    if (!confirm(`Delete "${row.name}"? This fails if it is still in use.`)) return
    try { await api.del(`${listUrl}/${row.id}`); reload() }
    catch (err) { alert(err.message) }
  }

  return (
    <div className="content">
      <h2 className="page-title">{title}</h2>
      <Banner error={error} />
      <div className="panel">
        <div className="toolbar">
          <input placeholder="Search…" value={search} onChange={(e) => setSearch(e.target.value)} style={{ width: 240 }} />
          <div className="spacer" />
          <button onClick={() => { setEditing({ active: true }); setFormError('') }}>+ New</button>
        </div>
        <table className="grid">
          <thead><tr>{columns.map((c) => <th key={c.header}>{c.header}</th>)}<th></th></tr></thead>
          <tbody>
            {(data?.content || []).map((row) => (
              <tr key={row.id} style={row.active === false ? { opacity: .5 } : undefined}>
                {columns.map((c) => <td key={c.header}>{c.get(row)}</td>)}
                <td style={{ whiteSpace: 'nowrap' }}>
                  <button className="secondary small" onClick={() => { setEditing({ ...row }); setFormError('') }}>Edit</button>{' '}
                  <button className="danger small" onClick={() => remove(row)}>Delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {editing && (
        <Modal title={editing.id ? 'Edit' : `New ${title.toLowerCase().replace(/s$/, '')}`} onClose={() => setEditing(null)}>
          <form onSubmit={save}>
            <Banner error={formError} />
            {fields.map((f) => (
              <label className="field" key={f.key}>{f.label}
                {f.type === 'textarea'
                  ? <textarea rows={2} value={editing[f.key] ?? ''} onChange={(e) => setEditing({ ...editing, [f.key]: e.target.value })} />
                  : <input type={f.type || 'text'} required={f.required} value={editing[f.key] ?? ''} onChange={(e) => setEditing({ ...editing, [f.key]: e.target.value })} />}
              </label>
            ))}
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
