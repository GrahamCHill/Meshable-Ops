// SPDX-License-Identifier: LicenseRef-Meshable-Ops-Community-1.0
const result = document.getElementById('result')
const send = document.getElementById('send')
meshableOps.context().then(context => {
  document.getElementById('heading').textContent = context.configuration.heading || 'Custom page'
}).catch(() => { result.textContent = 'The plugin context is unavailable.' })
document.getElementById('greeting-form').addEventListener('submit', async event => {
  event.preventDefault()
  send.disabled = true
  try {
    const response = await meshableOps.backend.request('/greet', {name: document.getElementById('name').value}, 'POST')
    result.textContent = JSON.stringify(response, null, 2)
  } catch (error) { result.textContent = error instanceof Error ? error.message : 'Request failed' }
  finally { send.disabled = false }
})
document.getElementById('guide').addEventListener('click', () => {
  meshableOps.navigate('guide').catch(() => { result.textContent = 'The guide could not open.' })
})
