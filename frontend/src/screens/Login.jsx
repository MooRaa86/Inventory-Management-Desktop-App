import React, { useState } from 'react'
import { useNavigate, Navigate } from 'react-router-dom'
import { useAuth } from '../auth'
import { api } from '../api'

export default function Login() {
  const { user, login } = useAuth()
  const nav = useNavigate()
  const [usernameOrEmail, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [forgot, setForgot] = useState(false)
  const [forgotBusy, setForgotBusy] = useState(false)
  const [forgotMsg, setForgotMsg] = useState('')

  if (user) return <Navigate to="/" replace />

  async function submit(e) {
    e.preventDefault()
    setBusy(true); setError('')
    try {
      const mustChange = await login(usernameOrEmail, password)
      nav(mustChange ? '/settings' : '/')
    } catch (err) {
      setError(err.message || 'Login failed')
    } finally {
      setBusy(false)
    }
  }

  async function resetAdmin() {
    setForgotBusy(true); setError('')
    try {
      await api.post('/api/auth/reset-admin')
      setForgotMsg('Your login credentials have been reset to factory defaults. Please contact Omar Medhat at mr.omarmedhat@gmail.com to receive your credentials.')
    } catch (err) {
      setError(err.message || 'Reset failed. Contact the administrator.')
    } finally {
      setForgotBusy(false)
    }
  }

  return (
    <div className="login-wrap">
      <form className="login-card" onSubmit={submit}>
        <img src="/logo.png" alt="Company logo" className="login-logo" />
        <h1>Inventory Manager</h1>
        <div className="sub">Offline warehouse management system</div>
        {error && <div className="err-banner">{error}</div>}
        {forgotMsg && <div className="info-banner" style={{ whiteSpace: 'pre-line' }}>{forgotMsg}</div>}
        {forgot && !forgotMsg && (
          <div className="info-banner">
            Credentials locked or forgotten? We'll reset the default administrator account back to its
            factory settings. Contact <b>Omar Medhat</b> at <b>mr.omarmedhat@gmail.com</b> if you need further help.
          </div>
        )}
        <label>Username or email</label>
        <input autoFocus value={usernameOrEmail} onChange={(e) => setUsername(e.target.value)} />
        <label>Password</label>
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
        {forgot ? (
          <button type="button" className="secondary" disabled={forgotBusy} onClick={resetAdmin}>
            {forgotBusy ? 'Resetting…' : 'Reset administrator credentials'}
          </button>
        ) : (
          <button className="linklike" type="button" style={{ marginTop: 8 }} onClick={() => setForgot(true)}>
            Forgot password?
          </button>
        )}
        <button type="submit" disabled={busy || !usernameOrEmail || !password}>{busy ? 'Signing in…' : 'Sign in'}</button>
      </form>
    </div>
  )
}
