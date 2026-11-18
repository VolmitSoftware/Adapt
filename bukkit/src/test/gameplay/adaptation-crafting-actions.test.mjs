import test from 'node:test'
import assert from 'node:assert/strict'
import { craftingBehaviorCases } from './adaptation-crafting-actions.mjs'

function point(x, y, z) {
    return { x, y, z,
        clone() { return point(this.x, this.y, this.z) },
        set(a, b, c) { this.x = a; this.y = b; this.z = c; return this },
        offset(a, b, c) { return point(this.x + a, this.y + b, this.z + c) },
    }
}

test('Deconstruction sends a real floor interaction while preserving the item eye ray', async () => {
    const packets = []
    const location = point(0.5, 100, 0.5)
    const dropped = { type: 'minecraft:iron_chestplate', entityId: 42, x: 2.5, y: 100, z: 0.5,
        pickupEligible: true, salvage: [{ type: 'minecraft:iron_ingot', amount: 4 }] }
    const snapshot = { location, crafting: { eyeHeight: 1.27, rayItemId: 42, drops: [dropped] } }
    const bot = { entity: { position: location }, entities: { 42: {} },
        _client: { write: (name, data) => packets.push({ name, data }) },
        setControlState() {}, async waitForTicks() {}, async look() {},
        blockAt: position => ({ name: 'stone', position: point(Math.floor(position.x), Math.floor(position.y), Math.floor(position.z)) }),
        activateItem() { assert.fail('Item-only use falsely denies the floor interaction on Paper') },
    }
    await craftingBehaviorCases.get('crafting-deconstruction').trigger({
        actor: { bot }, snapshot: async () => snapshot,
        context: { expect: (condition, message) => assert.ok(condition, message),
            waitUntil: async predicate => assert.ok(predicate()) },
    })
    assert.equal(packets.length, 1)
    assert.equal(packets[0].name, 'block_place')
    const packet = packets[0].data
    assert.deepEqual([packet.location.x, packet.location.y, packet.location.z], [2, 99, 0])
    assert.equal(packet.direction, 1)
    assert.equal(packet.cursorY, 1)
    assert.equal(packet.hand, 0)
    const hitX = packet.location.x + packet.cursorX
    const intersectionX = location.x + (hitX - location.x) * (1.27 - 0.125) / 1.27
    assert.ok(Math.abs(intersectionX - dropped.x) < 1e-9)
})
