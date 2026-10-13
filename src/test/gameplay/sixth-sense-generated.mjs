import { randomBytes } from 'node:crypto'
import { join } from 'node:path'
import { fixtureJson } from './fixture-json.mjs'
import { errorsSince, logPosition } from './runtime-log.mjs'

export default {
  name: 'adapt-sixth-sense-generated',
  description: 'Detect a native generated structure, enforce range and preference, and preserve terrain generation and owner tick progress during bounded sweeps.',
  async run(context) {
    context.expect(context.report.server.instance.startsWith('adapt-sense-audit-'), 'Generated terrain audit uses its disposable instance')
    const actor = await context.connectActor(`AQASs${randomBytes(3).toString('hex')}`)
    const fixture = command => fixtureJson(context,
      `/adaptqa sense ${actor.bot.username} ${command}`, `SENSE ${command.split(' ')[0]}`, 10000)
    const evidence = context.report.generatedSense = {}
    const observe = async () => ({ ...await fixture('snapshot'), position: {
      x: actor.bot.entity.position.x, y: actor.bot.entity.position.y, z: actor.bot.entity.position.z
    }, health: actor.bot.health })
    const log = await logPosition(join(context.report.server.directory, 'logs', 'latest.log'))
    try {
      await fixture('prepare')
      await context.waitUntil(async () => {
        evidence.prepared = await fixture('snapshot')
        context.expect(!evidence.prepared.failure, 'Bounded native terrain preparation succeeds', evidence.prepared)
        return evidence.prepared.ready
      }, { label: 'Native generated structure preparation', timeoutMs: 90000, intervalMs: 500 })
      await fixture('unload')
      await context.waitUntil(async () => {
        evidence.unloaded = await fixture('snapshot')
        return evidence.unloaded.startUnloaded
      }, { label: 'Generated native start unloads before sensing', timeoutMs: 20000, intervalMs: 500 })
      await fixture('learn')
      await context.waitUntil(async () => {
        evidence.detected = await observe()
        return evidence.detected.target && evidence.detected.senses > 0
      }, { label: 'Sixth Sense detects native generated metadata', timeoutMs: 40000, intervalMs: 500 })
      evidence.initial = evidence.detected
      await fixture('unload-target')
      await context.waitUntil(async () => (await fixture('snapshot')).ready,
        { label: 'Position outside the actual native start chunk', timeoutMs: 12000, intervalMs: 500 })
      await fixture('unload')
      await context.waitUntil(async () => (await fixture('snapshot')).startUnloaded,
        { label: 'Actual native target start unloads', timeoutMs: 20000, intervalMs: 500 })
      await fixture('learn')
      await context.waitUntil(async () => {
        evidence.detected = await observe()
        return evidence.detected.target && evidence.detected.targetWasUnloaded
      }, { label: 'Detect unloaded native structure again', timeoutMs: 40000, intervalMs: 500 })
      context.expect(evidence.detected.targetWasUnloaded,
        'The actual detected native start was unloaded before sensing', evidence.detected)
      context.expect(evidence.detected.distance > 16 && evidence.detected.distance <= 500,
        'Detected native structure lies within full range and outside the minimum radius', evidence.detected)
      await context.step('Minimum range suppresses a target beyond its configured radius', async () => {
        await fixture('range 16')
        await actor.bot.waitForTicks(120)
        evidence.restricted = await fixture('snapshot')
        context.expect(!evidence.restricted.target, 'Out-of-range structure does not appear in the HUD target', evidence.restricted)
      })
      await context.step('An absent structure preference suppresses other native structure families', async () => {
        await fixture('range 500')
        await fixture('preference FORTRESS')
        await actor.bot.waitForTicks(140)
        evidence.filtered = await fixture('snapshot')
        context.expect(!evidence.filtered.target, 'Overworld structure does not match the fortress preference', evidence.filtered)
      })
      await context.step('Generated-only sweep preserves terrain and continued player ticks', async () => {
        await fixture('preference DEFAULT')
        evidence.scanBefore = await fixture('snapshot')
        await actor.bot.waitForTicks(440)
        evidence.scanAfter = await fixture('snapshot')
        context.expect(evidence.scanAfter.target, 'Restored family selection detects generated terrain again', evidence.scanAfter)
        context.expect(evidence.scanAfter.newChunks === evidence.scanBefore.newChunks,
          '500-block sweeps create no terrain chunks', { before: evidence.scanBefore, after: evidence.scanAfter })
        context.expect(evidence.scanAfter.ticks - evidence.scanBefore.ticks >= 400,
          'Entity-owned heartbeat continues throughout the sweep', { before: evidence.scanBefore.ticks, after: evidence.scanAfter.ticks })
      })
      evidence.log = await errorsSince(log)
      context.expect(evidence.log.errors.length === 0, 'Generated-only scans have no runtime exceptions', evidence.log)
    } finally {
      actor.bot.clearControlStates()
      if (!context.signal.aborted) await fixture('cleanup')
    }
  },
}
