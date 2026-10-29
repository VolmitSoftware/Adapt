import assert from 'node:assert/strict'
import test from 'node:test'
import { claimsEntryRoute } from './claims.mjs'

test('restart entry uses short level cardinal waypoints from the validated outside apron', () => {
  const origin = { x: 4066.5, y: 100, z: 4066.5 }
  const destination = { x: 4106.5, y: 100, z: 4107.5 }
  const route = claimsEntryRoute(origin, destination)
  assert.deepEqual(route.at(-1), destination)
  let previous = origin
  for (const waypoint of route) {
    assert.equal(waypoint.y, 100)
    assert.ok(Math.hypot(waypoint.x - previous.x, waypoint.z - previous.z) <= 8)
    assert.ok(waypoint.x === previous.x || waypoint.z === previous.z)
    if (waypoint.x >= 4080) assert.equal(waypoint.z, 4107.5)
    previous = waypoint
  }
})

test('entry route does not assume a different elevation or add teleport-like gaps', () => {
  const destination = { x: 4106.5, y: 100, z: 4107.5 }
  assert.deepEqual(claimsEntryRoute(destination, destination), [])
  assert.throws(() => claimsEntryRoute({ x: 4066.5, y: 65, z: 4066.5 }, destination), /level arena apron/)
})
