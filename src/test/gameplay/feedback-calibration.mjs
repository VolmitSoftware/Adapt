import { randomBytes } from 'node:crypto'
import { fixtureJson } from './fixture-json.mjs'
import { observeParticlePackets } from './feedback.mjs'

export default {
  name: 'adapt-feedback-calibration',
  description: 'Calibrate actual Bukkit and Adapt dust packets and verify the player effects preference.',
  async run(context) {
    const suffix = randomBytes(4).toString('hex')
    const actor = await context.connectActor(`AQAa${suffix}`)
    const opponent = await context.connectActor(`AQAb${suffix}`)
    const particles = []
    const stopParticles = observeParticlePackets(actor.bot, packet => particles.push(packet))
    let setup = false
    let sequence = 0
    context.report.calibration = { cases: [], realClient: 'not verified' }
    try {
      await context.command(`/adaptqa setup ${actor.bot.username} ${opponent.bot.username}`, /^ADAPT_QA SETUP /, 60000)
      setup = true
      await context.waitUntil(async () => {
        const token = `cal${++sequence}`
        const state = await fixtureJson(context, `/adaptqa snapshot ${actor.bot.username} ${token}`, `SNAPSHOT ${token}`)
        return state.tps >= 19.25 && state.feedbackPosition?.world === state.world
      }, { label: 'feedback viewer registered and server TPS recovered', timeoutMs: 120000, intervalMs: 1000 })
      for (const enabled of [true, false, true]) {
        await context.step(`dust calibration with effects ${enabled ? 'enabled' : 'disabled'}`, async () => {
          await context.command(`/adaptqa effects ${enabled}`, new RegExp(`^ADAPT_QA EFFECTS ${enabled}$`), 5000)
          await actor.bot.waitForTicks(25)
          particles.length = 0
          await context.command('/adaptqa feedback', /^ADAPT_QA FEEDBACK DUST$/, 5000)
          await actor.bot.waitForTicks(25)
          const dust = particles.filter(packet => packet.name === 'dust')
          context.expect(dust.some(packet => packet.count === 2 && packet.dust?.rgb === 0xff0000 && packet.dust.scale === 1),
            'Direct Bukkit dust is received with exact color, count, and size')
          context.expect(dust.some(packet => packet.count === 3) === enabled,
            'Adapt dust honors the player effects preference')
          context.report.calibration.cases.push({ enabled, dust })
        })
      }
    } finally {
      stopParticles()
      if (setup && !context.signal.aborted) await context.command('/adaptqa cleanup', /^ADAPT_QA CLEANUP$/, 15000)
    }
  },
}
