import {createServer} from 'vite'
import react from '@vitejs/plugin-react'
const server = await createServer({configFile:false, plugins:[react(), {name:'test-ai-api', configureServer(server) {
  server.middlewares.use(async (request,response,next) => {
    if (!request.url?.startsWith('/api/admin/ai/')) return next()
    const chunks=[]; for await (const chunk of request) chunks.push(chunk)
    const {endpoint} = JSON.parse(Buffer.concat(chunks).toString())
    response.setHeader('Content-Type','application/json')
    if (endpoint.apiKey === 'bad-key') {response.statusCode=503; response.end(JSON.stringify({message:'The AI provider rejected authentication. Check the configured key and model access.'})); return}
    if (request.url.includes('/models/')) {
      const items = endpoint.apiKey === 'slow-key' ? [{id:'old-model',name:'Old model'}] : [{id:'native-3072',name:'Native 3072 model'},{id:'incompatible-384',name:'384-only model'}]
      if(endpoint.apiKey === 'slow-key') await new Promise(resolve => setTimeout(resolve,2200))
      response.end(JSON.stringify({models:items,message:'Choose an available model or enter a name manually.',truncated:false})); return
    }
    if (endpoint.model === 'incompatible-384') response.end(JSON.stringify({status:'INCOMPATIBLE',model:endpoint.model,requiredDimensions:1536,actualDimensions:384,message:'This model returned 384 dimensions; search requires 1536. Choose another embedding model.'}))
    else if (!endpoint.sendDimensions) response.end(JSON.stringify({status:'SETTING_CHANGE_REQUIRED',model:endpoint.model,requiredDimensions:1536,actualDimensions:1536,suggestedSendDimensions:true,message:'Enable Request 1536 dimensions: the model returned 1536 values with that setting. Apply the suggested setting before saving.'}))
    else response.end(JSON.stringify({status:'COMPATIBLE',model:endpoint.model,requiredDimensions:1536,actualDimensions:1536,message:'Verified: this model returned 1536 dimensions with the current settings. No storage documents were sent.'}))
  })
}}], server:{host:'127.0.0.1',port:5199,strictPort:true}})
await server.listen(); console.log('Model picker test server: http://127.0.0.1:5199/.ai-model-test/')
