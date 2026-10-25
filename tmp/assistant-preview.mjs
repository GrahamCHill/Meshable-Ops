import { createServer } from '../frontend/node_modules/vite/dist/node/index.js'
import react from '../frontend/node_modules/@vitejs/plugin-react/dist/index.js'
import { appendFile } from 'node:fs/promises'

const user = {id:'preview-admin', username:'preview', email:'preview@example.test', role:'SUPER_ADMIN', canUploadDocuments:true}
const views = [
  {id:'home',label:'Workspace',path:'/',admin:false},
  {id:'search',label:'Search',path:'/search',admin:false},
  {id:'projects',label:'Projects',path:'/projects',admin:false},
  {id:'admin.projects',label:'Project settings',path:'/admin/projects',admin:true},
  {id:'admin.ai',label:'App Intelligence',path:'/admin/ai',admin:true},
]
const cover = {html:'<html><body><h1>{{projectName}}</h1><p>{{period}}</p></body></html>',defaultHtml:'<h1>{{projectName}}</h1>',revision:0,assets:{},aiInsightsEnabled:false,canManage:true}
let failures = 0
const server = await createServer({
  root: new URL('../frontend',import.meta.url).pathname.replace(/^\/([A-Z]:)/,'$1'),
  configFile:false,
  plugins:[react(),{
    name:'local-preview-fixtures',
    transformIndexHtml(html){return {html,tags:[{tag:'script',children:`localStorage.setItem('auth-storage',${JSON.stringify(JSON.stringify({state:{token:'preview-admin:cookie-session',user},version:2}))});`,injectTo:'head-prepend'}]}},
    configureServer(vite){vite.middlewares.use(async(req,res,next)=>{
      const path = req.url?.split('?')[0]
      if (!path?.startsWith('/api/')) return next()
      let body='';for await(const chunk of req) body+=chunk
      const mode = new URL(req.headers.referer||'http://localhost').searchParams.get('previewMode')
      const json = value=>{res.setHeader('Content-Type','application/json');res.end(JSON.stringify(value))}
      if(path==='/api/access/me')return json({features:views.filter(v=>mode!=='restricted'||!v.admin).map(v=>v.id),views,role:mode==='restricted'?'USER':'SUPER_ADMIN'})
      if(path==='/api/auth/session-cookie')return json(user)
      if(path==='/api/branding')return json({logoVersion:null,faviconVersion:null,loginImages:[],settings:{applicationName:'Meshable Ops',themes:{switchingEnabled:true,fixedMode:'LIGHT',light:{},dark:{},custom:{}},loginBackgroundMode:'NONE',slideshowSeconds:8,revision:0}})
      if(path==='/api/navigation')return json({preferences:{order:[],submenus:{},revision:0},items:views.filter(v=>!v.admin).map(v=>({id:v.id,label:v.label,path:v.path,kind:'CORE',feature:v.id,section:'',children:[]}))})
      if(path==='/api/navigation/admin-sections')return json({})
      if(path==='/api/search/status')return json({enabled:mode!=='search',indexContent:true,searchEnabled:true,launcherEnabled:true,embeddingProvider:'LOCAL',dimensions:384})
      if(path==='/api/search/ask'){
        const request=JSON.parse(body)
        await appendFile(new URL('./assistant-preview-requests.jsonl',import.meta.url),JSON.stringify(request)+'\n')
        if(request.query==='Try failure'&&failures++===0){res.statusCode=503;return json({message:'Preview service unavailable. Try again.'})}
        return setTimeout(()=>json({answer:request.history?.length?'The contract requires 30 days’ written notice before renewal [1].':'Your workspace contract renews on 31 December [1].',grounded:true,model:'preview-fixture',sources:[{number:1,pointId:'contract-chunk',documentId:'contract',folderId:'contracts',title:'Workspace contract.pdf',path:'contracts/workspace-contract.pdf',excerpt:'The contract renews on 31 December. Cancellation requires 30 days’ written notice.',score:0.9}]}),300)
      }
      if(path==='/api/search')return json({mode:'KEYWORD',results:[{pointId:'report',score:1,payload:{title:'Monthly project report',entityType:'document',storagePath:'reports/monthly.pdf',folderId:'reports'}}]})
      if(path==='/api/admin/projects/report-cover')return json(cover)
      if(['/api/projects','/api/plugins','/api/admin/plugins'].includes(path))return json([])
      res.statusCode=404;json({message:'No preview fixture for '+path})
    })},
  }],
  server:{host:'127.0.0.1',port:5178,strictPort:true},
})
await server.listen()
console.log('Preview with mock API responses: http://127.0.0.1:5178/admin')
