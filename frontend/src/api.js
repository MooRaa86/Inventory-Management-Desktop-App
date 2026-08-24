const TOKEN_KEY = 'ims_token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(t) {
  t ? localStorage.setItem(TOKEN_KEY, t) : localStorage.removeItem(TOKEN_KEY)
}

async function request(method, url, body, raw = false) {
  const headers = {}
  const token = getToken()
  if (token) headers['Authorization'] = `Bearer ${token}`
  let opts = { method, headers }
  if (body !== undefined && !(body instanceof Blob)) {
    headers['Content-Type'] = 'application/json'
    opts.body = JSON.stringify(body)
  } else if (body instanceof Blob) {
    opts.body = body
  }
  const res = await fetch(url, opts)
  if (res.status === 401 && !url.includes('/auth/login')) {
    setToken(null)
    window.location.hash = '#/login'
    throw new Error('Session expired - please sign in again.')
  }
  if (!res.ok) {
    let msg = `${res.status} ${res.statusText}`
    try {
      const err = await res.json()
      if (err.message) msg = err.message
      if (err.code) msg = `[${err.code}] ${msg}`
    } catch { /* ignore */ }
    throw new Error(msg)
  }
  if (raw) return res
  const ct = res.headers.get('content-type') || ''
  return ct.includes('application/json') ? res.json() : res.text()
}

export const api = {
  get: (u) => request('GET', u),
  post: (u, b) => request('POST', u, b),
  put: (u, b) => request('PUT', u, b),
  del: (u) => request('DELETE', u),
  download: async (u, fallbackName) => {
    const res = await request('GET', u, undefined, true)
    const cd = res.headers.get('content-disposition') || ''
    const m = cd.match(/filename="([^"]+)"/)
    const blob = await res.blob()
    triggerDownload(blob, m ? m[1] : fallbackName)
  },
  postDownload: async (u, body) => {
    const token = getToken()
    const res = await fetch(u, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify(body),
    })
    if (!res.ok) {
      let msg = `${res.status}`
      try { msg = (await res.json()).message || msg } catch { /* ignore */ }
      throw new Error(msg)
    }
    const cd = res.headers.get('content-disposition') || ''
    const m = cd.match(/filename="([^"]+)"/)
    triggerDownload(await res.blob(), m ? m[1] : 'download')
    return null
  },
}

function triggerDownload(blob, name) {
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = name
  a.click()
  URL.revokeObjectURL(a.href)
}

export function qs(params) {
  const p = new URLSearchParams()
  Object.entries(params || {}).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') p.set(k, v)
  })
  const s = p.toString()
  return s ? `?${s}` : ''
}
