import test from 'node:test'
import assert from 'node:assert/strict'
import { EventEmitter } from 'node:events'
import { tamingBehaviorCases } from './adaptation-taming-actions.mjs'

const context = {
    expect: (condition, message) => assert.ok(condition, message),
    async waitUntil(predicate) { assert.ok(await predicate()) },
}

test('fetch captures travel packets between slower server snapshots and removes its listener', async () => {
    const bot = new EventEmitter()
    bot.entity = { position: { clone: () => ({}), distanceTo: () => 0 } }
    bot.inventory = { slots: [{ name: 'diamond', count: 3 }] }
    bot.waitForTicks = async () => {
        bot.emit('entityMoved', { id: 7, position: { x: 6.5 } })
        bot.emit('entityMoved', { id: 8, position: { x: 100 } })
    }
    const result = await tamingBehaviorCases.get('tame-fetch').trigger({
        actor: { bot }, snapshot: async () => ({ taming: { pet: { entityId: 7, x: 2.5 } } }),
    })
    assert.equal(result.furthestPetX, 6.5)
    assert.equal(result.diamonds, 3)
    assert.equal(bot.listenerCount('entityMoved'), 0)
    await tamingBehaviorCases.get('tame-fetch').verify({
        context, unlearned: { diamonds: 0 }, active: result,
    })
    await assert.rejects(tamingBehaviorCases.get('tame-fetch').verify({
        context, unlearned: { diamonds: 0 }, active: { ...result, furthestPetX: 2.5 },
    }), /Wolf travels/)
})

test('mounted melee completes without client physics ticks and dismounts after errors', async () => {
    const horse = { id: 7, position: { offset: () => ({}) } }
    const opponent = { bot: { username: 'opponent', entity: { id: 8 } } }
    let damage = 0
    let dismounts = 0
    const bot = {
        entities: { 7: horse, 8: { id: 8, position: { offset: () => ({}) } } },
        lookAt: async () => {},
        activateEntity() { this.vehicle = horse },
        attack() { damage = 2 },
        setControlState(key, enabled) {
            if (key === 'sneak' && enabled) { this.vehicle = undefined; dismounts++ }
        },
        waitForTicks() { assert.ok(!this.vehicle, 'Mounted client physics is suspended') },
    }
    const snapshot = async player => player === opponent
        ? { health: 20 - damage }
        : { taming: { mounted: Boolean(bot.vehicle), pet: { entityId: 7, attributes: {} } } }
    const entry = tamingBehaviorCases.get('tame-mounted-tactics')
    const result = await entry.trigger({ context, actor: { bot }, opponent, snapshot })
    assert.equal(result.damage, 2)
    assert.equal(dismounts, 1)
    delete bot.entities[8]
    await assert.rejects(entry.trigger({ context, actor: { bot }, opponent, snapshot }), /Mounted melee opponent/)
    assert.equal(dismounts, 2)
})
