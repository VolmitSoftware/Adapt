import test from 'node:test'
import assert from 'node:assert/strict'
import { utilityBehaviorCases } from './adaptation-utility-actions.mjs'

const context = { expect: (condition, message) => assert.ok(condition, message) }
const state = (events, credited = 0, hasTarget = false) => ({
    before: { utility: { veilSuppressions: 2 } },
    after: { utility: { events, veilSuppressions: 2 + credited, target: { hasTarget } } },
})

test('Enderveil proof requires credited native stare cancellation, not vanilla gaze prechecks', async () => {
    const verify = utilityBehaviorCases.get('stealth-enderveil').verify
    const unlearned = state([{ type: 'stare', cancelled: false }])
    const active = state([{ type: 'stare', cancelled: true }], 1)
    const result = await verify({ context, unlearned, active })
    assert.ok(result.assertions.length > 0)
    await assert.rejects(verify({ context, unlearned, active: state([{ type: 'stare', cancelled: true }]) }), /credits a real suppression/)
    await assert.rejects(verify({ context, unlearned, active: state([{ type: 'stare', cancelled: true }], 1, true) }), /credits a real suppression/)
    await assert.rejects(verify({ context, unlearned: active, active }), /permits the native stare event/)
})

test('Cat Reflexes requires twenty distinct sprinting control impacts rather than twenty shots', async () => {
    const verify = utilityBehaviorCases.get('agility-cat-reflexes').verify
    const events = Array.from({ length: 20 }, (_, index) => ({
        type: 'projectile', projectileId: 100 + index, sprinting: true, cancelled: false,
    }))
    const unlearned = state(events)
    const active = { ...state([{ type: 'projectile', projectileId: 201, sprinting: true, cancelled: true }]),
        eligibleHits: 1, noDodgeRisk: 0.65 }
    await verify({ context, unlearned, active })
    await assert.rejects(verify({ context, unlearned: state(Array(20).fill(events[0])), active }), /Twenty unlearned real arrows/)
    await assert.rejects(verify({ context, unlearned: state(events.map(event => ({ ...event, sprinting: false }))), active }), /Twenty unlearned real arrows/)
    await assert.rejects(verify({ context, unlearned, active: { ...active, ...state([{ ...events[0], cancelled: true, sprinting: false }]) } }), /Learned sprinting must cancel/)
})
