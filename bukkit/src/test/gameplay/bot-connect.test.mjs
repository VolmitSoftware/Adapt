import test from 'node:test'
import assert from 'node:assert/strict'
import { EventEmitter } from 'node:events'
import { existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { resilientCreateBot } from './bot-connect.mjs'

const multiplexor = process.env.ADAPT_QA_MULTIPLEXOR ?? join(dirname(fileURLToPath(import.meta.url)), '../../../../../ServerMultiplexor')
const actorsModule = join(multiplexor, 'MultiplexorApp/tool/mineflayer/src/scenario_actors.mjs')
const settings = { stallMs: 60, pollMs: 5, settleMs: 5 }

/** A fake mineflayer bot whose connection follows the given script: 'stall' stays in configuration, 'spawn' spawns, 'kick' sends a disconnect packet. */
function fakeServer(script) {
    const bots = []
    const createBot = options => {
        const bot = new EventEmitter()
        const client = new EventEmitter()
        const behaviour = script[bots.length] ?? 'spawn'
        Object.assign(bot, { username: options.username, version: '26.1', _client: client, plugins: [], ended: false })
        client.state = 'login'
        bot.loadPlugin = plugin => { bot.plugins.push(plugin) }
        bot.end = reason => { if (!bot.ended) { bot.ended = true; setImmediate(() => bot.emit('end', reason ?? 'socketClosed')) } }
        bots.push(bot)
        setTimeout(() => {
            client.state = 'configuration'
            client.emit('state', 'configuration', 'login')
            if (behaviour === 'spawn') setTimeout(() => { client.state = 'play'; bot.emit('spawn') }, 5)
            if (behaviour === 'kick') setTimeout(() => {
                client.emit('packet', {}, { name: 'disconnect', state: 'configuration' })
                bot.emit('kicked', 'Banned for testing')
                bot.end('kicked')
            }, 5)
        }, 2)
        return bot
    }
    return { bots, createBot }
}

async function manager(createBot) {
    const { createActorManager } = await import(pathToFileURL(actorsModule))
    const failures = []
    const abort = new AbortController()
    const plugin = () => {}
    const actors = createActorManager({
        configuration: { host: '127.0.0.1', port: 1, auth: 'offline', version: '26.1', connectTimeoutMs: 2000 },
        report: { messages: [] },
        signal: abort.signal,
        fail: error => { failures.push(error.message); abort.abort(error) },
        runtime: { createBot, install: bot => bot.loadPlugin(plugin), configure: () => {} },
    })
    return { actors, failures, plugin }
}

const skip = existsSync(actorsModule) ? false : `Multiplexor actor manager missing: ${actorsModule}`

test('a connect stalled in the configuration phase is torn down and reconnected before the scenario sees it', { skip }, async () => {
    const server = fakeServer(['stall', 'spawn'])
    const lines = []
    const { actors, failures, plugin } = await manager(resilientCreateBot(server.createBot, { ...settings, log: line => lines.push(line) }))
    const bot = await actors.connect('AQAStall', { primary: true })
    await new Promise(resolve => setTimeout(resolve, 20))
    assert.equal(server.bots.length, 2)
    assert.equal(server.bots[0].ended, true)
    assert.deepEqual(failures, [], 'the abandoned attempt\'s end never reaches the actor manager')
    assert.deepEqual(server.bots.map(candidate => candidate.plugins), [[plugin], [plugin]], 'plugins move to the new attempt')
    assert.equal(lines.length, 1)
    assert.match(lines[0], /^RETRY connect AQAStall: no spawn after \d+ s in the configuration phase, reconnecting \(attempt 2\/3\)$/)
    assert.equal(server.bots[0].listenerCount('messagestr'), 0)
    assert.equal(server.bots[1].listenerCount('messagestr'), 1, 'listeners registered before spawn live on the new attempt')
    server.bots[1].emit('end', 'socketClosed')
    assert.deepEqual(failures, ['AQAStall disconnected: socketClosed'], 'a disconnect after spawn is still fatal')
    assert.equal(bot.username, 'AQAStall')
})

test('a kick during configuration is reported, not retried', { skip }, async () => {
    const server = fakeServer(['kick'])
    const lines = []
    const { actors, failures } = await manager(resilientCreateBot(server.createBot, { ...settings, log: line => lines.push(line) }))
    await assert.rejects(actors.connect('AQAKicked', { primary: true }))
    assert.equal(server.bots.length, 1)
    assert.deepEqual(lines, [])
    assert.equal(failures[0], 'AQAKicked kicked: "Banned for testing"')
})

test('the last attempt is left to fail like a plain connect', { skip }, async () => {
    const server = fakeServer(['stall', 'stall', 'stall'])
    const lines = []
    const { actors } = await manager(resilientCreateBot(server.createBot, { ...settings, log: line => lines.push(line) }))
    await assert.rejects(actors.connect('AQAStuck', { primary: true }), /AQAStuck spawn timed out after 2000ms/)
    assert.equal(server.bots.length, 3)
    assert.equal(lines.length, 2)
    assert.equal(server.bots[2].ended, false, 'the third attempt is not torn down by the wrapper')
})

test('listeners added after spawn and removals pass straight to the live bot', async () => {
    const server = fakeServer(['spawn'])
    const bot = resilientCreateBot(server.createBot, settings)({ username: 'AQAPlain' })
    await new Promise(resolve => bot.once('spawn', resolve))
    const listener = () => {}
    bot.on('health', listener)
    assert.equal(server.bots[0].listeners('health')[0], listener)
    bot.removeListener('health', listener)
    assert.equal(server.bots[0].listenerCount('health'), 0)
    bot.chatted = true
    assert.equal(server.bots[0].chatted, true)
    assert.equal(server.bots.length, 1)
})
