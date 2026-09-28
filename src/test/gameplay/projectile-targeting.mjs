import { randomBytes } from 'node:crypto'
import { fixtureJson } from './fixture-json.mjs'
import { synchronizePlayerInput } from './player-input.mjs'
import { synchronizePlayerVelocity } from './player-velocity.mjs'

export default {
    name: 'adapt-projectile-targeting',
    description: 'Verify natural seeking arrows, bounced axes, and skull explosions protect configured mobs and summoned servants.',
    async run(context) {
        const [configured, selectedFamilies] = (context.options.command ?? '').split(':')
        if (!['true', 'false'].includes(configured)) throw new Error('Pass --command true or false, optionally followed by :axe,skull')
        const protectedMobs = configured === 'true'
        const families = selectedFamilies ? selectedFamilies.split(',') : ['heart', 'heart-chain', 'axe', 'skull']
        if (families.some(family => !['heart', 'heart-chain', 'axe', 'skull'].includes(family))) throw new Error('Unknown projectile family')
        const suffix = randomBytes(4).toString('hex')
        const actor = await context.connectActor(`AQAa${suffix}`)
        const opponent = await context.connectActor(`AQAb${suffix}`)
        const stops = [actor, opponent].flatMap(player => [synchronizePlayerInput(player.bot), synchronizePlayerVelocity(player.bot)])
        const evidence = context.report.projectileTargeting = { ignorePassiveMobs: protectedMobs, trials: [] }
        let sequence = 0
        let setup = false
        const snapshot = () => {
            const token = `p${++sequence}`
            return fixtureJson(context, `/adaptqa snapshot ${actor.bot.username} ${token}`, `SNAPSHOT ${token}`)
        }
        const learn = (name, level, append = false) => context.command(`/adaptqa ${append ? 'learn-add' : 'learn'} ${name} ${level}`, /ADAPT_QA LEARN(?:-ADD)? /, 5000)
        async function stage(trial, item) {
            actor.bot.clearControlStates()
            const name = `projectile-qa-targeting-${trial}`
            await context.command(`/adaptqa stage ${name}`, new RegExp(`^ADAPT_QA STAGE ${name}$`), 10000)
            await actor.bot.waitForTicks(8)
            const held = actor.bot.inventory.items().find(candidate => candidate.name === item)
            context.expect(Boolean(held), `Fixture provides ${item}`)
            await actor.bot.equip(held, 'hand')
            await actor.bot.waitForTicks(4)
        }
        async function shoot(target) {
            await actor.bot.lookAt(target.position.offset(0, 0.8, 0), true)
            actor.bot.activateItem()
            await actor.bot.waitForTicks(30)
            await actor.bot.lookAt(target.position.offset(0, 0.8, 3.5), true)
            await actor.bot.waitForTicks(2)
            actor.bot.deactivateItem()
        }
        try {
            await context.command(`/adaptqa setup ${actor.bot.username} ${opponent.bot.username}`, /^ADAPT_QA SETUP /, 60000)
            setup = true
            for (const family of families) {
                for (const mob of family === 'axe' ? ['cow', 'husk', 'servant'] : ['cow', 'wolf', 'husk', 'servant']) {
                    await context.step(`${family} targeting ${mob}`, async () => {
                        await learn(family.startsWith('heart') ? 'ranged-heartseeker' : family === 'axe' ? 'axe-throwing-axe' : 'nether-skull-toss', family === 'skull' ? 3 : family === 'axe' ? 4 : 5)
                        if (family === 'heart-chain') await learn('ranged-piercing', 1, true)
                        if (family === 'axe') await learn('ranged-ricochet-bolt', 1, true)
                        await stage(`${family}-${mob}`, family.startsWith('heart') ? 'bow' : family === 'axe' ? 'iron_axe' : 'wither_skeleton_skull')
                        const before = await snapshot()
                        if (family.startsWith('heart')) {
                            const state = before.projectileTargeting.targets[family === 'heart-chain' ? 'primary' : 'target']
                            const target = actor.bot.entities[state.entityId]
                            context.expect(Boolean(target), 'Bow target is visible')
                            await shoot(target)
                        } else if (family === 'axe') {
                            await actor.bot.lookAt(actor.bot.entity.position.clone().set(6.5, 101.15, 2.5), true)
                            await actor.bot.waitForTicks(2)
                            actor.bot.swingArm('right')
                        } else {
                            await actor.bot.lookAt(actor.bot.entity.position.clone().set(9.5, 101.0, 0.5), true)
                            actor.bot.activateItem()
                            await actor.bot.waitForTicks(2)
                            actor.bot.deactivateItem()
                        }
                        await actor.bot.waitForTicks(family === 'heart-chain' ? 140 : 80)
                        const after = await snapshot()
                        const expected = mob !== 'servant' && (mob === 'husk' || !protectedMobs)
                        const damaged = after.projectileTargeting.targets.target.health < before.projectileTargeting.targets.target.health
                        evidence.trials.push({ family, mob, before: before.projectileTargeting, after: after.projectileTargeting })
                        context.expect(damaged === expected, `${family} ${mob} damage obeys configured protection`)
                        if (family === 'heart-chain') context.expect(after.projectileTargeting.targets.primary.health < before.projectileTargeting.targets.primary.health, 'Initial hostile lock takes seeking arrow damage')
                        if (family === 'axe') context.expect(after.projectileTargeting.impacts.some(hit => hit.ricochets > 0 && hit.targetId === after.projectileTargeting.targets.target.entityId), 'Bounced axe physically reaches the tested mob')
                        if (family === 'skull') context.expect(after.projectileTargeting.damage.some(hit => hit.cause === 'ENTITY_EXPLOSION'), 'Actual vanilla skull explosion reaches the target')
                    })
                    await actor.bot.waitForTicks(30)
                }
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
