import { randomBytes } from 'node:crypto'
import { join } from 'node:path'
import { fixtureJson } from './fixture-json.mjs'
import { errorsSince, logPosition } from './runtime-log.mjs'

export default {
  name: 'adapt-temporal-overlap-conservation',
  description: 'Exercise absorbed lethal damage, overlapping production freezes, recall flight restoration and frozen trident recall on live entities.',
  async run(context) {
    context.expect(['adapt-temporal-audit-', 'adapt-interactions-'].some(prefix => context.report.server.instance.startsWith(prefix)), 'Temporal audit targets its disposable instance')
    const suffix = randomBytes(4).toString('hex')
    const actor = await context.connectActor(`AQATm${suffix}`)
    const other = await context.connectActor(`AQATo${suffix}`)
    const fixture = command => fixtureJson(context,
      `/adaptqa temporal ${actor.bot.username} ${command}`, `TEMPORAL ${command.split(' ')[0]}`, 10000)
    const evidence = context.report.temporalOverlap = {}
    const log = await logPosition(join(context.report.server.directory, 'logs', 'latest.log'))
    let setup = false
    try {
      await context.command(`/adaptqa setup ${actor.bot.username} ${other.bot.username}`, /^ADAPT_QA SETUP /, 30000)
      setup = true
      await context.step('Void Skin rescues lethal damage remaining after absorption', async () => {
        evidence.voidBefore = await fixture('void-prepare')
        context.expect(evidence.voidBefore.health === 6 && evidence.voidBefore.absorption === 8, 'Live victim has six health and eight absorption', evidence.voidBefore)
        await fixture('void-hit')
        await context.waitUntil(async () => {
          evidence.voidAfter = await fixture('snapshot')
          return evidence.voidAfter.escapes > evidence.voidBefore.escapes
        }, { label: 'Void Skin completes actual rescue teleport', timeoutMs: 8000, intervalMs: 100 })
        context.expect(!evidence.voidAfter.dead && evidence.voidAfter.health > 0 && evidence.voidAfter.pearls === 1,
          'Rescue preserves life and consumes exactly one pearl', evidence.voidAfter)
      })
      await context.step('Both field expiry orders restore shared projectile motion', async () => {
        for (const first of ['bomb', 'stasis']) {
          const result = await fixture(`overlap ${first}`)
          evidence[first] = result
          context.expect(result.frozen && result.afterFirstFrozen && !result.afterFirstGravity,
            'The first release leaves the other production freeze active', result)
          context.expect(result.gravity && result.persistent && !result.remainingFrozen && result.speed > 0.4,
            'The final release restores original gravity, persistence and motion', result)
        }
      })
      await context.step('Trident recall pauses until the production time freeze releases', async () => {
        evidence.trident = await fixture('trident')
        context.expect(evidence.trident.frozenSpeed === 0 && evidence.trident.recallSpeed > 0.1,
          'Frozen trident remains stationary and resumes recall after thaw', evidence.trident)
      })
      await context.step('Recall restores pre-freeze survival flight flags', async () => {
        await fixture('recall-prepare')
        await actor.bot.waitForTicks(30)
        evidence.recallStart = await fixture('recall')
        context.expect(evidence.recallStart.mode === 'SPECTATOR', 'Actual recall animation starts in its temporary spectator state', evidence.recallStart)
        await context.waitUntil(async () => {
          evidence.recallAfter = await fixture('snapshot')
          return evidence.recallAfter.mode === 'SURVIVAL'
        }, { label: 'Recall completes its animation', timeoutMs: 5000, intervalMs: 100 })
        context.expect(!evidence.recallAfter.allowFlight && !evidence.recallAfter.flying,
          'Survival player retains no flight flags after recall and freeze cleanup', evidence.recallAfter)
      })
      evidence.log = await errorsSince(log)
      context.expect(evidence.log.errors.length === 0, 'Temporal interactions produce no server errors', evidence.log)
    } finally {
      actor.bot.clearControlStates()
      other.bot.clearControlStates()
      if (setup && !context.signal.aborted) await context.command('/adaptqa cleanup', /^ADAPT_QA CLEANUP$/, 15000)
    }
  },
}
