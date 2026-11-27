import { randomBytes } from 'node:crypto'
import { join } from 'node:path'
import temporal from './temporal-overlap-conservation.mjs'
import { fixtureJson } from './fixture-json.mjs'
import { errorsSince, logPosition } from './runtime-log.mjs'
import { disposableInstance } from './disposable-instance.mjs'

export default {
  name: 'adapt-skill-interactions',
  description: 'Assert competing death saves, cancelled damage debt, and cooperative pearl modifiers on live players.',
  async run(context) {
    context.expect(disposableInstance(context.report.server.instance, 'adapt-interactions-'), 'Disposable interaction target required')
    const log = await logPosition(join(context.report.server.directory, 'logs', 'latest.log'))
    await temporal.run(context)
    const suffix = randomBytes(3).toString('hex')
    const opponent = await context.connectActor(`AQAO${suffix}`)
    const catalogue = await fixtureJson(context, '/adaptqa catalog audit', 'CATALOG audit')
    const levels = Object.fromEntries(catalogue.skills.flatMap(skill => skill.adaptations.map(a => [a.name, a.maxLevel])))
    const ids = ['adapt:tragoul/last_rites', 'adapt:chronos/hourglass_guard', 'adapt:rift/void_skin']
    const statKeys = ['tragoul.last-rites.deaths-defied', 'chronos.hourglass-guard.saves', 'rift.void-skin.escapes']
    const cases = [[0], [1], [2], [0, 1], [1, 2], [0, 2], [0, 1, 2], [1, 'debt'], [0, 1, 2, 'totem'], [0, 1, 2, 'priority']]
    context.report.deathSaves = []
    let index = 0
    for (const combination of cases) {
      const actor = await context.connectActor(`AQAD${index++}${suffix}`)
      let setup = false
      try {
        await context.command(`/adaptqa setup ${actor.bot.username} ${opponent.bot.username}`, /^ADAPT_QA SETUP /, 30000)
        setup = true
        const fixture = command => fixtureJson(context, `/adaptqa temporal ${actor.bot.username} ${command}`, `TEMPORAL ${command}`)
        const snapshot = () => fixtureJson(context, `/adaptqa snapshot ${actor.bot.username} death`, 'SNAPSHOT death')
        await fixture('void-prepare')
        for (let n = 0; n < combination.length; n++) {
          const item = combination[n]
          if (typeof item !== 'number') continue
          const command = n === 0 ? 'learn' : 'learn-add'
          await context.command(`/adaptqa ${command} ${ids[item]} ${levels[ids[item]]}`, /^ADAPT_QA LEARN/)
        }
        if (combination.includes('debt')) await context.command(`/adaptqa learn-add adapt:chronos/borrowed_time ${levels['adapt:chronos/borrowed_time']}`, /^ADAPT_QA LEARN/)
        if (combination.includes('priority')) await fixtureJson(context, `/adaptqa preferences ${actor.bot.username} set adapt:rift/void_skin death-save-priority FIRST`, 'PREFERENCES set')
        if (combination.includes('totem')) await fixture('totem')
        await context.step(`Competing saves ${combination.map(i => ids[i] ?? i).join(' + ')}`, async () => {
          const before = await snapshot()
          await context.waitUntil(async () => (await fixture(combination.includes('debt') ? 'lethal-hit' : 'void-hit')).delivered,
            { label: 'lethal hit reaches the player after the arena world change', timeoutMs: 6000, intervalMs: 100 })
          let after
          await context.waitUntil(async () => {
            after = await snapshot()
            return statKeys.reduce((sum, key) => sum + (after.stats[key] ?? 0) - (before.stats[key] ?? 0), 0) >= 1
          }, { label: 'one save finishes', timeoutMs: 7000, intervalMs: 100 })
          const deltas = Object.fromEntries(statKeys.map(key => [key, (after.stats[key] ?? 0) - (before.stats[key] ?? 0)]))
          context.expect(Object.values(deltas).reduce((a, b) => a + b, 0) === 1, 'Exactly one save activates for one lethal hit', deltas)
          const winner = combination.includes('priority') ? 2 : Math.min(...combination.filter(value => typeof value === 'number'))
          context.expect(deltas[statKeys[winner]] === 1, 'Save priority selects the expected eligible source', deltas)
          if (combination.includes('debt')) {
            const debt = await fixture('snapshot')
            context.expect(debt.borrowedDebt == null && (after.stats['chronos.borrowed-time.damage-deferred'] ?? 0) === (before.stats['chronos.borrowed-time.damage-deferred'] ?? 0), 'Cancelled hit creates no debt or deferred-damage credit', debt)
          }
          context.expect(after.health > 0, 'Saved player remains alive', after.health)
          const result = { skills: combination.map(i => ids[i] ?? i), before: { health: before.health, absorption: before.defense.absorption }, after: { health: after.health, absorption: after.defense.absorption }, deltas, deferred: after.stats['chronos.borrowed-time.damage-deferred'] ?? 0 }
          if (combination.includes('totem')) {
            await actor.bot.waitForTicks(3)
            result.totems = actor.bot.inventory.slots.reduce((sum, item) => sum + (item?.name === 'totem_of_undying' ? item.count : 0), 0)
            context.expect(result.totems === 1, 'Adapt save leaves equipped totem available', result)
          }
          context.report.deathSaves.push(result)
        })
      } finally {
        actor.bot.clearControlStates()
        if (setup && !context.signal.aborted) await context.command('/adaptqa cleanup', /^ADAPT_QA CLEANUP$/, 15000)
      }
    }
    const actor = await context.connectActor(`AQAP${suffix}`)
    let setup = false
    try {
      await context.command(`/adaptqa setup ${actor.bot.username} ${opponent.bot.username}`, /^ADAPT_QA SETUP /, 30000)
      setup = true
      context.report.pearlInteractions = []
      const combos = [[], ['adapt:ranged/fetch_shot'], ['adapt:ranged/floaters'], ['adapt:ranged/pinning_shot'], ['adapt:ranged/ricochet_bolt'], ['adapt:ranged/fetch_shot', 'adapt:ranged/floaters', 'adapt:ranged/pinning_shot']]
      for (const additional of combos) {
        await context.step(`Pearl Rebound with ${additional.join(', ') || 'no ranged modifiers'}`, async () => {
          await context.command('/adaptqa stage rift-rebound', /^ADAPT_QA STAGE /)
          await context.command(`/adaptqa learn adapt:rift/pearl_rebound ${levels['adapt:rift/pearl_rebound']}`, /^ADAPT_QA LEARN/)
          for (const id of additional) await context.command(`/adaptqa learn-add ${id} ${levels[id]}`, /^ADAPT_QA LEARN/)
          const snapshot = () => fixtureJson(context, `/adaptqa snapshot ${actor.bot.username} pearl`, 'SNAPSHOT pearl')
          await context.waitUntil(() => actor.bot.inventory.items().some(item => item.name === 'ender_pearl'), { label: 'pearl inventory arrives', timeoutMs: 5000 })
          await actor.bot.equip(actor.bot.inventory.items().find(item => item.name === 'ender_pearl'), 'hand')
          actor.bot.clearControlStates()
          const before = await snapshot()
          await actor.bot.lookAt(actor.bot.entity.position.clone().set(8, 102.8, 0.5), true)
          await actor.bot.waitForTicks(2)
          actor.bot.activateItem()
          actor.bot.deactivateItem()
          await actor.bot.waitForTicks(1)
          await actor.bot.lookAt(actor.bot.entity.position.clone().set(-8, 103.5, 0.5), true)
          actor.bot._client.write('look', {
            yaw: 180 - actor.bot.entity.yaw * 180 / Math.PI,
            pitch: -actor.bot.entity.pitch * 180 / Math.PI,
            flags: { onGround: actor.bot.entity.onGround, hasHorizontalCollision: false },
          })
          await context.waitUntil(async () => Math.abs((await snapshot()).location.yaw - 90) < 1,
            { label: 'server receives the backward aim', timeoutMs: 2000, intervalMs: 25 })
          await context.waitUntil(async () => {
            const moved = Math.hypot(actor.bot.entity.position.x - before.location.x, actor.bot.entity.position.z - before.location.z) > 1
            return moved || (await snapshot()).rift?.teleport?.cause === 'ENDER_PEARL'
          }, { label: 'pearl completes teleport', timeoutMs: 10000, intervalMs: 100 })
          await actor.bot.waitForTicks(5)
          const after = await snapshot()
          const rebounds = (after.stats['rift.pearl-rebound.rebounds'] ?? 0) - (before.stats['rift.pearl-rebound.rebounds'] ?? 0)
          const expected = 1
          const result = { additional, rebounds, expectedRebounds: expected, destination: after.location }
          context.report.pearlInteractions.push(result)
          context.expect(rebounds === expected, 'Cooperative modifiers preserve exactly one pearl rebound', result)
        })
      }
      context.report.interactionLog = await errorsSince(log)
      context.expect(context.report.interactionLog.errors.length === 0, 'No server errors during probes', context.report.interactionLog)
    } finally {
      actor.bot.clearControlStates()
      opponent.bot.clearControlStates()
      if (setup && !context.signal.aborted) await context.command('/adaptqa cleanup', /^ADAPT_QA CLEANUP$/, 15000)
    }
  },
}
