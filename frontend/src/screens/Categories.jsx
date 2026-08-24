import React from 'react'
import CrudScreen from './Crud'

export default function Categories() {
  return (
    <CrudScreen
      title="Categories"
      listUrl="/api/categories"
      columns={[
        { header: 'Name', get: (r) => r.name },
        { header: 'Description', get: (r) => r.description },
      ]}
      fields={[
        { key: 'name', label: 'Name', required: true },
        { key: 'description', label: 'Description', type: 'textarea' },
      ]}
      toForm={(e) => ({ name: e.name, description: e.description || '' })}
    />
  )
}
