import test from 'node:test'
import assert from 'node:assert/strict'
import { matchesParticle } from './all-adaptations.mjs'
import { craftingBehaviorCases } from './adaptation-crafting-actions.mjs'

const placement = craftingBehaviorCases.get('architect-placement')
const dust = {
    name: 'dust', count: 1, dust: { rgb: 0x55FFFF, scale: 1 },
    offset: { x: 0, y: 0, z: 0 }, position: { x: 3.3, y: 101.5, z: 0.5 },
}

test('placement matches its authored block-centered ring and rejects the preceding teleport ring', () => {
    assert.equal(matchesParticle(dust, placement), true)
    assert.equal(matchesParticle({ ...dust, position: { x: 2.0652173913043477, y: 100, z: 0.5 } }, placement), false)
})

test('particle height does not replace count, color, or spread requirements', () => {
    assert.equal(matchesParticle({ ...dust, count: 12 }, placement), false)
    assert.equal(matchesParticle({ ...dust, dust: { rgb: 0xFFFFFF } }, placement), false)
    assert.equal(matchesParticle({ ...dust, offset: { x: 0.3, y: 0, z: 0 } }, placement), false)
    assert.equal(matchesParticle({ ...dust, name: 'cloud' }, placement), false)
    assert.equal(matchesParticle({ ...dust, position: { x: 0, y: 100, z: 0 } }, { particle: 'dust' }), true)
})
