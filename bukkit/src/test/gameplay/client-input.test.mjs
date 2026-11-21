import test from 'node:test'
import assert from 'node:assert/strict'
import { EventEmitter } from 'node:events'
import { doubleJump, serverSeesJumpKey, vanillaFlightToggle } from './client-input.mjs'

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

function tickingBot(mayFly) {
    const bot = fakeBot(mayFly)
    bot.entity.onGround = true
    bot.entity.yaw = 0
    bot.entity.pitch = 0
    bot.game = { gameMode: 'survival' }
    bot.presses = 0
    bot.setControlState = (control, state) => {
        if (control === 'jump' && state && !bot.controlState.jump) bot.presses++
        bot.controlState[control] = state
    }
    bot.look = async () => {}
    bot.waitForTicks = async ticks => {
        for (let i = 0; i < ticks; i++) {
            bot.emit('physicsTick')
            bot.entity.onGround = false
        }
    }
    return bot
}

test('a double jump is two presses, the second one inside the double tap window of the first', async () => {
    const previous = process.env.ADAPT_QA_MINECRAFT_VERSION
    process.env.ADAPT_QA_MINECRAFT_VERSION = '1.20.1'
    try {
        const bot = tickingBot(true)
        const jump = await doubleJump(bot)
        assert.equal(bot.presses, 2)
        assert.deepEqual(jump, { airborne: true, mayFly: true, flightRequests: 1 })
    } finally {
        if (previous === undefined) delete process.env.ADAPT_QA_MINECRAFT_VERSION
        else process.env.ADAPT_QA_MINECRAFT_VERSION = previous
    }
})
