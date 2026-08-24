import React, { useState } from 'react'
import { api, qs } from '../api'
import { usePage, Banner, Badge, Modal, Pager, fmtDate } from '../ui'

export default function Users() {
  const [search, setSearch] = useState('')
  const list = usePage(() => api.get(`/api/users${qs({ search })}`), [search])
  const { data: rolesData } = usePage(() => api.get('/api/users/roles'), [])
  const roles = rolesData || []

  const [editing, setEditing] = useState(null)   // create/edit profile
  const [pwTarget, setPwTarget] = useState(null)
  const [roleTarget, setRoleTarget] = useState(null)
  const [formError, setFormError] = useState('')

  async function saveUser(e) {
    e.preventDefault(); setFormError('')
    try {
      if (editing.id && editing._profileOnly) {
        await api.put(`/api/users/${editing.id}`, { email: editing.email, fullName: editing.fullName })
      } else if (editing.id) {
        await api.put(`/api/users/${editing.id}`, { email: editing.email, fullName: editing.fullName })
      } else {
        await api.post('/api/users', {
          username: editing.username, email: editing.email, fullName: editing.fullName,
          password: editing.password, roles: editing.roles,
        })
      }
      setEditing(null); list.reload()
    } catch (err) { setFormError(err.message) }
  }

  async function resetPw(e) {
    e.preventDefault(); setFormError('')
    try {
      await api.put(`/api/users/${pwTarget.id}/password`,
        { newPassword: pwTarget.newPassword, mustChangePassword: true })
      setPwTarget(null)
    } catch (err) { setFormError(err.message) }
  }

  async function saveRoles(e) {
    e.preventDefault(); setFormError('')
    try {
      await api.put(`/api/users/${roleTarget.id}/roles`, { roles: roleTarget.selectedRoles })
      setRoleTarget(null); list.reload()
    } catch (err) { setFormError(err.message) }
  }

  async function setActive(u, value) {
    try { await api.put(`/api/users/${u.id}/active?value=${value}`); list.reload() }
    catch (err) { alert(err.message) }
  }

  return (
    <div className="content">
      <h2 className="page-title">Users & Roles</h2>
      <Banner error={list.error} />
      <div className="panel">
        <div className="toolbar">
          <input placeholder="Search…" value={search} onChange={(e) => setSearch(e.target.value)} style={{ width: 220 }} />
          <div className="spacer" />
          <button onClick={() => { setEditing({ username: '', email: '', fullName: '', password: '', roles: ['VIEWER'] }); setFormError('') }}>+ New user</button>
        </div>
        <table className="grid">
          <thead><tr><th>Username</th><th>Name</th><th>Email</th><th>Roles</th><th>Status</th><th>Last login</th><th></th></tr></thead>
          <tbody>
            {(list.data?.content || []).map((u) => (
              <tr key={u.id}>
                <td><b>{u.username}</b>{u.mustChangePassword && <span title="must change password"> ⚠️</span>}</td>
                <td>{u.fullName}</td><td>{u.email}</td>
                <td>{(u.roles || []).map((r) => <span key={r} className="badge blue" style={{ marginRight: 4 }}>{r}</span>)}</td>
                <td><Badge value={u.active ? 'ACTIVE' : 'INACTIVE'} /></td>
                <td className="muted">{fmtDate(u.lastLoginAt)}</td>
                <td style={{ whiteSpace: 'nowrap' }}>
                  <button className="secondary small" onClick={() => { setEditing({ ...u, password: undefined }); setFormError('') }}>Edit</button>{' '}
                  <button className="secondary small" onClick={() => { setRoleTarget({ ...u, selectedRoles: u.roles }); setFormError('') }}>Roles</button>{' '}
                  <button className="secondary small" onClick={() => { setPwTarget({ ...u, newPassword: '' }); setFormError('') }}>Reset PW</button>{' '}
                  <button className={u.active ? 'danger small' : 'small'} onClick={() => setActive(u, !u.active)}>{u.active ? 'Disable' : 'Enable'}</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="panel" style={{ marginTop: 16 }}>
        <h3 style={{ marginTop: 0 }}>Role → permissions matrix</h3>
        <table className="grid">
          <thead><tr><th>Role</th><th>Description</th><th>Permissions</th></tr></thead>
          <tbody>
            {roles.map((r) => (
              <tr key={r.name}><td><b>{r.name}</b></td><td>{r.description}</td>
                <td style={{ fontSize: 12 }}>{(r.permissions || []).join(', ')}</td></tr>
            ))}
          </tbody>
        </table>
      </div>

      {editing && (
        <Modal title={editing.id ? `Edit ${editing.username}` : 'New user'} onClose={() => setEditing(null)}>
          <form onSubmit={saveUser}>
            <Banner error={formError} />
            {!editing.id && <label className="field">Username *<input required value={editing.username} onChange={(e) => setEditing({ ...editing, username: e.target.value })} /></label>}
            <label className="field">Full name *<input required value={editing.fullName || ''} onChange={(e) => setEditing({ ...editing, fullName: e.target.value })} /></label>
            <label className="field">Email *<input type="email" required value={editing.email || ''} onChange={(e) => setEditing({ ...editing, email: e.target.value })} /></label>
            {!editing.id && <label className="field">Initial password * (user must change at first login)<input required value={editing.password} onChange={(e) => setEditing({ ...editing, password: e.target.value })} /></label>}
            {!editing.id && (
              <label className="field">Roles *
                <select multiple size={Math.min(4, roles.length)} value={editing.roles}
                        onChange={(e) => setEditing({ ...editing, roles: [...e.target.selectedOptions].map((o) => o.value) })}
                        style={{ height: 90 }}>
                  {roles.map((r) => <option key={r.name} value={r.name}>{r.name}</option>)}
                </select></label>
            )}
            <div className="modal-actions">
              <button type="button" className="secondary" onClick={() => setEditing(null)}>Cancel</button>
              <button type="submit">Save</button>
            </div>
          </form>
        </Modal>
      )}

      {roleTarget && (
        <Modal title={`Roles for ${roleTarget.username}`} onClose={() => setRoleTarget(null)}>
          <form onSubmit={saveRoles}>
            <Banner error={formError} />
            {roles.map((r) => (
              <label key={r.name} style={{ display: 'block', margin: '6px 0', fontSize: 14 }}>
                <input type="checkbox" checked={roleTarget.selectedRoles.includes(r.name)}
                       onChange={(e) => setRoleTarget({
                         ...roleTarget,
                         selectedRoles: e.target.checked
                           ? [...roleTarget.selectedRoles, r.name]
                           : roleTarget.selectedRoles.filter((x) => x !== r.name),
                       })} />{' '}
                <b>{r.name}</b> — <span className="muted">{r.description}</span>
              </label>
            ))}
            <div className="modal-actions">
              <button type="button" className="secondary" onClick={() => setRoleTarget(null)}>Cancel</button>
              <button type="submit">Save roles</button>
            </div>
          </form>
        </Modal>
      )}

      {pwTarget && (
        <Modal title={`Reset password for ${pwTarget.username}`} onClose={() => setPwTarget(null)}>
          <form onSubmit={resetPw}>
            <Banner error={formError} />
            <label className="field">New password * (min 8 chars, letters + digits)
              <input required value={pwTarget.newPassword} onChange={(e) => setPwTarget({ ...pwTarget, newPassword: e.target.value })} /></label>
            <p className="muted">The user will be forced to change this password at next login.</p>
            <div className="modal-actions">
              <button type="button" className="secondary" onClick={() => setPwTarget(null)}>Cancel</button>
              <button type="submit">Reset password</button>
            </div>
          </form>
        </Modal>
      )}
    </div>
  )
}
