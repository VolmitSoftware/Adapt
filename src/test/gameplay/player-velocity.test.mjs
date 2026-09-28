import assert from 'node:assert/strict'
import test from 'node:test'
import { EventEmitter } from 'node:events'
import { synchronizePlayerVelocity } from './player-velocity.mjs'

function player(type = 'lpVec3') {
    const velocity = { x: 0, y: 0, z: 0, set(x, y, z) { Object.assign(this, { x, y, z }) } }
    const client = new EventEmitter()
    client.on('entity_velocity', packet => velocity.set(packet.velocity.x / 8000, packet.velocity.y / 8000, packet.velocity.z / 8000))
    return { _client: client, entities: { 1: { velocity } }, registry: { protocol: { play: { toClient: { types: {
        packet_entity_velocity: ['container', [{ name: 'velocity', type }]],
        packet_spawn_entity: ['container', [{ name: 'velocity', type }]],
    } } } } } }
}

test('decoded lpVec3 impulses retain server units after Mineflayer processing', () => {
    const bot = player()
    synchronizePlayerVelocity(bot)
    const packet = { entityId: 1, velocity: { x: 0.75, y: 1.1, z: -0.4 } }
    bot._client.emit('entity_velocity', packet)
    const { x, y, z } = bot.entities[1].velocity
    assert.deepEqual({ x, y, z }, packet.velocity)
    assert.deepEqual(packet.velocity, { x: 0.75, y: 1.1, z: -0.4 })
})

test('spawn impulses are applied and cleanup removes only the correction listeners', () => {
    const bot = player()
    const stop = synchronizePlayerVelocity(bot)
    bot._client.emit('spawn_entity', { entityId: 1, velocity: { x: 0, y: 0.5, z: 0 } })
    assert.equal(bot.entities[1].velocity.y, 0.5)
    stop()
    assert.equal(bot._client.listenerCount('spawn_entity'), 0)
    assert.equal(bot._client.listenerCount('entity_velocity'), 1)
})

test('older fixed point protocols are rejected instead of receiving a wrong conversion', () => {
    assert.throws(() => synchronizePlayerVelocity(player('vec3i16')), /lpVec3 protocol/)
})
