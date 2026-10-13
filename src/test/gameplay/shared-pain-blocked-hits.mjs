import { randomBytes } from 'node:crypto'
import { join } from 'node:path'
import { fixtureJson } from './fixture-json.mjs'
import { errorsSince, logPosition } from './runtime-log.mjs'

export default {
  name: 'adapt-shared-pain-blocked-hits',
  description: 'Verify actual shield-blocked attacks and intercepted arrows cannot charge Shared Pain pet health twice.',
  async run(context) {
    context.expect(context.report.server.instance.startsWith('adapt-mining-fixes-'), 'Pet checks target their disposable instance')
    const suffix = randomBytes(4).toString('hex')
    const actor = await context.connectActor(`AQAP${suffix}`)
    const other = await context.connectActor(`AQAQ${suffix}`)
    const fixture = command => fixtureJson(context,
      `/adaptqa mining-audit ${actor.bot.username} ${command}`, `MINING_AUDIT ${command.split(' ')[0]}`, 10000)
    const evidence = context.report.petConservation = {}
    const log = await logPosition(join(context.report.server.directory, 'logs', 'latest.log'))
    let setup = false
    try {
      await context.command(`/adaptqa setup ${actor.bot.username} ${other.bot.username}`, /^ADAPT_QA SETUP /, 30000)
      setup = true
      await context.step('Fully blocked natural melee hit does not hurt a pet', async () => {
        evidence.before = await fixture('prepare shared')
        await context.command(`/tp ${other.bot.username} 2.5 100 0.5`, /Teleported/, 5000)
        await context.command(`/give ${other.bot.username} minecraft:wooden_sword 1`, /Gave/, 5000)
        await context.waitUntil(() => Boolean(other.bot.inventory.items().find(item => item.name === 'wooden_sword')),
          { label: 'Attacker receives sword', timeoutMs: 5000 })
        await other.bot.equip(other.bot.inventory.items().find(item => item.name === 'wooden_sword'), 'hand')
        await actor.bot.lookAt(other.bot.entity.position.offset(0, 1, 0), true)
        actor.bot.activateItem(true)
        await actor.bot.waitForTicks(10)
        context.expect((await fixture('snapshot')).blocking, 'Victim is actually shield blocking')
        const victim = other.bot.players[actor.bot.username]?.entity
        context.expect(Boolean(victim), 'Attacker can see the ordinary victim')
        other.bot.attack(victim)
        await actor.bot.waitForTicks(15)
        actor.bot.deactivateItem()
        evidence.after = await fixture('snapshot')
        context.expect(evidence.after.hit && evidence.after.hit.base > 0 && evidence.after.hit.final < 0.00001,
          'Natural attack reached the shield and has zero final damage', evidence.after)
      })
      await context.step('An intercepted natural arrow does not also trigger Shared Pain', async () => {
        evidence.guardianBefore = await fixture('prepare guardian')
        actor.bot.deactivateItem()
        await context.command(`/tp ${other.bot.username} 6.5 100 0.5`, /Teleported/, 5000)
        await context.command(`/clear ${other.bot.username}`, /Removed/, 5000)
        await context.command(`/give ${other.bot.username} minecraft:bow 1`, /Gave/, 5000)
        await context.command(`/give ${other.bot.username} minecraft:arrow 1`, /Gave/, 5000)
        await context.waitUntil(() => Boolean(other.bot.inventory.items().find(item => item.name === 'bow')),
          { label: 'Attacker receives bow', timeoutMs: 5000 })
        await other.bot.equip(other.bot.inventory.items().find(item => item.name === 'bow'), 'hand')
        await other.bot.lookAt(actor.bot.entity.position.offset(0, 1.3, 0), true)
        other.bot.activateItem()
        await other.bot.waitForTicks(24)
        other.bot.deactivateItem()
        await context.waitUntil(async () => {
          evidence.guardianAfter = await fixture('snapshot')
          return evidence.guardianAfter.intercepts > evidence.guardianBefore.intercepts
        }, { label: 'Guardian intercepts the actual projectile', timeoutMs: 5000, intervalMs: 100 })
        await actor.bot.waitForTicks(5)
        evidence.guardianAfter = await fixture('snapshot')
      })
      evidence.log = await errorsSince(log)
      context.expect(evidence.log.errors.length === 0, 'Pet interactions emit no server errors', evidence.log)
      context.expect(evidence.after.petHealth === evidence.before.petHealth && evidence.after.health === evidence.before.health,
        'Fully blocked hit conserves both owner and pet health', evidence)
      context.expect(evidence.guardianAfter.sharedDamage === evidence.guardianBefore.sharedDamage
        && evidence.guardianAfter.health === evidence.guardianBefore.health,
      'Intercepted arrow consumes no Shared Pain transfer', evidence)
    } finally {
      actor.bot.deactivateItem()
      actor.bot.clearControlStates()
      other.bot.clearControlStates()
      if (setup && !context.signal.aborted) await context.command('/adaptqa cleanup', /^ADAPT_QA CLEANUP$/, 15000)
    }
  },
}
