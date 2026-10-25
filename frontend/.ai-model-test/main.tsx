import React, {useState} from 'react'
import {createRoot} from 'react-dom/client'
import {AiEndpointForm} from '../src/components/admin/ai/AiEndpointForm'
import '../src/styles/global.css'
function Harness() {
  const [value, setValue] = useState({provider:'OPENAI_COMPATIBLE', baseUrl:'https://provider.test/v1', model:'', apiKey:'', clearApiKey:false, sendDimensions:false})
  return <main className="page-stack" style={{maxWidth:900,margin:'40px auto',padding:24}}><h1>App Intelligence: model checks</h1><AiEndpointForm kind="embeddings" source="ADMIN" value={value} configured={false} readOnly={false} onChange={setValue} /></main>
}
createRoot(document.getElementById('root')).render(<Harness />)
