import React, { useState } from 'react'
import { api } from '../api'
import { usePage, Banner } from '../ui'

export default function Settings() {
  return (
    <div className="content">
      <h2 className="page-title">Settings</h2>
      <CompanyCard />
      <ChangePassword />
    </div>
  )
}

function CompanyCard() {
  const { data, error, reload } = usePage(() => api.get('/api/settings'), [])
  const [form, setForm] = useState(null)
  const [err, setErr] = useState('')
  const [ok, setOk] = useState('')

  React.useEffect(() => { if (data && !form) setForm(data.editable || {}) }, [data])

  async function save(e) {
    e.preventDefault(); setErr(''); setOk('')
    try {
      for (const [k, v] of Object.entries(form || {})) {
        if (typeof v === 'string' && k !== 'updatedAt') await api.put(`/api/settings/${k}`, { value: v })
      }
      setOk('Settings saved.'); reload()
    } catch (ex) { setErr(ex.message) }
  }

  if (error) return <div className="panel" style={{ margin: '0 22px' }}><Banner error={error} /></div>
  return (
    <div className="panel" style={{ marginBottom: 16 }}>
      <h3 style={{ marginTop: 0 }}>Company & system</h3>
      <Banner error={err} ok={ok} />
      <form onSubmit={save}>
        <div className="row">
          <label className="field">Company name
            <input value={form?.['company.name'] ?? ''} onChange={(e) => setForm({ ...form, 'company.name': e.target.value })} /></label>
          <label className="field">Application name
            <input value={form?.['app.name'] ?? ''} onChange={(e) => setForm({ ...form, 'app.name': e.target.value })} placeholder="Inventory Manager" /></label>
          <label className="field">Currency code
            <input value={form?.['company.currency'] ?? ''} onChange={(e) => setForm({ ...form, 'company.currency': e.target.value })} /></label>
        </div>
        <div className="row">
          <label className="field">Daily backup time (HH:mm)
            <input value={form?.['backup.time'] ?? ''} onChange={(e) => setForm({ ...form, 'backup.time': e.target.value })} placeholder="02:00" /></label>
          <label className="field">Backup retention count
            <input type="number" min="1" value={form?.['backup.retention.count'] ?? ''} onChange={(e) => setForm({ ...form, 'backup.retention.count': e.target.value })} /></label>
        </div>
        <button type="submit">Save settings</button>
      </form>
    </div>
  )
}

function ChangePassword() {
  const [oldPw, setOld] = useState('')
  const [newPw, setNew] = useState('')
  const [confirm_, setConfirm] = useState('')
  const [err, setErr] = useState('')
  const [ok, setOk] = useState('')

  async function submit(e) {
    e.preventDefault(); setErr(''); setOk('')
    if (newPw !== confirm_) { setErr('New passwords do not match.'); return }
    try {
      await api.post('/api/auth/change-password', { currentPassword: oldPw, newPassword: newPw })
      setOk('Password changed.'); setOld(''); setNew(''); setConfirm('')
    } catch (ex) { setErr(ex.message) }
  }

  return (
    <div className="panel">
      <h3 style={{ marginTop: 0 }}>Change my password</h3>
      <Banner error={err} ok={ok} />
      <form onSubmit={submit}>
        <label className="field">Current password<input type="password" required value={oldPw} onChange={(e) => setOld(e.target.value)} /></label>
        <label className="field">New password<input type="password" required value={newPw} onChange={(e) => setNew(e.target.value)} /></label>
        <label className="field">Repeat new password<input type="password" required value={confirm_} onChange={(e) => setConfirm(e.target.value)} /></label>
        <button type="submit">Change password</button>
      </form>
    </div>
  )
}
