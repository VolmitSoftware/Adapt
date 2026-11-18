import assert from 'node:assert/strict'
import { EventEmitter } from 'node:events'
import test from 'node:test'
import { holdWindowsUntilFilled, releaseClient, resynchronizeInventory } from './client-reset.mjs'

function fakeBot({ answers = [] } = {}) {
    const bot = new EventEmitter()
    const closed = []
    bot.username = 'AQAa'
    bot.inventory = { id: 0, selectedItem: null }
    bot.currentWindow = null
    bot.clearControlStates = () => { bot.cleared = true }
    bot.deactivateItem = () => { bot.deactivated = true }
    bot.closeWindow = window => { closed.push(window.id); bot.currentWindow = null }
    bot._syncWindow = () => {
        const answer = answers.shift()
        if (answer === undefined) return new Promise(() => {})
        answer(bot)
        return Promise.resolve()
    }
    return { bot, closed }
}

test('releasing a client closes its window and drops listeners of windows that never got contents', () => {
    const { bot, closed } = fakeBot()
    bot.currentWindow = { id: 7 }
    let stale = 0
    let inventory = 0
    bot.once('setWindowItems:1', () => { stale++ })
    bot.on('setWindowItems:0', () => { inventory++ })
    releaseClient(bot)
    bot.emit('setWindowItems:1')
    bot.emit('setWindowItems:0')
    assert.deepEqual(closed, [7])
    assert.equal(stale, 0)
    assert.equal(inventory, 1)
    assert.ok(bot.cleared && bot.deactivated)
})

test('resynchronizing retries until the server answers with no window and an empty cursor', async () => {
    const { bot, closed } = fakeBot({ answers: [
        current => { current.currentWindow = { id: 3 } },
        () => {},
    ] })
    await resynchronizeInventory({}, bot, { timeoutMs: 1000, attemptMs: 20 })
    assert.deepEqual(closed, [3])
    assert.equal(bot.currentWindow, null)
})

test('resynchronizing fails when the server never confirms a closed container', async () => {
    const { bot } = fakeBot()
    await assert.rejects(resynchronizeInventory({}, bot, { timeoutMs: 60, attemptMs: 20 }), /not resynchronized/)
})

test('a container window reaches the bot together with its contents', () => {
    const client = new EventEmitter()
    const seen = []
    for (const name of ['open_window', 'window_items', 'set_slot', 'chat']) client.on(name, packet => seen.push([name, packet.windowId]))
    const restore = holdWindowsUntilFilled({ _client: client })
    client.emit('open_window', { windowId: 4 })
    assert.deepEqual(seen, [])
    client.emit('chat', {})
    assert.deepEqual(seen, [['chat', undefined]])
    client.emit('window_items', { windowId: 4 })
    assert.deepEqual(seen.slice(1), [['open_window', 4], ['window_items', 4]])
    client.emit('open_window', { windowId: 5 })
    restore()
    assert.deepEqual(seen.at(-1), ['open_window', 5])
    client.emit('open_window', { windowId: 6 })
    assert.deepEqual(seen.at(-1), ['open_window', 6])
})
