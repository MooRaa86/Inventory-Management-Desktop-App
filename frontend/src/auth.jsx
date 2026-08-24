import React, { createContext, useContext, useEffect, useState } from 'react'
import { api, setToken, getToken } from './api'

const AuthCtx = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [permissions, setPermissions] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!getToken()) { setLoading(false); return }
    api.get('/api/auth/me')
      .then((me) => { setUser(me); setPermissions(me.permissions || []) })
      .catch(() => setToken(null))
      .finally(() => setLoading(false))
  }, [])

  async function login(usernameOrEmail, password) {
    const res = await api.post('/api/auth/login', { usernameOrEmail, password })
    setToken(res.token)
    const me = await api.get('/api/auth/me')
    setUser(me)
    setPermissions(me.permissions || [])
    return me.mustChangePassword
  }

  async function logout() {
    try { await api.post('/api/auth/logout') } catch { /* ignore */ }
    setToken(null); setUser(null); setPermissions([])
  }

  const can = (perm) => permissions.includes(perm) || user?.roles?.includes('ADMIN')

  return (
    <AuthCtx.Provider value={{ user, permissions, login, logout, can, loading }}>
      {children}
    </AuthCtx.Provider>
  )
}

export function useAuth() {
  return useContext(AuthCtx)
}
