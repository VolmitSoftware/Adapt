import assert from 'node:assert/strict'
import test from 'node:test'
import { CLIENT_MEASURED_ATTRIBUTES, jumpHeight, readClientAttributes } from './emulated-attributes.mjs'

const EFFECTS = { Speed: 1, Slowness: 2, Haste: 3, MiningFatigue: 4, JumpBoost: 8, SlowFalling: 28, DolphinsGrace: 30 }

class FakeBlock {
    static fromStateId(stateId) {
        assert.equal(stateId, 77)
        return new FakeBlock()
    }

    digTime(heldItemType, creative, inWater, notOnGround, enchantments, effects) {
        const haste = effects[EFFECTS.Haste] ? effects[EFFECTS.Haste].amplifier + 1 : 0
        const ticks = Math.ceil(5000 * (inWater ? 5 : 1) / (1 + 0.2 * haste))
        return ticks * 50
    }
}

function bot(effects = {}) {
    return {
        registry: {
            effectsByName: Object.fromEntries(Object.entries(EFFECTS).map(([name, id]) => [name, { id }])),
            blocksByName: { obsidian: { defaultState: 77 } },
        },
        entity: {
            position: { floored: () => ({ x: 0, y: 100, z: 0 }) },
            effects: Object.fromEntries(Object.entries(effects).map(([name, amplifier]) => [EFFECTS[name], { id: EFFECTS[name], amplifier }])),
        },
        blockAt: () => new FakeBlock(),
    }
}

test('a vanilla jump peaks near 1.25 blocks and Jump Boost raises it', () => {
    assert.ok(Math.abs(jumpHeight(0.42) - 1.2522) < 0.001)
    assert.ok(jumpHeight(0.52) > jumpHeight(0.42) + 0.4)
})

test('missing attributes are measured from the client effects in attribute units', () => {
    const plain = readClientAttributes({ attributes: {}, absorption: 0 }, bot())
    assert.deepEqual(Object.keys(plain.attributes).sort(), [...CLIENT_MEASURED_ATTRIBUTES].sort())
    assert.equal(plain.attributes['minecraft:block_break_speed'], 1)
    assert.ok(Math.abs(plain.attributes['minecraft:submerged_mining_speed'] - 0.2) < 1e-9)
    assert.equal(plain.attributes['minecraft:jump_strength'], 0.42)
    assert.equal(plain.attributes['minecraft:gravity'], 0.08)
    assert.equal(plain.attributes['minecraft:sneaking_speed'], 0.3)
    assert.equal(plain.attributes['minecraft:movement_efficiency'], 0)
    assert.equal(plain.attributes['minecraft:water_movement_efficiency'], 0)
    assert.equal(plain.attributes['minecraft:max_absorption'], 0)

    const boosted = readClientAttributes({ attributes: {}, absorption: 6 }, bot({ Haste: 1, JumpBoost: 0, SlowFalling: 0, Speed: 2, DolphinsGrace: 0 }))
    assert.ok(Math.abs(boosted.attributes['minecraft:block_break_speed'] - 1.4) < 0.001)
    assert.equal(boosted.clientMeasuredAttributes['minecraft:block_break_speed'].mineTimeMs, Math.ceil(5000 / 1.4) * 50)
    assert.equal(boosted.attributes['minecraft:jump_strength'], 0.52)
    assert.equal(boosted.attributes['minecraft:gravity'], 0.01)
    assert.ok(Math.abs(boosted.attributes['minecraft:sneaking_speed'] - 0.48) < 1e-9)
    assert.ok(Math.abs(boosted.attributes['minecraft:movement_efficiency'] - 0.4) < 1e-9)
    assert.equal(boosted.attributes['minecraft:water_movement_efficiency'], 1)
    assert.equal(boosted.attributes['minecraft:max_absorption'], 6)
})

test('attributes the server reports are never replaced', () => {
    const state = readClientAttributes({ attributes: { 'minecraft:jump_strength': 0.7, 'minecraft:block_break_speed': 2 }, absorption: 0 }, bot({ JumpBoost: 4 }))
    assert.equal(state.attributes['minecraft:jump_strength'], 0.7)
    assert.equal(state.attributes['minecraft:block_break_speed'], 2)
    assert.equal(state.clientMeasuredAttributes['minecraft:jump_strength'], undefined)
})

test('a server reporting every attribute gets no client measurements', () => {
    const attributes = Object.fromEntries(CLIENT_MEASURED_ATTRIBUTES.map(name => [name, 1]))
    const state = readClientAttributes({ attributes }, bot())
    assert.equal(state.clientMeasuredAttributes, undefined)
    assert.deepEqual(state.attributes, attributes)
})

test('mining readings wait until the client has loaded a block', () => {
    const unloaded = { ...bot(), blockAt: () => null }
    const state = readClientAttributes({ attributes: {}, absorption: 0 }, unloaded)
    assert.equal(state.attributes['minecraft:block_break_speed'], undefined)
    assert.equal(state.attributes['minecraft:submerged_mining_speed'], undefined)
    assert.equal(state.attributes['minecraft:jump_strength'], 0.42)
})
