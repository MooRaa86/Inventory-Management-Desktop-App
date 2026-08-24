import React from 'react'
import CrudScreen from './Crud'

export default function Suppliers() {
  return (
    <CrudScreen
      title="Suppliers"
      listUrl="/api/suppliers"
      columns={[
        { header: 'Name', get: (r) => <b>{r.name}</b> },
        { header: 'Phone', get: (r) => r.phone },
        { header: 'Email', get: (r) => r.email },
        { header: 'Address', get: (r) => r.address },
        { header: 'Tax #', get: (r) => r.taxNumber },
      ]}
      fields={[
        { key: 'name', label: 'Name *', required: true },
        { key: 'phone', label: 'Phone' },
        { key: 'email', label: 'Email', type: 'email' },
        { key: 'address', label: 'Address' },
        { key: 'taxNumber', label: 'Tax number' },
        { key: 'notes', label: 'Notes', type: 'textarea' },
      ]}
      toForm={(e) => ({
        name: e.name, phone: e.phone || '', email: e.email || '',
        address: e.address || '', taxNumber: e.taxNumber || '', notes: e.notes || '',
      })}
    />
  )
}
