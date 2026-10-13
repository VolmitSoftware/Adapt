import { once } from 'node:events'
import { fixtureJson } from './fixture-json.mjs'
import { errorsSince, logPosition } from './runtime-log.mjs'
import { join } from 'node:path'

export default {
  name: 'adapt-scaffolder-retirement-folia',
  description: 'Verify the real Scaffolder refund helper conserves a named loaded shulker when its retained owner entity has retired.',
  async run(context) {
    context.expect(context.report.server.instance.startsWith('adapt-items-retirement-folia-'), 'Retirement audit uses its isolated Folia instance')
    let actor = await context.connectActor('AQAItemRetired')
    const observer = await context.connectActor('AQAItemWatch')
    const log = await logPosition(join(context.report.server.directory, 'logs', 'latest.log'))
    const fixture = (player, action) => fixtureJson(context, `/adaptqa items-audit ${player} ${action}`, `ITEMS_AUDIT ${action.split(' ')[0]}`, 10000)
    const evidence = context.report.scaffolderRetirement = {}
    try {
      await context.command('/gamemode spectator @a', /Set .* game mode/, 10000)
      await context.command(`/tp ${observer.bot.username} ${actor.bot.username}`, /Teleported/, 10000)
      await observer.bot.waitForTicks(5)
      const armed = evidence.armed = await fixture(actor.bot.username, 'retirement-arm')
      context.expect(armed.original.name === 'Retired Retention Box' && armed.original.contents.some(item => item.type === 'DIAMOND' && item.amount === 3), 'Refund source preserves a named shulker containing three diamonds', armed)
      await context.step('Retired entity delivery returns exactly one loaded shulker on its original region', async () => {
        actor = await actor.reconnectAfter(async () => {
          const ended = once(actor.bot, 'end')
          actor.bot.quit('Scaffolder retirement conservation')
          await ended
          await observer.bot.waitForTicks(5)
          evidence.refund = await fixture(observer.bot.username, `retirement-refund ${armed.owner}`)
          context.expect(evidence.refund.ownerOnline === false, 'Actual retained player entity is offline before refund scheduling', evidence.refund)
          await context.waitUntil(async () => {
            evidence.after = await fixture(observer.bot.username, 'retirement-snapshot')
            return evidence.after.drops.some(item => item.type === 'PURPLE_SHULKER_BOX')
          }, { label: 'Retired owner refund appears as a world drop', timeoutMs: 5000 })
          await observer.bot.waitForTicks(10)
          evidence.after = await fixture(observer.bot.username, 'retirement-snapshot')
          const boxes = evidence.after.drops.filter(item => item.type === 'PURPLE_SHULKER_BOX')
          context.expect(boxes.length === 1 && boxes[0].amount === 1 && boxes[0].name === 'Retired Retention Box', 'Retired delivery returns exactly one named shulker', evidence.after)
          context.expect(boxes[0].contents.some(item => item.type === 'DIAMOND' && item.amount === 3) && !evidence.after.drops.some(item => item.type === 'DIAMOND'), 'The three diamonds remain contained exactly once', evidence.after)
        }, { timeoutMs: 20000 })
        context.expect(!actor.bot.inventory.items().some(item => item.name === 'purple_shulker_box'), 'Retired player inventory receives no duplicate refund after reconnect')
      })
      evidence.log = await errorsSince(log)
      context.expect(evidence.log.errors.length === 0, 'Retirement refund has no Folia scheduler or region exceptions', evidence.log)
    } finally {
      actor.bot.clearControlStates()
      observer.bot.clearControlStates()
    }
  },
}
