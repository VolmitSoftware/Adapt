import { randomBytes } from 'node:crypto'
import { join } from 'node:path'
import { fixtureJson } from './fixture-json.mjs'
import { errorsSince, logPosition } from './runtime-log.mjs'

export default {
  name: 'adapt-demolition-transform-conservation',
  description: 'Verify path flattening and tilling conserve named tools while normal placed blocks are returned exactly once.',
  async run(context) {
    context.expect(context.report.server.instance.startsWith('adapt-demolition-dupe-'), 'Demolition fixture targets its disposable server')
    const suffix = randomBytes(4).toString('hex')
    const actor = await context.connectActor(`AQAd${suffix}`)
    const other = await context.connectActor(`AQAo${suffix}`)
    const fixture = command => fixtureJson(context,
      `/adaptqa demolition ${actor.bot.username} ${command}`, `DEMOLITION ${command.split(' ')[0]}`, 10000)
    const preferences = command => fixtureJson(context,
      `/adaptqa preferences ${actor.bot.username} ${command}`, `PREFERENCES ${command.split(' ')[0]}`, 10000)
    const evidence = context.report.demolitionConservation = { transforms: [] }
    const position = actor.bot.entity.position.clone().set(2, 99, 0)
    const total = (state, material) => state.items.filter(item => item.material === material).reduce((sum, item) => sum + item.amount, 0)
    const log = await logPosition(join(context.report.server.directory, 'logs', 'latest.log'))
    let setup = false
    try {
      await context.command(`/adaptqa setup ${actor.bot.username} ${other.bot.username}`, /^ADAPT_QA SETUP /, 30000)
      setup = true
      await preferences('learn architect-demolition')
      if (context.report.demolitionProfile) {
        evidence.profile = (await fixture('profile')).learned
        context.expect(Object.keys(evidence.profile).length === 21, 'Complete learned profile contains all 21 adaptations', evidence.profile)
      }
      for (const mode of [
        { name: 'path', block: 'dirt_path', material: 'DIAMOND_SHOVEL' },
        { name: 'till', block: 'farmland', material: 'DIAMOND_HOE' },
      ]) {
        await context.step(`${mode.name} transformations conserve one named damaged tool`, async () => {
          const before = await fixture(`prepare ${mode.name}`)
          context.expect(total(before, mode.material) === 1, 'Fixture supplies exactly one original tool', before)
          actor.bot.setQuickBarSlot(0)
          for (let repeat = 0; repeat < 3; repeat++) {
            if (repeat > 0) await fixture('soil')
            await context.waitUntil(() => actor.bot.blockAt(position)?.name === 'dirt', { label: 'Natural dirt loads', timeoutMs: 5000 })
            await actor.bot.activateBlock(actor.bot.blockAt(position), position.clone().set(0, 1, 0))
            await context.waitUntil(() => actor.bot.blockAt(position)?.name === mode.block,
              { label: `Actual tool interaction creates ${mode.block}`, timeoutMs: 5000 })
            const transformed = await fixture('snapshot')
            context.expect(total(transformed, mode.material) === 1, 'Transformation consumes durability without adding a tool', transformed)
            await actor.bot.dig(actor.bot.blockAt(position))
            await context.waitUntil(() => actor.bot.blockAt(position)?.name === 'air', { label: 'Transformed ground breaks', timeoutMs: 5000 })
            await actor.bot.waitForTicks(5)
            const after = await fixture('snapshot')
            const tools = after.items.filter(item => item.material === mode.material)
            const measurement = { mode: mode.name, repeat, before, transformed, after, total: total(after, mode.material) }
            evidence.transforms.push(measurement)
            context.expect(measurement.total === 1, `${mode.name} breaking conserves total tools across inventory and dropped entities`, measurement)
            context.expect(tools.every(item => item.name === 'Groundwork Tool' && item.damage >= 38), 'The original tool retains its name and accumulated wear', tools)
            const dirt = after.items.filter(item => item.material === 'DIRT')
            context.expect(dirt.reduce((sum, item) => sum + item.amount, 0) >= repeat + 1, 'Natural transformed ground produces its normal dirt drop', after)
          }
        })
      }
      await context.step('Normal placement returns its named block exactly once', async () => {
        const before = await fixture('prepare placed')
        context.expect(total(before, 'STONE') === 1, 'Fixture supplies one named stone block')
        actor.bot.setQuickBarSlot(0)
        actor.bot.setControlState('sneak', true)
        await actor.bot.waitForTicks(2)
        const anchor = position.clone().set(2, 100, 2)
        const placed = anchor.offset(0, 0, -1)
        await context.waitUntil(() => actor.bot.blockAt(anchor)?.name === 'stone', { label: 'Placement anchor loads', timeoutMs: 5000 })
        await actor.bot.placeBlock(actor.bot.blockAt(anchor), anchor.clone().set(0, 0, -1))
        await context.waitUntil(() => actor.bot.blockAt(placed)?.name === 'stone', { label: 'Real survival placement creates stone', timeoutMs: 5000 })
        const consumed = await fixture('snapshot')
        context.expect(total(consumed, 'STONE') === 0, 'Actual placement consumes its single source block', consumed)
        await actor.bot.dig(actor.bot.blockAt(placed))
        await context.waitUntil(() => actor.bot.blockAt(placed)?.name === 'air', { label: 'Demolition removes the placed block', timeoutMs: 5000 })
        await actor.bot.waitForTicks(5)
        const after = await fixture('snapshot')
        evidence.placement = { before, consumed, after }
        context.expect(total(after, 'STONE') === 1, 'Demolition restores exactly one source block', after)
        context.expect(after.items.filter(item => item.material === 'STONE').every(item => item.name === 'Restitution Stone'),
          'Normal placement restitution preserves source metadata', after)
      })
      evidence.log = await errorsSince(log)
      context.expect(evidence.log.errors.length === 0, 'Demolition interactions produce no server errors', evidence.log)
    } finally {
      for (const player of [actor, other]) {
        player.bot.clearControlStates()
        if (player.bot.currentWindow) player.bot.closeWindow(player.bot.currentWindow)
      }
      if (setup && !context.signal.aborted) await context.command('/adaptqa cleanup', /^ADAPT_QA CLEANUP$/, 15000)
    }
  },
}
