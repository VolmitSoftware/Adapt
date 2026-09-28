import test from 'node:test'
import assert from 'node:assert/strict'
import { EventEmitter } from 'node:events'
import { synchronizePlayerPassengers } from './player-passengers.mjs'

function mounted() {
    const bot = new EventEmitter()
    bot._client = new EventEmitter()
    bot.entity = { id: 91 }
    const companion = { id: 92 }
    bot.vehicle = { id: 34, passengers: [bot.entity, companion] }
    bot.entity.vehicle = bot.vehicle
    return { bot, companion }
}

test('real passenger removal clears stale rider state and emits one dismount', () => {
    const { bot, companion } = mounted()
    const vehicle = bot.vehicle
    const received = []
    bot.on('dismount', entity => received.push(entity))
    const stop = synchronizePlayerPassengers(bot)
    bot._client.emit('set_passengers', { entityId: 34, passengers: [92] })
    assert.equal(bot.vehicle, null)
    assert.equal(bot.entity.vehicle, null)
    assert.deepEqual(vehicle.passengers, [companion])
    assert.deepEqual(received, [vehicle])
    bot._client.emit('set_passengers', { entityId: 34, passengers: [] })
    assert.equal(received.length, 1)
    stop()
    assert.equal(bot._client.listenerCount('set_passengers'), 0)
})

test('unrelated vehicle packets and retained rider IDs do not dismount the player', () => {
    const { bot } = mounted()
    const vehicle = bot.vehicle
    const stop = synchronizePlayerPassengers(bot)
    bot._client.emit('set_passengers', { entityId: 78, passengers: [] })
    bot._client.emit('set_passengers', { entityId: 34, passengers: [91] })
    assert.equal(bot.vehicle, vehicle)
    assert.equal(bot.entity.vehicle, vehicle)
    stop()
    bot._client.emit('set_passengers', { entityId: 34, passengers: [] })
    assert.equal(bot.vehicle, vehicle)
})
