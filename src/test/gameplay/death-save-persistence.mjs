import { join } from 'node:path'
import { fixtureJson } from './fixture-json.mjs'
import { errorsSince, logPosition } from './runtime-log.mjs'

export default {
  name: 'adapt-death-save-persistence',
  description: 'Assert death-save cooldowns survive reconnects and a second run after a server restart.',
  async run(context) {
    context.expect(context.report.server.instance.startsWith('adapt-interactions-'), 'Disposable interaction instance required')
    const log = await logPosition(join(context.report.server.directory, 'logs', 'latest.log'))
    const restart = context.options.command === 'verify-restart'
    const opponent = await context.connectActor('AQADSCopponent')
    const cases = [
      ['tragoul-last-rites', 'tragoul.last-rites.deaths-defied'],
      ['chronos-hourglass-guard', 'chronos.hourglass-guard.saves'],
      ['rift-void-skin', 'rift.void-skin.escapes'],
    ]
    context.report.cooldownPersistence = []
    for (let index = 0; index < cases.length; index++) {
      const [id, stat] = cases[index]
      let actor = await context.connectActor(`AQADSC${index}`)
      let setup = false
      const snapshot = () => fixtureJson(context, `/adaptqa snapshot ${actor.bot.username} cooldown`, 'SNAPSHOT cooldown')
      const fixture = operation => fixtureJson(context, `/adaptqa temporal ${actor.bot.username} ${operation}`, `TEMPORAL ${operation}`)
      const prepare = async () => {
        await context.command(`/adaptqa setup ${actor.bot.username} ${opponent.bot.username}`, /^ADAPT_QA SETUP /, 30000)
        setup = true
        await fixture('void-prepare')
        await context.command(`/effect clear ${actor.bot.username}`, /removed|no effects/i)
        await context.command(`/adaptqa learn ${id} 1`, /^ADAPT_QA LEARN/)
      }
      try {
        await context.step(`${id} remains spent after ${restart ? 'server restart' : 'reconnect'}`, async () => {
          await prepare()
          let deadline
          if (!restart) {
            const initial = await fixture('snapshot')
            context.expect(initial.deathSaveDeadlines[id] === 0, 'Initial player has never spent this save', initial)
            const before = await snapshot()
            await fixture('void-hit')
            await context.waitUntil(async () => (await snapshot()).stats[stat] > (before.stats[stat] ?? 0),
              { label: 'first lethal hit activates save', timeoutMs: 7000, intervalMs: 100 })
            deadline = (await fixture('snapshot')).deathSaveDeadlines[id]
            await context.command('/adaptqa cleanup', /^ADAPT_QA CLEANUP$/, 15000)
            setup = false
            actor = await actor.reconnectAfter(() => actor.bot.quit('Death-save persistence check'), { timeoutMs: 30000 })
            await prepare()
          }
          const persisted = await fixture('snapshot')
          context.expect(persisted.deathSaveDeadlines[id] > persisted.now, 'Cooldown is still active', persisted)
          if (deadline !== undefined) context.expect(persisted.deathSaveDeadlines[id] === deadline, 'Reconnect preserves the original deadline', persisted)
          await context.command(`/item replace entity ${actor.bot.username} weapon.offhand with minecraft:totem_of_undying`, /Replaced/)
          await context.waitUntil(() => actor.bot.inventory.slots.some(item => item?.name === 'totem_of_undying'),
            { label: 'totem reaches player', timeoutMs: 5000 })
          const before = await snapshot()
          await fixture('void-hit')
          await context.waitUntil(() => !actor.bot.inventory.slots.some(item => item?.name === 'totem_of_undying'),
            { label: 'vanilla totem handles lethal hit while Adapt save is spent', timeoutMs: 7000 })
          const after = await snapshot()
          const settled = await fixture('snapshot')
          context.expect((after.stats[stat] ?? 0) === (before.stats[stat] ?? 0), 'Reconnect or restart grants no second Adapt save', after)
          context.expect(settled.deathSaveDeadlines[id] === persisted.deathSaveDeadlines[id], 'Blocked activation leaves the original cooldown unchanged', settled)
          context.report.cooldownPersistence.push({ id, phase: restart ? 'restart' : 'reconnect', deadline: settled.deathSaveDeadlines[id], saves: after.stats[stat], health: after.health })
        })
      } finally {
        actor.bot.clearControlStates()
        if (setup && !context.signal.aborted) await context.command('/adaptqa cleanup', /^ADAPT_QA CLEANUP$/, 15000)
      }
    }
    context.report.interactionLog = await errorsSince(log)
    context.expect(context.report.interactionLog.errors.length === 0, 'No runtime errors during persistence checks', context.report.interactionLog)
  },
}
