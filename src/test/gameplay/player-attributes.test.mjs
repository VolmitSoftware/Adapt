import assert from 'node:assert/strict'
import test from 'node:test'
import { EventEmitter } from 'node:events'
import { createAttributePacketDecoder, synchronizePlayerAttributes } from './player-attributes.mjs'

const registry = { 0: 'minecraft:armor', 22: 'minecraft:movement_speed', 35: 'minecraft:friction_modifier' }
function bot() {
    return { version: '26.1.2', physics: { sprintingUUID: 'old-sprint-uuid' }, entities: {
        1: { attributes: { 'generic.scale': { value: 0.1, modifiers: [] } } },
    }, _client: { state: 'play', decompressor: new EventEmitter() }, registry: { attributesArray: Array.from({ length: 36 }, (_, id) => ({ resource: registry[id] ?? `minecraft:unused_${id}` })), protocol: { play: { toClient: { types: {
        packet: ['container', [{ name: 'name', type: ['mapper', { mappings: { 4: 'entity_update_attributes' } }] }]],
        packet_entity_update_attributes: ['container', [{ name: 'properties', type: ['array', { type: ['container', [
            { name: 'key', type: ['mapper', { mappings: { 0: 'generic.armor', 22: 'generic.scale' } }] },
        ]] }] }]],
    } } } } } }
}
function packet(properties) {
    const chunks = [Buffer.from([4, 1, properties.length])]
    for (const property of properties) {
        const header = Buffer.alloc(10)
        header[0] = property.id
        header.writeDoubleBE(property.value, 1)
        header[9] = property.modifiers?.length ?? 0
        chunks.push(header)
        for (const modifier of property.modifiers ?? []) {
            const name = Buffer.from(modifier.uuid)
            const tail = Buffer.alloc(9)
            tail.writeDoubleBE(modifier.amount)
            tail[8] = modifier.operation
            chunks.push(Buffer.from([name.length]), name, tail)
        }
    }
    return Buffer.concat(chunks)
}

test('raw attributes use the negotiated client registry despite a stale protocol name mapper', () => {
    const decode = createAttributePacketDecoder(bot())
    const modifiers = [
        { uuid: 'adapt:agility-windup', amount: 0.4, operation: 1 },
        { uuid: 'minecraft:sprinting', amount: Math.fround(0.3), operation: 2 },
    ]
    assert.deepEqual(decode(packet([{ id: 22, value: 0.1, modifiers }, { id: 35, value: 1 }])), {
        entityId: 1, properties: [
            { registryId: 22, key: 'minecraft:movement_speed', value: 0.1, modifiers },
            { registryId: 35, key: 'minecraft:friction_modifier', value: 1, modifiers: [] },
        ],
    })
    assert.equal(decode(Buffer.from([5])), null)
})

test('attribute observation corrects client physics keys and preserves modifier operations without double sprinting', () => {
    const player = bot()
    const stream = player._client.decompressor
    const ordinary = () => { player.entities[1].attributes['generic.scale'] = { value: 0.1, modifiers: [] } }
    stream.on('data', ordinary)
    const stop = synchronizePlayerAttributes(player, error => { throw error })
    assert.equal(player.entities[1].attributes['minecraft:movement_speed'].value, 0.1)
    assert.equal(player.physics.sprintingUUID, 'minecraft:sprinting')
    const modifiers = [{ uuid: 'adapt:windup', amount: 0.4, operation: 1 }]
    stream.emit('data', packet([{ id: 22, value: 0.1, modifiers }]))
    assert.deepEqual(player.entities[1].attributes, { 'minecraft:movement_speed': { value: 0.1, modifiers } })
    stop()
    assert.deepEqual(stream.listeners('data'), [ordinary])
    assert.equal(player.physics.sprintingUUID, 'old-sprint-uuid')
})

test('malformed or unknown client attributes cannot silently become a movement value', () => {
    const decode = createAttributePacketDecoder(bot())
    assert.throws(() => decode(packet([{ id: 36, value: 0.1 }])), /Unknown client attribute/)
    assert.throws(() => decode(packet([{ id: 22, value: NaN }])), /Invalid attribute/)
    assert.throws(() => decode(packet([{ id: 22, value: 0.1 }]).subarray(0, -2)))
    assert.throws(() => decode(Buffer.concat([packet([]), Buffer.from([0])])), /suffix/)
    assert.throws(() => decode(packet([{ id: 22, value: 0.1, modifiers: [{ uuid: 'adapt:invalid', amount: 1, operation: 3 }] }])), /Invalid attribute modifier/)
})
