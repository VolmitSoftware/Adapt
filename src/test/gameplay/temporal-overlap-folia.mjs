import { randomBytes } from 'node:crypto'
import { fixtureJson } from './fixture-json.mjs'
import { errorsSince, logPosition } from './runtime-log.mjs'
import { join } from 'node:path'

export default {
  name: 'adapt-temporal-overlap-folia',
  description: 'Run temporal overlap and trident continuation against Folia entities on their owning regions in the existing world.',
  async run(context) {
    context.expect(context.report.server.instance.startsWith('adapt-temporal-audit-folia-'), 'Folia temporal audit targets its disposable instance')
    const actor = await context.connectActor(`AQAFT${randomBytes(3).toString('hex')}`)
    const fixture = command => fixtureJson(context,
      `/adaptqa temporal ${actor.bot.username} ${command}`, `TEMPORAL ${command.split(' ')[0]}`, 10000)
    const evidence = context.report.temporalFolia = {}
    const log = await logPosition(join(context.report.server.directory, 'logs', 'latest.log'))
    try {
      await actor.bot.waitForTicks(50)
      await context.step('Folia preserves motion under both production freeze release orders', async () => {
        for (const first of ['bomb', 'stasis']) {
          const result = await fixture(`overlap ${first}`)
          evidence[first] = result
          context.expect(result.frozen && result.afterFirstFrozen && !result.afterFirstGravity,
            'The earlier release retains the second region-owned freeze', result)
          context.expect(result.gravity && result.persistent && !result.remainingFrozen && result.speed > 0.4,
            'The final release restores original projectile motion on its owning region', result)
        }
      })
      await context.step('Folia trident continuation respects temporal ownership', async () => {
        evidence.trident = await fixture('trident')
        context.expect(evidence.trident.frozenSpeed === 0 && evidence.trident.recallSpeed > 0.1,
          'Recall remains paused while frozen and resumes after release', evidence.trident)
      })
      evidence.log = await errorsSince(log)
      context.expect(evidence.log.errors.length === 0, 'Folia temporal owner tasks have no server exceptions', evidence.log)
    } finally {
      actor.bot.clearControlStates()
    }
  },
}
