import test from 'node:test'
import assert from 'node:assert/strict'
import { matchesParticle } from './all-adaptations.mjs'
import { craftingBehaviorCases } from './adaptation-crafting-actions.mjs'
import { guardBehaviorCases } from './adaptation-guard-actions.mjs'
import { projectileBehaviorCases } from './adaptation-projectile-actions.mjs'

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

test('disarm crit is told apart from a snare-line root crit by its speed', () => {
    const disarm = guardBehaviorCases.get('unarmed-disarm')
    const crit = { name: 'crit', count: 6, offset: { x: 0.3, y: 0.3, z: 0.3 }, speed: Math.fround(0.08), position: { x: 2.5, y: 101.1, z: 0.5 } }
    assert.equal(matchesParticle(crit, disarm), true)
    assert.equal(matchesParticle({ ...crit, count: 3 }, disarm), true)
    assert.equal(matchesParticle({ ...crit, speed: 0 }, disarm), false)
    assert.equal(matchesParticle({ ...crit, offset: { x: 0, y: 0, z: 0 } }, disarm), false)
})

test('snare line matches its green snare dust and rejects the cyan arena ring', () => {
    const snareLine = projectileBehaviorCases.get('hunter-snare-line')
    const ring = { name: 'dust', count: 1, dust: { rgb: 0x78B45A, scale: 0.8 }, offset: { x: 0, y: 0, z: 0 }, position: { x: 3.5, y: 100, z: 0.5 } }
    assert.equal(matchesParticle(ring, snareLine), true)
    assert.equal(matchesParticle({ ...ring, dust: { rgb: 0x55FFFF, scale: 1 } }, snareLine), false)
})
