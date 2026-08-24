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

  return (
    <div className="login-wrap">
      <form className="login-card" onSubmit={submit}>
        <h1>Inventory Manager</h1>
        <div className="sub">Offline warehouse management system</div>
        {error && <div className="err-banner">{error}</div>}
        <label>Username or email</label>
        <input autoFocus value={usernameOrEmail} onChange={(e) => setUsername(e.target.value)} />
        <label>Password</label>
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
        <button disabled={busy || !usernameOrEmail || !password}>{busy ? 'Signing in…' : 'Sign in'}</button>
      </form>
    </div>
  )
}
