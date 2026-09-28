import assert from 'node:assert/strict'
import test from 'node:test'
import { synchronizePlayerInput } from './player-input.mjs'

function player() {
    const states = { forward: false, back: false, left: false, right: false, jump: false, sneak: false, sprint: false }
    const packets = []
    const bot = {
        supportFeature: () => true,
        getControlState: control => states[control],
        _client: { write: (name, packet) => packets.push({ name, packet }) },
        setControlState(control, state) {
            states[control] = state
            if (control === 'sneak') this._client.write('player_input', { inputs: { shift: state } })
        },
    }
    return { bot, packets }
}

test('jump presses and releases send complete server input without dropping held movement', () => {
    const { bot, packets } = player()
    synchronizePlayerInput(bot)
    bot.setControlState('forward', true)
    bot.setControlState('jump', true)
    bot.setControlState('jump', false)
    assert.deepEqual(packets.map(value => value.packet.inputs), [
        { forward: true, backward: false, left: false, right: false, jump: false, shift: false, sprint: false },
        { forward: true, backward: false, left: false, right: false, jump: true, shift: false, sprint: false },
        { forward: true, backward: false, left: false, right: false, jump: false, shift: false, sprint: false },
    ])
})

test('sneak packets preserve simultaneous sprint, backward movement and jump', () => {
    const { bot, packets } = player()
    synchronizePlayerInput(bot)
    for (const control of ['back', 'jump', 'sprint', 'sneak']) bot.setControlState(control, true)
    assert.equal(packets.length, 4)
    assert.deepEqual(packets.at(-1).packet.inputs, { forward: false, backward: true, left: false, right: false, jump: true, shift: true, sprint: true })
})

test('cleanup restores the original controller and protocol writer', () => {
    const { bot, packets } = player()
    const originalControl = bot.setControlState
    const originalWrite = bot._client.write
    const stop = synchronizePlayerInput(bot)
    bot._client.write('chat_message', { message: 'hello' })
    assert.deepEqual(packets, [{ name: 'chat_message', packet: { message: 'hello' } }])
    stop()
    assert.equal(bot.setControlState, originalControl)
    assert.equal(bot._client.write, originalWrite)
})
