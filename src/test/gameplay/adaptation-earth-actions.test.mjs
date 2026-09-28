import test from 'node:test'
import assert from 'node:assert/strict'
import { EventEmitter } from 'node:events'
import { observeOreMarkers } from './adaptation-earth-actions.mjs'

test('ore marker observer separates real-drop visuals and handles delayed display metadata', () => {
    const actor = { bot: new EventEmitter() }
    const opponent = { bot: new EventEmitter() }
    const position = { x: 4, y: 100, z: 1 }
    const observer = observeOreMarkers(actor, opponent, position)
    const drop = { id: 1, name: 'block_display', position: { x: 3.4, y: 100.2, z: 1.1 }, metadata: [0x40] }
    actor.bot.emit('entitySpawn', drop)
    opponent.bot.emit('entitySpawn', drop)
    const marker = { id: 2, name: 'block_display', position, metadata: [] }
    actor.bot.emit('entitySpawn', marker)
    assert.equal(observer.displays.size, 0)
    marker.metadata[0] = 0x40
    actor.bot.emit('entityUpdate', marker)
    actor.bot.emit('entityUpdate', marker)
    assert.deepEqual([...observer.displays], [2])
    assert.equal(observer.privateDisplays.size, 0)
    opponent.bot.emit('entityUpdate', marker)
    assert.deepEqual([...observer.privateDisplays], [2])
    observer.stop()
    for (const event of ['entitySpawn', 'entityUpdate']) {
        assert.equal(actor.bot.listenerCount(event), 0)
        assert.equal(opponent.bot.listenerCount(event), 0)
    }
})
