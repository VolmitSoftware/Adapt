import { randomBytes } from 'node:crypto'
import { fixtureJson } from './fixture-json.mjs'
import { synchronizePlayerInput } from './player-input.mjs'
import { synchronizePlayerVelocity } from './player-velocity.mjs'

export default {
    name: 'adapt-tragoul-targeting',
    description: 'Verify passive and neutral mob protection against natural Tragoul combat with the configured toggle.',
    async run(context) {
        if (!['true', 'false'].includes(context.options.command)) throw new Error('Pass --command true or false for the configured ignorePassiveMobs value')
        const protectedMobs = context.options.command === 'true'
        const suffix = randomBytes(4).toString('hex')
        const actor = await context.connectActor(`AQAa${suffix}`)
        const opponent = await context.connectActor(`AQAb${suffix}`)
        const stops = [actor, opponent].flatMap(player => [synchronizePlayerInput(player.bot), synchronizePlayerVelocity(player.bot)])
        const evidence = context.report.tragoulTargeting = { ignorePassiveMobs: protectedMobs, trials: [] }
        let sequence = 0
        let setup = false
        const snapshot = () => {
            const token = `t${++sequence}`
            return fixtureJson(context, `/adaptqa snapshot ${actor.bot.username} ${token}`, `SNAPSHOT ${token}`)
        }
        const learn = name => context.command(`/adaptqa learn tragoul-${name} 1`, new RegExp(`^ADAPT_QA LEARN tragoul-${name} 1$`), 5000)
        async function stage(trial) {
            actor.bot.clearControlStates()
            const name = `tragoul-qa-targeting-${trial}`
            await context.command(`/adaptqa stage ${name}`, new RegExp(`^ADAPT_QA STAGE ${name}$`), 10000)
            await actor.bot.waitForTicks(6)
        }
        async function strikePrimary() {
            const axe = actor.bot.inventory.items().find(item => item.name === 'wooden_axe')
            context.expect(Boolean(axe), 'Actor has the fixture axe')
            await actor.bot.equip(axe, 'hand')
            await actor.bot.waitForTicks(24)
            const before = await snapshot()
            const target = actor.bot.entities[before.tragoul.targets.primary.entityId]
            context.expect(Boolean(target), 'Natural melee target is visible')
            await actor.bot.lookAt(target.position.offset(0, 0.8, 0), true)
            actor.bot.attack(target)
            return before
        }
        try {
            await context.command(`/adaptqa setup ${actor.bot.username} ${opponent.bot.username}`, /^ADAPT_QA SETUP /, 60000)
            setup = true
            for (const mob of ['wolf', 'husk']) {
                await context.step(`Thorns against naturally attacking ${mob}`, async () => {
                    await learn('thorns')
                    await stage(`thorns-${mob}`)
                    const before = await snapshot()
                    context.expect(before.health === 20, 'Attacker has not reached the defender before observation')
                    await context.waitUntil(() => actor.bot.health < 20, { label: `${mob} naturally attacks the defender`, timeoutMs: 15000 })
                    await actor.bot.waitForTicks(3)
                    const after = await snapshot()
                    const reflected = after.tragoul.targets.attacker.health < before.tragoul.targets.attacker.health
                    context.expect(reflected === (mob === 'husk' || !protectedMobs), `${mob} reflection obeys ignorePassiveMobs=${protectedMobs}`)
                    evidence.trials.push({ ability: 'thorns', mob, before, after })
                })
                await actor.bot.waitForTicks(35)
            }
            await context.step('Globe collateral targeting preserves direct melee damage', async () => {
                await learn('globe')
                await stage('globe')
                const before = await strikePrimary()
                await actor.bot.waitForTicks(15)
                const after = await snapshot()
                context.expect(after.tragoul.targets.primary.health < before.tragoul.targets.primary.health, 'Directly attacked passive cow still takes melee damage')
                for (const mob of ['cow', 'wolf', 'enderman', 'husk']) {
                    const damaged = after.tragoul.targets[mob].health < before.tragoul.targets[mob].health
                    context.expect(damaged === (mob === 'husk' || !protectedMobs), `${mob} Globe collateral obeys ignorePassiveMobs=${protectedMobs}`)
                }
                for (const mob of ['pet', 'servant', 'decoy']) context.expect(after.tragoul.targets[mob].health === before.tragoul.targets[mob].health,
                    `Globe always protects ${mob}`)
                evidence.trials.push({ ability: 'globe', before, after })
            })
            for (const mob of ['cow', 'wolf', 'enderman']) {
                await context.step(`Lance chooses between nearby ${mob} and farther hostile husk`, async () => {
                    await learn('lance')
                    await stage(`lance-${mob}`)
                    const before = await strikePrimary()
                    await actor.bot.waitForTicks(45)
                    const after = await snapshot()
                    context.expect(after.tragoul.targets.primary.dead, 'Direct player strike kills the source cow')
                    const protectedDamaged = after.tragoul.targets.protected.health < before.tragoul.targets.protected.health
                    const hostileDamaged = after.tragoul.targets.husk.health < before.tragoul.targets.husk.health
                    context.expect(protectedDamaged === !protectedMobs, `${mob} Lance damage obeys ignorePassiveMobs=${protectedMobs}`)
                    context.expect(hostileDamaged === protectedMobs, 'Level-one lance chooses exactly the closest eligible target')
                    for (const friendly of ['pet', 'servant', 'decoy']) context.expect(after.tragoul.targets[friendly].health === before.tragoul.targets[friendly].health,
                        `Lance skips nearer ${friendly} regardless of passive protection`)
                    context.expect(after.health < before.health, 'Successful lance still pays the owner health cost')
                    evidence.trials.push({ ability: 'lance', mob, before, after })
                })
                await actor.bot.waitForTicks(65)
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
