import { randomBytes } from 'node:crypto'
import { fixtureJson } from './fixture-json.mjs'
import { synchronizePlayerInput } from './player-input.mjs'
import { synchronizePlayerVelocity } from './player-velocity.mjs'

export default {
    name: 'adapt-golem-lifecycle',
    description: 'Check copper tool retention, bone equipment wear, packing and servant dismissal.',
    async run(context) {
        context.bot.physicsEnabled = false
        const suffix = randomBytes(4).toString('hex')
        const actor = await context.connectActor(`AQAa${suffix}`)
        const opponent = await context.connectActor(`AQAb${suffix}`)
        const stops = [actor, opponent].flatMap(player => [synchronizePlayerInput(player.bot), synchronizePlayerVelocity(player.bot)])
        const evidence = context.report.golemLifecycle = {}
        const fixture = action => fixtureJson(context, `/adaptqa golem-lifecycle ${actor.bot.username} ${action}`, `GOLEM_LIFECYCLE ${action}`)
        const body = (state, kind) => state.golems.find(golem => golem.body === kind)
        const equip = async name => {
            const item = actor.bot.inventory.items().find(stack => stack.name === name)
            context.expect(Boolean(item), `Inventory contains ${name}`)
            await actor.bot.equip(item, 'hand')
            await context.sleep(150)
        }
        let setup = false
        try {
            await context.command(`/adaptqa setup ${actor.bot.username} ${opponent.bot.username}`, /^ADAPT_QA SETUP /, 60000)
            setup = true
            evidence.initial = await fixture('prepare')
            await context.step('Copper retains its rod through both empty-hand interactions', async () => {
                const before = await fixture('copper')
                await context.sleep(400)
                const golem = actor.bot.entities[body(before, 'COPPER').entityId]
                context.expect(Boolean(golem), 'Copper golem is visible')
                await actor.bot.lookAt(golem.position.offset(0, 0.4, 0), true)
                for (const sneaking of [false, true]) {
                    actor.bot.setControlState('sneak', sneaking)
                    await context.sleep(150)
                    for (const hand of ['main_hand', 'off_hand']) {
                        actor.bot._client.write('use_entity', { target: golem.id, hand, location: { x: 0, y: 0.4, z: 0 }, sneaking })
                    }
                    await context.sleep(200)
                }
                actor.bot.setControlState('sneak', false)
                const after = await fixture('snapshot')
                context.expect(body(after, 'COPPER').mainHand.type === 'FISHING_ROD', 'Copper still holds the rod')
                context.expect(body(after, 'COPPER').held, 'Stored rod remains present')
                context.expect(!after.drops.some(item => item.type === 'FISHING_ROD'), 'No rod was dropped')
                evidence.copper = { before, after }
            })
            await context.step('Summoned servant binds without a home chest and wears supplied equipment', async () => {
                await equip('bone')
                actor.bot.setControlState('sneak', true)
                await context.sleep(200)
                await actor.bot.look(0, 0, true)
                const input = await fixtureJson(context, `/adaptqa snapshot ${actor.bot.username} summon`, 'SNAPSHOT summon')
                evidence.summonInput = input
                context.expect(input.sneaking, 'The server receives the summon sneak input')
                context.expect(input.learned['tragoul-skeletal-servant'] === 1, 'Skeletal Servant is learned before summoning')
                actor.bot.activateItem()
                await context.sleep(800)
                actor.bot.deactivateItem()
                actor.bot.setControlState('sneak', false)
                const summoned = await fixture('snapshot')
                context.expect(summoned.servants === 1, 'Natural bone use summons a servant')
                const bound = await fixture('bind')
                context.expect(bound.servants === 0, 'Binding removes the skeleton from the servant pack')
                context.expect(bound.maxHealth === summoned.maxHealth, 'Binding reserves health exactly once')
                await context.sleep(400)
                const worn = await fixture('fight')
                const bone = body(worn, 'BONE')
                context.expect(!bone.homeChest, 'Bone golem has no home chest')
                context.expect(bone.equipment.MAIN_HAND.damage > 11, 'A successful native attack wears the sword')
                context.expect(bone.equipment.CHESTPLATE.damage > 7, 'Incoming attack wears armor')
                context.expect(bone.storedEquipment.some(item => item.type === 'IRON_SWORD' && item.damage === bone.equipment.MAIN_HAND.damage),
                    'Sword wear is persisted immediately')
                context.expect(worn.maxHealth < evidence.initial.maxHealth, 'Bound servant reserves owner health')
                evidence.bone = { summoned, bound, worn }
            })
            await context.step('Crook packs a bone block and placement preserves equipment wear', async () => {
                await equip('iron_hoe')
                const before = await fixture('snapshot')
                const bone = actor.bot.entities[body(before, 'BONE').entityId]
                context.expect(Boolean(bone), 'Bone golem is visible before packing')
                actor.bot.setControlState('sneak', true)
                await context.sleep(200)
                await actor.bot.lookAt(bone.position.offset(0, 0.4, 0), true)
                actor.bot._client.write('use_entity', {
                    target: bone.id, hand: 'main_hand', location: { x: 0, y: 0.4, z: 0 }, sneaking: true,
                })
                await context.waitUntil(() => actor.bot.inventory.items().some(item => item.name === 'bone_block'),
                    { label: 'Crook produces a packed bone block', timeoutMs: 10000 })
                actor.bot.setControlState('sneak', false)
                const packed = await fixture('snapshot')
                context.expect(!body(packed, 'BONE'), 'Packed skeleton leaves the world')
                context.expect(packed.maxHealth === before.maxHealth, 'Packing preserves health reservation')
                await equip('bone_block')
                const ground = actor.bot.blockAt(actor.bot.entity.position.offset(0, -1, 2).floored())
                context.expect(Boolean(ground), 'Placement ground is loaded')
                await actor.bot.activateBlock(ground, ground.position.offset(0, 1, 0).minus(ground.position))
                await context.sleep(1500)
                const restored = await fixture('snapshot')
                context.expect(Boolean(body(restored, 'BONE')), 'Placing the block awakens a bone golem')
                context.expect(restored.maxHealth === before.maxHealth, 'Awakening preserves the same health reservation')
                context.expect(body(restored, 'BONE').burnsInDaylight === false, 'Awakened bone golem retains daylight immunity')
                context.expect(body(restored, 'BONE').equipment.MAIN_HAND.damage === body(before, 'BONE').equipment.MAIN_HAND.damage,
                    'Packed sword retains durability')
                context.expect(body(restored, 'BONE').equipment.CHESTPLATE.damage === body(before, 'BONE').equipment.CHESTPLATE.damage,
                    'Packed armor retains durability')
                context.expect(!restored.inventory.some(item => item.packed), 'Placement consumes the packed item')
                evidence.packing = { before, packed, restored }
            })
            await context.step('Unlearning removes the bound servant and releases contents exactly once', async () => {
                const before = await fixture('snapshot')
                await fixture('unlearn')
                await context.sleep(500)
                const after = await fixture('snapshot')
                context.expect(!body(after, 'BONE'), 'Unlearning removes the bound skeleton')
                context.expect(after.servants === 0, 'No ordinary servants remain')
                context.expect(after.maxHealth === evidence.initial.maxHealth, 'Reserved owner health is restored')
                const contents = [...after.inventory, ...after.drops]
                for (const type of ['IRON_SWORD', 'IRON_CHESTPLATE', 'IRON_HELMET']) {
                    context.expect(contents.filter(item => item.type === type).reduce((total, item) => total + item.amount, 0) === 1,
                        `Exactly one supplied ${type} is returned`)
                }
                context.expect(contents.filter(item => item.core === 'BLANK').length === 1, 'The core is returned')
                context.expect(contents.filter(item => item.rune === 'MIGHT').length === 1, 'The rune is returned')
                await fixture('unlearn')
                const repeated = await fixture('snapshot')
                context.expect([...repeated.inventory, ...repeated.drops].filter(item => item.type === 'IRON_SWORD')
                    .reduce((total, item) => total + item.amount, 0) === 1, 'Repeated unlearning does not duplicate equipment')
                evidence.dismissal = { before, after, repeated }
            })
            await context.step('Clearing adaptations dismisses ordinary and bound servants together', async () => {
                await fixture('prepare')
                for (let index = 0; index < 2; index++) {
                    await context.sleep(10000)
                    await equip('bone')
                    actor.bot.setControlState('sneak', true)
                    await context.sleep(200)
                    await actor.bot.look(0, 0, true)
                    actor.bot.activateItem()
                    await context.sleep(800)
                    actor.bot.deactivateItem()
                    actor.bot.setControlState('sneak', false)
                    const summoned = await fixture('snapshot')
                    context.expect(summoned.servants === 1, 'A servant is summoned for bulk clearing')
                    if (index === 0) await fixture('bind')
                }
                const before = await fixture('snapshot')
                context.expect(Boolean(body(before, 'BONE')) && before.servants === 1, 'Both servant forms exist before clearing')
                await context.command(`/adapt clear adaptations player=${actor.bot.username}`, /Cleared adaptations/, 5000)
                await context.sleep(500)
                const after = await fixture('snapshot')
                context.expect(!body(after, 'BONE') && after.servants === 0, 'Bulk clearing removes both servant forms')
                context.expect(after.maxHealth === evidence.initial.maxHealth, 'Bulk clearing restores all reserved health')
                evidence.bulkClear = { before, after }
            })
        } finally {
            for (const player of [actor, opponent]) {
                player.bot.clearControlStates()
                player.bot.deactivateItem()
            }
            if (setup && !context.signal.aborted) await context.command('/adaptqa cleanup', /^ADAPT_QA CLEANUP$/, 15000)
            for (const stop of stops) stop()
        }
    },
}
