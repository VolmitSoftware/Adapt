import { randomBytes } from 'node:crypto'
import { fixtureJson } from './fixture-json.mjs'
import { synchronizePlayerInput } from './player-input.mjs'

export default {
  name: 'adapt-iris-preference-latch',
  description: 'Verify ordinary hold and latched felling through real Iris provenance, natural dig and sneak packets.',
  async run(context) {
    if (context.options.command !== 'reuse') {
      await context.command('/iris create name=adapt_gameplay_iris type=native-terrain seed=78264193', /Successfully created your world/i, 180000)
    }
    const suffix = randomBytes(4).toString('hex')
    const actor = await context.connectActor(`AQAil${suffix}`)
    const other = await context.connectActor(`AQAio${suffix}`)
    const stop = synchronizePlayerInput(actor.bot)
    const evidence = context.report.irisLatch = { trials: [] }
    const preference = command => fixtureJson(context,
      `/adaptqa preferences ${actor.bot.username} ${command}`, `PREFERENCES ${command.split(' ')[0]}`)
    const policy = value => fixtureJson(context,
      `/adaptqa catalog-behavior ${actor.bot.username} iris-policy ${value}`, 'CATALOG_BEHAVIOR iris-policy')
    const snapshot = () => fixtureJson(context, `/adaptqa snapshot ${actor.bot.username} iris-latch`, 'SNAPSHOT iris-latch')
    let setup = false
    let changedPolicy = false
    async function trial(latched, cancel) {
      await context.command('/adaptqa stage integration-iris-latch', /^ADAPT_QA STAGE integration-iris-latch$/, 30000)
      await preference(`set axe-iris-feller latched-run ${latched ? 'ON' : 'OFF'}`)
      await actor.bot.equip(actor.bot.inventory.items().find(item => item.name === 'iron_axe'), 'hand')
      const before = await snapshot()
      context.expect(before.integration.iris?.enabled && before.integration.logs === 40
        && before.integration.markedLogs.every(Boolean) && !before.integration.standaloneAllowed,
        'Forty real Iris logs and ordinary player permissions are established', before.integration)
      const target = actor.bot.entity.position.clone().set(3, 100, 0)
      await context.waitUntil(() => actor.bot.blockAt(target)?.name === 'oak_log', { label: 'Marked tree arrives', timeoutMs: 10000 })
      actor.bot.setControlState('sneak', true)
      await actor.bot.waitForTicks(3)
      await actor.bot.dig(actor.bot.blockAt(target))
      let accepted
      await context.waitUntil(async () => {
        accepted = await snapshot()
        return accepted.integration.logs < 40
      }, { label: 'Iris run has begun removing actual logs', timeoutMs: 5000, intervalMs: 10 })
      context.expect(accepted.integration.logs > 0, 'The accepted run still has logs when control is released', accepted.integration)
      actor.bot.setControlState('sneak', false)
      await actor.bot.waitForTicks(2)
      const released = await snapshot()
      if (cancel) {
        actor.bot.setControlState('sneak', true)
        await actor.bot.waitForTicks(2)
        actor.bot.setControlState('sneak', false)
      }
      await actor.bot.waitForTicks(35)
      const after = await snapshot()
      if (latched && !cancel) {
        context.expect(released.integration.logs > 0 && after.integration.logs === 0,
          'Latched felling continues after release and completes the remaining real tree', { released, after })
        context.expect(after.integration.recoveredLogs - before.integration.recoveredLogs === 40,
          'Latched run recovers all forty logs exactly once')
      } else {
        context.expect(after.integration.logs > 0 && after.integration.logs < 40,
          cancel ? 'A second sneak stops the accepted latched run' : 'Default hold stops when sneak is released', after.integration)
      }
      context.expect(before.food === after.food, 'Explicit zero-cost fixture policy is respected')
      evidence.trials.push({ latched, cancel, before, accepted, released, after })
    }
    try {
      await context.command(`/adaptqa setup ${actor.bot.username} ${other.bot.username}`, /^ADAPT_QA SETUP /, 30000)
      setup = true
      await preference('learn axe-iris-feller')
      evidence.controlledPolicy = await policy(true)
      changedPolicy = true
      await trial(false, false)
      await trial(true, false)
      await trial(true, true)
      evidence.restoredPolicy = await policy(false)
      changedPolicy = false
      await preference('set axe-iris-feller latched-run OFF')
      evidence.costTrials = []
      for (const level of [0, 3]) {
        await context.command(`/adaptqa learn axe-iris-feller ${level}`, /^ADAPT_QA LEARN /, 5000)
        await context.command('/adaptqa stage integration-iris', /^ADAPT_QA STAGE integration-iris$/, 30000)
        await actor.bot.equip(actor.bot.inventory.items().find(item => item.name === 'iron_axe'), 'hand')
        const before = await snapshot()
        const target = actor.bot.entity.position.clone().set(3, 100, 0)
        await context.waitUntil(() => actor.bot.blockAt(target)?.name === 'oak_log', { label: 'Cost trial tree arrives', timeoutMs: 10000 })
        actor.bot.setControlState('sneak', true)
        await actor.bot.waitForTicks(3)
        await actor.bot.dig(actor.bot.blockAt(target))
        await actor.bot.waitForTicks(40)
        actor.bot.setControlState('sneak', false)
        const after = await snapshot()
        context.expect(after.integration.logs === (level === 0 ? 3 : 0),
          'Only learned felling removes the entire four-log tree', { level, before, after })
        context.expect(before.food - after.food === (level === 0 ? 0 : 4 * evidence.restoredPolicy.hunger),
          'Default server hunger cost is charged for every actual learned log', { level, before, after })
        evidence.costTrials.push({ level, before, after })
      }
    } finally {
      actor.bot.clearControlStates()
      other.bot.clearControlStates()
      stop()
      if (changedPolicy && !context.signal.aborted) await policy(false)
      if (setup && !context.signal.aborted) await context.command('/adaptqa cleanup', /^ADAPT_QA CLEANUP$/, 15000)
    }
  },
}
