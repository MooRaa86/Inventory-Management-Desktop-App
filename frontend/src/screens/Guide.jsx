import React from 'react'
import { api } from '../api'
import { usePage } from '../ui'

export default function Guide() {
  const { data: settings } = usePage(() => api.get('/api/settings'), [])
  const appName = settings?.editable?.['app.name'] || settings?.['app.name'] || 'Inventory Manager'

  return (
    <div className="content">
      <h2 className="page-title">User Guide — {appName}</h2>

      <div className="panel" style={{ marginBottom: 16 }}>
        <h3 style={{ marginTop: 0 }}>1. Dashboard</h3>
        <p>The dashboard gives you a live overview of your warehouse:</p>
        <ul>
          <li><b>Summary cards</b> — total products, stock quantities, low/out-of-stock alerts, suppliers, pending purchases</li>
          <li><b>Today &amp; Month stats</b> — stock in/out for today and the current month</li>
          <li><b>Low stock alerts</b> — products below their minimum stock level that need reordering</li>
          <li><b>Recent movements</b> — the latest stock in, stock out, and adjustment transactions</li>
        </ul>
        <p>Use the <b>↻ Refresh</b> button in the top-right to reload the dashboard.</p>
      </div>

      <div className="panel" style={{ marginBottom: 16 }}>
        <h3 style={{ marginTop: 0 }}>2. Products</h3>
        <p>Manage your product catalog:</p>
        <ul>
          <li><b>+ New product</b> — enter name, category, unit, supplier, min/max stock, cost/selling prices, and an optional opening stock quantity</li>
          <li><b>Edit</b> — modify product details (only while the product is active)</li>
          <li><b>Deactivate</b> — hides the product from dropdowns but keeps all history. Use this for products you no longer stock</li>
          <li><b>Delete</b> — permanently removes the product. Only works if the product has no stock movements, purchases, or issues. If it has history, you'll see a message explaining what's blocking deletion</li>
          <li><b>Search</b> — filter products by name</li>
          <li><b>Status filter</b> — show only In Stock, Low Stock, or Out of Stock products</li>
        </ul>
      </div>

      <div className="panel" style={{ marginBottom: 16 }}>
        <h3 style={{ marginTop: 0 }}>3. Stock Operations</h3>
        <p>Record stock changes that don't come from purchases or issues:</p>
        <ul>
          <li><b>Stock In</b> — receive goods without a purchase order (returns, donations, found items). Requires a reference/note</li>
          <li><b>Stock Out</b> — remove stock without an issue note (damage, loss, samples). Requires a reason</li>
          <li><b>Adjust</b> — correct stock after a physical count. Set the counted quantity and direction (IN/OUT). Requires a reason</li>
        </ul>
        <p>The <b>Ledger</b> table below shows every stock movement with before/after quantities and who performed it.</p>
      </div>

      <div className="panel" style={{ marginBottom: 16 }}>
        <h3 style={{ marginTop: 0 }}>4. Categories &amp; Units</h3>
        <ul>
          <li><b>Categories</b> — group products (e.g. Electronics, Food, Furniture). Products can have one category or none</li>
          <li><b>Units</b> — define units of measure (Piece, Kg, Liter, Box, etc.). Every product must have a unit</li>
        </ul>
      </div>

      <div className="panel" style={{ marginBottom: 16 }}>
        <h3 style={{ marginTop: 0 }}>5. Suppliers</h3>
        <p>Keep a directory of your suppliers with contact details, tax numbers, and notes. Assign a default supplier to products to auto-fill when creating purchases.</p>
      </div>

      <div className="panel" style={{ marginBottom: 16 }}>
        <h3 style={{ marginTop: 0 }}>6. Purchases</h3>
        <p>Track incoming stock from suppliers:</p>
        <ul>
          <li><b>+ New purchase</b> — select a supplier, add product lines with quantities and unit costs</li>
          <li><b>Create (PENDING)</b> — saves the purchase as a draft. No stock is changed yet</li>
          <li><b>Receive</b> — marks the purchase as received and <b>adds the quantities to stock</b> automatically</li>
          <li><b>Cancel</b> — cancels a pending purchase (no stock effect)</li>
        </ul>
      </div>

      <div className="panel" style={{ marginBottom: 16 }}>
        <h3 style={{ marginTop: 0 }}>7. Issues (Stock Out to Departments)</h3>
        <p>Issue goods to internal departments or companies:</p>
        <ul>
          <li><b>+ New issue</b> — enter department, requester, and product lines with quantities</li>
          <li><b>Create draft</b> — saves as DRAFT (no stock change)</li>
          <li><b>Approve</b> — moves to APPROVED status (still no stock change)</li>
          <li><b>Complete</b> — deducts the quantities from stock</li>
          <li><b>Cancel</b> — cancels at any stage before completion</li>
        </ul>
      </div>

      <div className="panel" style={{ marginBottom: 16 }}>
        <h3 style={{ marginTop: 0 }}>8. Reports</h3>
        <p>Generate reports in multiple formats:</p>
        <ul>
          <li><b>Inventory</b> — all products with stock levels, costs, and total value</li>
          <li><b>Low Stock</b> — products below minimum with shortage amounts</li>
          <li><b>Movements</b> — full movement history with dates and users</li>
          <li><b>Purchases</b> — purchase history with totals</li>
          <li><b>Issues</b> — issue history</li>
          <li><b>Suppliers</b> — supplier directory</li>
          <li><b>Audit</b> — complete audit trail of all actions</li>
        </ul>
        <p>Choose a format: <b>JSON</b> (inline preview), <b>CSV</b> (Excel), <b>XLSX</b> (Excel native), or <b>PDF</b>.</p>
      </div>

      <div className="panel" style={{ marginBottom: 16 }}>
        <h3 style={{ marginTop: 0 }}>9. Users &amp; Roles</h3>
        <p>Manage who can access the system:</p>
        <ul>
          <li><b>ADMIN</b> — full access to everything including users, settings, and backups</li>
          <li><b>WAREHOUSE MANAGER</b> — catalog, purchases, issues, reports, backups</li>
          <li><b>WAREHOUSE EMPLOYEE</b> — stock operations, create drafts, view reports</li>
          <li><b>VIEWER</b> — read-only access</li>
        </ul>
        <p>You can create users, assign roles, reset passwords, and enable/disable accounts.</p>
      </div>

      <div className="panel" style={{ marginBottom: 16 }}>
        <h3 style={{ marginTop: 0 }}>10. Backups</h3>
        <p>Protect your data:</p>
        <ul>
          <li><b>Create backup</b> — creates a zip file with your database + metadata</li>
          <li><b>Verify</b> — checks the backup's integrity</li>
          <li><b>Download / Export</b> — save a backup to your computer or a folder</li>
          <li><b>Import</b> — bring in a backup from another installation</li>
          <li><b>Restore</b> — swap your live database with a backup (the app creates a safety backup first)</li>
          <li><b>Automatic backups</b> — configured in Settings (daily at a set time with retention cleanup)</li>
        </ul>
      </div>

      <div className="panel" style={{ marginBottom: 16 }}>
        <h3 style={{ marginTop: 0 }}>11. Audit Log</h3>
        <p>Every action in the system is recorded with the user, timestamp, and details. Filter by username, action type, or date range.</p>
      </div>

      <div className="panel" style={{ marginBottom: 16 }}>
        <h3 style={{ marginTop: 0 }}>12. Settings</h3>
        <ul>
          <li><b>Company name</b> — shown on the dashboard</li>
          <li><b>Application name</b> — shown in the sidebar</li>
          <li><b>Currency code</b> — for display purposes</li>
          <li><b>Backup schedule</b> — enable/disable automatic backups, set time and retention</li>
        </ul>
      </div>

      <div className="panel" style={{ marginBottom: 16 }}>
        <h3 style={{ marginTop: 0 }}>13. Deployment &amp; Migration</h3>
        <p>The application is <b>fully portable</b> — no installation required on the target PC.</p>
        <h3>Folder Structure</h3>
        <pre style={{ background: '#f4f6f8', border: '1px solid #d1d5db', borderRadius: 6, padding: 12, fontSize: 12, lineHeight: 1.5, overflowX: 'auto' }}>{`InventoryManager/
├── start.cmd                          ← Double-click to launch
├── app/
│   ├── main.js                        ← Electron shell
│   └── package.json
├── electron/                          ← Electron binaries
├── runtime/                           ← Bundled Java (no install needed)
│   ├── bin/
│   └── lib/
└── backend/
    ├── inventory-backend.jar          ← The server
    ├── config/
    │   ├── jwt-secret.key
    │   └── initial-admin-credentials.txt
    ├── data/
    │   └── inventory.db               ← ★ YOUR DATABASE
    ├── backups/
    │   ├── manual/
    │   ├── automatic/
    │   └── safety/
    ├── exports/reports/
    └── logs/`}</pre>
        <h3>Where is the database?</h3>
        <p>All your data — products, stock, purchases, issues, users, settings — lives in a single file:</p>
        <p style={{ fontFamily: 'monospace', background: '#f4f6f8', padding: '6px 10px', borderRadius: 4, border: '1px solid #d1d5db', display: 'inline-block' }}>backend/data/inventory.db</p>
        <h3 style={{ marginTop: 12 }}>How to move to another PC</h3>
        <ol>
          <li>Close the application on the current PC</li>
          <li>Copy the entire <code>InventoryManager</code> folder to a USB drive or shared folder</li>
          <li>Paste it on the new PC (e.g., <code>C:\InventoryManager</code>)</li>
          <li>Double-click <code>start.cmd</code> — done!</li>
        </ol>
        <div className="warn" style={{ background: '#fef9e7', borderLeft: '3px solid #f59e0b', padding: '10px 14px', borderRadius: '0 6px 6px 0', marginTop: 10, fontSize: 13 }}>
          <b>Default credentials:</b> Username: <code>admin</code>, Password: <code>admin@omar.com</code>. Change the password after first login.
        </div>
        <h3 style={{ marginTop: 12 }}>Fresh start</h3>
        <p>For a clean installation: copy only <code>app/</code>, <code>electron/</code>, <code>runtime/</code>, and <code>start.cmd</code>. Skip <code>backend/data/</code>. A fresh database is created on first launch.</p>
        <h3 style={{ marginTop: 12 }}>What moves with the folder?</h3>
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13, marginTop: 6 }}>
          <thead><tr style={{ background: '#1e3a5f', color: '#fff' }}><th style={{ border: '1px solid #d1d5db', padding: '6px 10px', textAlign: 'left' }}>Item</th><th style={{ border: '1px solid #d1d5db', padding: '6px 10px', textAlign: 'left' }}>Location</th></tr></thead>
          <tbody>
            <tr><td style={{ border: '1px solid #d1d5db', padding: '6px 10px' }}>All data</td><td style={{ border: '1px solid #d1d5db', padding: '6px 10px', fontFamily: 'monospace' }}>backend/data/inventory.db</td></tr>
            <tr style={{ background: '#f8f9fa' }}><td style={{ border: '1px solid #d1d5db', padding: '6px 10px' }}>Admin password</td><td style={{ border: '1px solid #d1d5db', padding: '6px 10px', fontFamily: 'monospace' }}>backend/config/initial-admin-credentials.txt</td></tr>
            <tr><td style={{ border: '1px solid #d1d5db', padding: '6px 10px' }}>Backups</td><td style={{ border: '1px solid #d1d5db', padding: '6px 10px', fontFamily: 'monospace' }}>backend/backups/</td></tr>
            <tr style={{ background: '#f8f9fa' }}><td style={{ border: '1px solid #d1d5db', padding: '6px 10px' }}>Application code</td><td style={{ border: '1px solid #d1d5db', padding: '6px 10px', fontFamily: 'monospace' }}>app/, electron/, runtime/</td></tr>
          </tbody>
        </table>
      </div>

      <div className="panel" style={{ marginBottom: 16, background: '#eef6ff', borderLeft: '3px solid #3b82f6' }}>
        <h3 style={{ marginTop: 0 }}>Tips</h3>
        <ul style={{ marginBottom: 0 }}>
          <li>All data is stored locally — no internet connection required</li>
          <li>Close the window to stop the app; the next launch reuses the same data</li>
          <li>To move to another PC, copy the entire <code>InventoryManager</code> folder</li>
          <li>Always back up before restoring or upgrading</li>
          <li>Change the admin password after first login</li>
        </ul>
      </div>
    </div>
  )
}
