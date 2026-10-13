import { randomBytes } from 'node:crypto'
import { fixtureJson } from './fixture-json.mjs'

export default {
  name: 'adapt-void-skin-absorption-baseline',
  description: 'Reproduce a missed lethal rescue with absorption on the pre-fix runtime.',
  async run(context) {
    context.expect(context.report.server.instance.startsWith('adapt-temporal-audit-'), 'Baseline targets a disposable temporal audit instance')
    const suffix = randomBytes(4).toString('hex')
    const actor = await context.connectActor(`AQATm${suffix}`)
    const other = await context.connectActor(`AQATo${suffix}`)
    const fixture = command => fixtureJson(context,
      `/adaptqa temporal ${actor.bot.username} ${command}`, `TEMPORAL ${command.split(' ')[0]}`, 10000)
    const evidence = context.report.voidSkinAbsorptionBaseline = {}
    let setup = false
    let observedDeath = false
    const onDeath = () => { observedDeath = true }
    actor.bot.on('death', onDeath)
    try {
      await context.command(`/adaptqa setup ${actor.bot.username} ${other.bot.username}`, /^ADAPT_QA SETUP /, 30000)
      setup = true
      evidence.before = await fixture('void-prepare')
      context.expect(evidence.before.health === 6 && evidence.before.absorption === 8, 'Live victim has six health and eight absorption', evidence.before)
      evidence.hit = await fixture('void-hit')
      await context.waitUntil(() => observedDeath, { label: 'Missed rescue is visible as player death', timeoutMs: 4000 })
      context.expect(evidence.hit.dead && evidence.hit.escapes === evidence.before.escapes,
        'Old runtime lets the absorbed lethal hit kill without a Void Skin escape', evidence.hit)
    } finally {
      actor.bot.off('death', onDeath)
      actor.bot.clearControlStates()
      other.bot.clearControlStates()
      if (setup && !context.signal.aborted) await context.command('/adaptqa cleanup', /^ADAPT_QA CLEANUP$/, 15000)
    }
  },
}
