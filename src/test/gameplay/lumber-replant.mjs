import { randomBytes } from 'node:crypto'
import { fixtureJson } from './fixture-json.mjs'

export default {
  name: 'adapt-lumber-replant',
  description: 'Fell an oak through the Lumber worker, then replant from a delayed dropped sapling.',
  async run(context) {
    context.bot.physicsEnabled = false
    const suffix = randomBytes(3).toString('hex')
    const actor = await context.connectActor(`AQAlumb${suffix}`)
    actor.bot.physicsEnabled = false
    const peer = await context.connectActor(`AQApeer${suffix}`)
    peer.bot.physicsEnabled = false
    const lumber = action => fixtureJson(context, `/adaptqa lumber-replant ${actor.bot.username} ${action}`,
      `LUMBER_REPLANT ${action}`, 10000)
    let setup = false
    let prepared = false
    let staged = false
    let initial
    let felled
    let replanted
    try {
      await context.command(`/adaptqa setup ${actor.bot.username} ${peer.bot.username}`, /^ADAPT_QA SETUP /, 30000)
      setup = true
      await lumber('prepare')
      prepared = true
      await context.waitUntil(async () => (await lumber('ready')).ready,
        { label: 'Fresh arena chunks and node index are ready', timeoutMs: 10000, intervalMs: 100 })
      await context.step('Lumber completes felling with no saplings available', async () => {
        initial = await lumber('stage')
        staged = true
        context.expect(initial.logs === 5 && initial.golemAlive, 'A bound Lumber golem and a five-log oak are present', initial)
        context.expect(initial.cargoSaplings + initial.chestSaplings + initial.playerSaplings + initial.seedRemaining === 0,
          'No replanting supply is available before felling', initial)
        await context.waitUntil(async () => {
          felled = await lumber('snapshot')
          return felled.logs === 0 && felled.tasksCompleted >= 1
        }, { label: 'Production Lumber worker finishes felling', timeoutMs: 60000, intervalMs: 500 })
        context.expect(felled.stump === 'AIR' && felled.golemAlive, 'The job completes and releases the golem without inventing a sapling', felled)
        context.expect(felled.cargoSaplings + felled.chestSaplings + felled.playerSaplings + felled.seedRemaining === 0,
          'No saplings appeared during felling', felled)
      })
      await context.step('A later ground drop replants without waiting for the full area scan', async () => {
        const dropped = await lumber('seed')
        context.expect(dropped.seedRemaining === 2 && dropped.stump === 'AIR', 'Two saplings drop only after the original job completes', dropped)
        context.expect(dropped.areaVolume === 274625, 'The node spans a full radius-32 cube for the retry', dropped)
        await context.waitUntil(async () => {
          replanted = await lumber('snapshot')
          return replanted.stump === 'OAK_SAPLING'
        }, { label: 'A pending stump uses the delayed sapling', timeoutMs: 30000, intervalMs: 500 })
        context.expect(replanted.seedRemaining === 1, 'Exactly one dropped sapling is consumed', replanted)
        context.expect(replanted.cargoSaplings + replanted.chestSaplings + replanted.playerSaplings === 0,
          'No sapling is duplicated into cargo, the chest, or the owner inventory', replanted)
        const position = actor.bot.entity.position.clone().set(initial.treeX, initial.treeY, initial.treeZ)
        await context.waitUntil(() => actor.bot.blockAt(position)?.name === 'oak_sapling',
          { label: 'The protocol client receives the replanted sapling block', timeoutMs: 5000 })
        context.report.lumberReplant = { initial, felled, dropped, replanted }
      })
    } finally {
      for (const player of [actor, peer]) {
        player.bot.clearControlStates()
        if (player.bot.currentWindow) player.bot.closeWindow(player.bot.currentWindow)
      }
      if ((staged || prepared) && !context.signal.aborted) await lumber('cleanup')
      if (setup && !context.signal.aborted) await context.command('/adaptqa cleanup', /^ADAPT_QA CLEANUP$/, 15000)
    }
  },
}
