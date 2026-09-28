import { randomBytes } from 'node:crypto'
import { fixtureJson } from './fixture-json.mjs'
import { synchronizePlayerInput } from './player-input.mjs'
import { synchronizePlayerVelocity } from './player-velocity.mjs'

const cases = [
    { stage: 'cleave', ability: 'axe-cleave', weapon: 'iron_axe' },
    { stage: 'smash', ability: 'axe-ground-smash', weapon: 'iron_axe' },
    { stage: 'cyclone', ability: 'sword-crimson-cyclone', weapon: 'wooden_sword' },
    { stage: 'earth', ability: 'excavation-earth-mover', weapon: 'diamond_shovel', enemyOnly: true },
    { stage: 'corpse', ability: 'tragoul-corpse-explosion', weapon: 'wooden_axe', enemyOnly: true },
    { stage: 'plague', ability: 'tragoul-plague-bearer', weapon: 'netherite_sword' },
]

export default {
    name: 'adapt-area-targeting',
    description: 'Verify area attacks protect passive and neutral mobs when configured, while pets and servants always remain protected.',
    async run(context) {
        if (!['true', 'false'].includes(context.options.command)) throw new Error('Pass --command true or false for the configured ignorePassiveMobs value')
        const protectedMobs = context.options.command === 'true'
        const suffix = randomBytes(4).toString('hex')
        const actor = await context.connectActor(`AQAa${suffix}`)
        const opponent = await context.connectActor(`AQAb${suffix}`)
        const stops = [actor, opponent].flatMap(player => [synchronizePlayerInput(player.bot), synchronizePlayerVelocity(player.bot)])
        const evidence = context.report.areaTargeting = { ignorePassiveMobs: protectedMobs, trials: [], servantFixture: 'skeleton with the adaptation servant ownership tag' }
        let sequence = 0
        let setup = false
        const snapshot = () => {
            const token = `t${++sequence}`
            return fixtureJson(context, `/adaptqa snapshot ${actor.bot.username} ${token}`, `SNAPSHOT ${token}`)
        }
        async function equip(name) {
            const item = actor.bot.inventory.items().find(item => item.name === name)
            context.expect(Boolean(item), `${name} is available`)
            await actor.bot.equip(item, 'hand')
            await actor.bot.waitForTicks(24)
        }
        async function primary() {
            const state = await snapshot()
            const target = actor.bot.entities[state.areaTargets.primary.entityId]
            context.expect(Boolean(target), 'Primary target is visible for a natural attack')
            return target
        }
        async function strike() {
            const target = await primary()
            await actor.bot.lookAt(target.position.offset(0, 0.8, 0), true)
            actor.bot.attack(target)
        }
        async function trigger(trial) {
            switch (trial.stage) {
                case 'cleave':
                case 'corpse':
                    await strike()
                    break
                case 'cyclone': {
                    const target = await primary()
                    await actor.bot.lookAt(target.position.offset(0, 0.9, 0), true)
                    actor.bot.setControlState('jump', true)
                    try {
                        await context.waitUntil(() => !actor.bot.entity.onGround && actor.bot.entity.velocity.y < -0.15,
                            { label: 'natural critical strike descending phase', timeoutMs: 2000, intervalMs: 10 })
                        actor.bot.attack(target)
                    } finally { actor.bot.setControlState('jump', false) }
                    break
                }
                case 'smash':
                    actor.bot.setControlState('jump', true)
                    await actor.bot.waitForTicks(4)
                    actor.bot.setControlState('jump', false)
                    context.expect(!actor.bot.entity.onGround, 'Ground smash begins during an actual jump')
                    actor.bot.setControlState('sneak', true)
                    await context.waitUntil(() => actor.bot.entity.onGround, { label: 'ground smash landing', timeoutMs: 4000, intervalMs: 20 })
                    actor.bot.setControlState('sneak', false)
                    break
                case 'earth':
                    actor.bot.setControlState('sneak', true)
                    await actor.bot.waitForTicks(4)
                    await actor.bot.look(0, 0, true)
                    actor.bot.activateItem()
                    await actor.bot.waitForTicks(4)
                    actor.bot.deactivateItem()
                    actor.bot.setControlState('sneak', false)
                    break
                case 'plague': {
                    await equip('bow')
                    const target = await primary()
                    await actor.bot.lookAt(target.position.offset(0, 0.6, 0), true)
                    actor.bot.activateItem()
                    await actor.bot.waitForTicks(12)
                    actor.bot.deactivateItem()
                    await context.waitUntil(async () => (await snapshot()).areaTargets.primary.effects.includes('minecraft:poison'),
                        { label: 'natural tipped arrow poisons the primary cow', timeoutMs: 4000 })
                    await equip('netherite_sword')
                    for (let hit = 0; hit < 4; hit++) {
                        if ((await snapshot()).areaTargets.primary.dead) break
                        await strike()
                        await actor.bot.waitForTicks(15)
                    }
                    break
                }
            }
            await actor.bot.waitForTicks(40)
        }
        try {
            await context.command(`/adaptqa setup ${actor.bot.username} ${opponent.bot.username}`, /^ADAPT_QA SETUP /, 60000)
            setup = true
            const catalog = await fixtureJson(context, '/adaptqa catalog targets', 'CATALOG targets')
            const levels = new Map(catalog.skills.flatMap(skill => skill.adaptations.map(ability => [ability.name, ability.maxLevel])))
            for (const trial of cases) {
                await context.step(`${trial.ability} respects passive protection and friendly summons`, async () => {
                    const level = levels.get(trial.ability)
                    context.expect(level > 0, `${trial.ability} exists and has learnable levels`)
                    await context.command(`/adaptqa learn ${trial.ability} ${level}`, new RegExp(`^ADAPT_QA LEARN ${trial.ability} ${level}$`), 5000)
                    await context.command(`/adaptqa stage area-targeting-${trial.stage}`, new RegExp(`^ADAPT_QA STAGE area-targeting-${trial.stage}$`), 10000)
                    await actor.bot.waitForTicks(6)
                    await equip(trial.weapon)
                    const before = await snapshot()
                    await trigger(trial)
                    const after = await snapshot()
                    if (['cleave', 'cyclone', 'corpse', 'plague'].includes(trial.stage)) {
                        context.expect(after.areaTargets.primary.health < before.areaTargets.primary.health, 'Direct attacks on the passive primary target still work')
                    }
                    if (['corpse', 'plague'].includes(trial.stage)) context.expect(after.areaTargets.primary.dead, 'Natural player combat kills the source cow')
                    for (const mob of ['cow', 'wolf', 'enderman', 'hostile', 'pet', 'servant', 'decoy']) {
                        const expected = mob === 'hostile' || (!protectedMobs && !['pet', 'servant', 'decoy'].includes(mob)
                            && (!trial.enemyOnly || mob === 'enderman'))
                        const affected = trial.stage === 'plague' ? after.areaTargets[mob].effects.includes('minecraft:poison')
                            : after.areaTargets[mob].health < before.areaTargets[mob].health
                        context.expect(affected === expected, `${trial.ability}: ${mob} affected=${affected}, expected=${expected}`)
                        if (!expected) context.expect(after.areaTargets[mob].effects.length === 0, `${trial.ability} also leaves protected ${mob} without secondary effects`)
                    }
                    if (trial.stage === 'earth') context.expect(after.areaTargets.hostile.effects.includes('minecraft:slowness'), 'Earth mover retains hostile slowness')
                    evidence.trials.push({ ability: trial.ability, before: before.areaTargets, after: after.areaTargets })
                })
            }
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
