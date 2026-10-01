import test from 'node:test'
import assert from 'node:assert/strict'
import { itemBehaviorCases } from './adaptation-item-actions.mjs'

test('crafting XP measures natural crafting without requiring a villager fixture', async () => {
    const recipe = { result: { id: 1, count: 4 } }
    const bot = {
        registry: { itemsByName: { oak_planks: { id: 1 } } },
        experience: { points: 0 }, player: { uuid: 'crafter' },
        inventory: { items: () => [{ name: 'oak_planks', count: 4 }] },
        recipesFor: () => [recipe],
        async craft(actual, count) { assert.equal(actual, recipe); assert.equal(count, 1); bot.experience.points = 7 },
        async waitForTicks() {},
    }
    const result = await itemBehaviorCases.get('crafting-xp').trigger({
        actor: { bot }, context: { expect: (condition, message) => assert.ok(condition, message) },
    })
    assert.deepEqual(result, { crafted: 4, xp: 7 })
})
