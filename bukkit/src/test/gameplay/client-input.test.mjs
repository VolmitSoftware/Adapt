import test from 'node:test'
import assert from 'node:assert/strict'
import { EventEmitter } from 'node:events'
import { serverSeesJumpKey, vanillaFlightToggle } from './client-input.mjs'

function fakeBot(mayFly) {
    const bot = new EventEmitter()
    bot.controlState = { jump: false }
    bot.abilities = { mayFly, flying: false }
    bot.entity = { isInWater: false }
    bot.sent = []
    bot._client = { write: (name, packet) => bot.sent.push({ name, packet }) }
    vanillaFlightToggle(bot)
    bot.press = ticks => { bot.controlState.jump = true; for (let i = 0; i < ticks; i++) bot.emit('physicsTick') }
    bot.release = ticks => { bot.controlState.jump = false; for (let i = 0; i < ticks; i++) bot.emit('physicsTick') }
    return bot
}

test('a double tap within seven ticks asks to fly only while the client may fly', () => {
    const allowed = fakeBot(true)
    allowed.press(2); allowed.release(2); allowed.press(1)
    assert.deepEqual(allowed.sent, [{ name: 'abilities', packet: { flags: 2 } }])

    const denied = fakeBot(false)
    denied.press(2); denied.release(2); denied.press(1)
    assert.equal(denied.sent.length, 0)
})

test('presses further apart than the window, or one held press, never ask to fly', () => {
    const bot = fakeBot(true)
    bot.press(10)
    bot.release(8); bot.press(1)
    assert.equal(bot.sent.length, 0)
})

test('only servers before 1.21.2 lose the jump key', () => {
    assert.equal(serverSeesJumpKey('1.20.1'), false)
    assert.equal(serverSeesJumpKey('1.21.1'), false)
    assert.equal(serverSeesJumpKey('1.21.2'), true)
    assert.equal(serverSeesJumpKey('26.3'), true)
})
