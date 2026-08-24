import React from 'react'
import { HashRouter, Routes, Route, NavLink, Navigate, useNavigate } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth'
import { api } from './api'
import { usePage } from './ui'
import Login from './screens/Login'
import Dashboard from './screens/Dashboard'
import Products from './screens/Products'
import Categories from './screens/Categories'
import Units from './screens/Units'
import Stock from './screens/Stock'
import Suppliers from './screens/Suppliers'
import Purchases from './screens/Purchases'
import Issues from './screens/Issues'
import Reports from './screens/Reports'
import Users from './screens/Users'
import AuditLogs from './screens/AuditLogs'
import Backups from './screens/Backups'
import SettingsScreen from './screens/Settings'
import Guide from './screens/Guide'

class ErrorBoundary extends React.Component {
  constructor(props) { super(props); this.state = { error: null } }
  static getDerivedStateFromError(error) { return { error } }
  render() {
    if (this.state.error) {
      return (
        <div className="content">
          <div className="err-banner">
            <b>Something went wrong.</b><br />
            {this.state.error.message || String(this.state.error)}
          </div>
          <button className="secondary" onClick={() => this.setState({ error: null })}>Try again</button>
        </div>
      )
    }
    return this.props.children
  }
}

function Sidebar() {
  const { user, can, logout } = useAuth()
  const nav = useNavigate()
  const { data: settings } = usePage(() => api.get('/api/settings'), [])
  const appName = settings?.editable?.['app.name'] || settings?.['app.name'] || 'Inventory Manager'
  const Item = ({ to, perm, children }) =>
    (!perm || can(perm)) ? <NavLink className="navlink" to={to}>{children}</NavLink> : null

  return (
    <div className="sidebar">
      <div className="brand">📦 {appName}</div>
      <Item to="/" >Dashboard</Item>
      <div className="navsection">Inventory</div>
      <Item to="/products" perm="PRODUCT_VIEW">Products</Item>
      <Item to="/stock" perm="STOCK_VIEW">Stock Operations</Item>
      <Item to="/categories" perm="CATEGORY_VIEW">Categories</Item>
      <Item to="/units" perm="UNIT_VIEW">Units</Item>
      <div className="navsection">Purchasing</div>
      <Item to="/suppliers" perm="SUPPLIER_VIEW">Suppliers</Item>
      <Item to="/purchases" perm="PURCHASE_VIEW">Purchases</Item>
      <Item to="/issues" perm="ISSUE_VIEW">Issues</Item>
      <div className="navsection">Insights</div>
      <Item to="/reports" perm="REPORT_VIEW">Reports</Item>
      <Item to="/audit" perm="AUDIT_VIEW">Audit Log</Item>
      <div className="navsection">Administration</div>
      <Item to="/users" perm="USER_VIEW">Users & Roles</Item>
      <Item to="/backups" perm="BACKUP_VIEW">Backups</Item>
      <Item to="/settings" perm="SETTINGS_EDIT">Settings</Item>
      <div className="navsection">Help</div>
      <Item to="/guide">User Guide</Item>
      <div style={{ flex: 1 }} />
      <div style={{ padding: '8px 10px', fontSize: 12.5 }}>
        {user?.fullName}<div className="muted" style={{ color: '#7d90a5' }}>{user?.username}</div>
      </div>
      <button className="secondary small" onClick={async () => { await logout(); nav('/login') }}>Sign out</button>
    </div>
  )
}

function Shell() {
  const { user, loading } = useAuth()
  if (loading) return <div style={{ padding: 40 }}>Loading…</div>
  if (!user) return <Navigate to="/login" replace />
  return (
    <div className="layout">
      <Sidebar />
      <div className="main">
        <ErrorBoundary>
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/products" element={<Products />} />
          <Route path="/categories" element={<Categories />} />
          <Route path="/units" element={<Units />} />
          <Route path="/stock" element={<Stock />} />
          <Route path="/suppliers" element={<Suppliers />} />
          <Route path="/purchases" element={<Purchases />} />
          <Route path="/issues" element={<Issues />} />
          <Route path="/reports" element={<Reports />} />
          <Route path="/users" element={<Users />} />
          <Route path="/audit" element={<AuditLogs />} />
          <Route path="/backups" element={<Backups />} />
          <Route path="/settings" element={<SettingsScreen />} />
          <Route path="/guide" element={<Guide />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
        </ErrorBoundary>
      </div>
    </div>
  )
}

export default function App() {
  return (
    <AuthProvider>
      <HashRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/*" element={<Shell />} />
        </Routes>
      </HashRouter>
    </AuthProvider>
  )
}
