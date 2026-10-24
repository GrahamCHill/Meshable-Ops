// Build using the repository's already installed frontend toolchain; the ZIP has no host imports.
import {createRequire} from 'node:module'
import {fileURLToPath} from 'node:url'
import {dirname, resolve} from 'node:path'
const root = dirname(fileURLToPath(import.meta.url))
const require = createRequire(resolve(root, '../../../frontend/package.json'))
const {build} = require('esbuild')
await build({entryPoints: [resolve(root, 'frontend/App.tsx')], bundle: true, format: 'iife', minify: true,
  outfile: resolve(root, 'target/app.js'), nodePaths: [resolve(root, '../../../frontend/node_modules')],
  define: {'process.env.NODE_ENV': '"production"'}, target: ['es2020']})
