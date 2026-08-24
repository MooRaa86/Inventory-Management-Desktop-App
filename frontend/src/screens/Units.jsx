import React from 'react'
import CrudScreen from './Crud'

export default function Units() {
  return (
    <CrudScreen
      title="Units"
      listUrl="/api/units"
      columns={[
        { header: 'Name', get: (r) => r.name },
        { header: 'Symbol', get: (r) => r.symbol },
      ]}
      fields={[
        { key: 'name', label: 'Name', required: true },
        { key: 'symbol', label: 'Symbol', required: true },
      ]}
      toForm={(e) => ({ name: e.name, symbol: e.symbol })}
    />
  )
}
