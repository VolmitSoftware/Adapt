import test from 'node:test'
import assert from 'node:assert/strict'
import { kineticsBehaviorCases } from './adaptation-kinetics-actions.mjs'

for (const fails of [false, true]) {
    test(`mounted spear uses server movement and always dismounts${fails ? ' after attack failure' : ''}`, async () => {
        let mounted = false
        let powered = false
        let damage = 0
        let aimed = false
        const controls = []
        const bot = {
            username: 'rider',
            entity: { position: { x: -4.5, y: 100, z: 1.5 } },
            entities: { 7: { id: 7 }, 8: { id: 8, position: { offset: () => ({}) } } },
            async lookAt() { assert.equal(mounted, false); aimed = true },
            async waitForTicks() { assert.equal(mounted, false, 'Mounted physics ticks never arrive') },
            mount() { assert.ok(aimed); mounted = true },
            _client: { write(name, packet) {
                assert.ok(mounted && powered)
                if (name === 'look') {
                    assert.ok(Number.isFinite(packet.yaw) && Number.isFinite(packet.pitch))
                    return
                }
                assert.equal(name, 'block_dig')
                assert.equal(packet.status, 7)
                if (fails) throw new Error('Attack rejected')
                damage = 4
            } },
            setControlState(name, value) {
                controls.push([name, value])
                if (name === 'sneak' && value) mounted = false
            },
        }
        const context = {
            expect: (condition, message) => assert.ok(condition, message),
            async command() { powered = true },
            async waitUntil(predicate) { assert.ok(await predicate()) },
        }
        const snapshot = async () => ({ kinetics: {
            x: powered ? 0.5 : -4.5, y: 100, z: 0.5, eyeHeight: 1.62,
            mounted, vehicleSpeed: powered ? 0.4 : 0, mountId: 7,
            targets: { primary: { entityId: 8, x: 1.5, y: 100, z: 1.5, health: 20 - damage } },
        } })
        const trigger = kineticsBehaviorCases.get('kinetics-mounted-shock').trigger
        if (fails) {
            await assert.rejects(trigger({ actor: { bot }, context, snapshot }), /Attack rejected/)
        } else {
            const result = await trigger({ actor: { bot }, context, snapshot })
            assert.equal(result.damage, 4)
            assert.equal(result.after.kinetics.mounted, true)
        }
        assert.equal(mounted, false)
        assert.deepEqual(controls, [['sneak', true], ['sneak', false]])
    })
}
