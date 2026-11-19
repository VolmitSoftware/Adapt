import { readFile, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { fixtureJson } from './fixture-json.mjs'
import { errorsSince, logPosition } from './runtime-log.mjs'
import { disposableInstance } from './disposable-instance.mjs'
import { adaptConfigDirectory } from './adapt-config.mjs'

// The restart step stops the server and queues its boot behind the shared Multiplexor and boot locks
// (each waits up to 10 minutes), so the saves spent before the restart must outlast that window.
const SAVE_COOLDOWN_MS = 7200000
const RESTART_WINDOW_MS = 3600000
// Entity event sent when a totem of undying saves the entity. A vanilla client removes the totem from its hand on this
// event; the bot does not, and on Paper 1.20.1 it receives no slot update for the spent totem.
const TOTEM_USED = 35

async function lengthenSaveCooldowns(context, cases) {
  const directory = context.report.server.directory
  const log = join(directory, 'logs', 'latest.log')
  const offset = (await readFile(log, 'utf8')).length
  const files = []
  for (const [id, , key] of cases) {
    const file = join(adaptConfigDirectory(directory), 'adaptations', `${id}.toml`)
    const original = await readFile(file, 'utf8')
    const pattern = new RegExp(`^${key} = [0-9.]+$`, 'm')
    context.expect(pattern.test(original), `${id} config has its base cooldown ${key}`)
    await writeFile(file, original.replace(pattern, `${key} = ${SAVE_COOLDOWN_MS}`))
    files.push(`/adaptations/${id}.toml`)
  }
  await context.waitUntil(async () => {
    const lines = (await readFile(log, 'utf8')).slice(offset).split('\n').filter(line => line.includes('Hotloaded '))
    return files.every(file => lines.some(line => line.replace(/\u001b\[[0-9;]*m/g, '').trimEnd().endsWith(file)))
  }, { label: 'death-save cooldown configs are hotloaded', timeoutMs: 15000, intervalMs: 100 })
}

export default {
  name: 'adapt-death-save-persistence',
  description: 'Assert death-save cooldowns survive reconnects and a second run after a server restart.',
  async run(context) {
    context.expect(disposableInstance(context.report.server.instance, 'adapt-interactions-'), 'Disposable interaction instance required')
    const log = await logPosition(join(context.report.server.directory, 'logs', 'latest.log'))
    const restart = context.options.command === 'verify-restart'
    const opponent = await context.connectActor('AQADSCopponent')
    const cases = [
      ['tragoul-last-rites', 'tragoul.last-rites.deaths-defied', 'cooldownMillisBase'],
      ['chronos-hourglass-guard', 'chronos.hourglass-guard.saves', 'baseCooldownMillis'],
      ['rift-void-skin', 'rift.void-skin.escapes', 'cooldownBaseMillis'],
    ]
    context.report.cooldownPersistence = []
    if (!restart) await lengthenSaveCooldowns(context, cases)
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
            await context.waitUntil(async () => (await fixture('void-hit')).delivered,
            { label: 'lethal hit reaches the player after the arena world change', timeoutMs: 6000, intervalMs: 100 })
            await context.waitUntil(async () => (await snapshot()).stats[stat] > (before.stats[stat] ?? 0),
              { label: 'first lethal hit activates save', timeoutMs: 7000, intervalMs: 100 })
            const spent = await fixture('snapshot')
            deadline = spent.deathSaveDeadlines[id]
            context.expect(deadline - spent.now > RESTART_WINDOW_MS, 'Spent save outlasts the restart window', spent)
            await context.command('/adaptqa cleanup', /^ADAPT_QA CLEANUP$/, 15000)
            setup = false
            actor = await actor.reconnectAfter(() => actor.bot.quit('Death-save persistence check'), { timeoutMs: 30000 })
            await prepare()
          }
          const persisted = await fixture('snapshot')
          context.expect(persisted.deathSaveDeadlines[id] > persisted.now, 'Cooldown is still active', persisted)
          if (deadline !== undefined) context.expect(persisted.deathSaveDeadlines[id] === deadline, 'Reconnect preserves the original deadline', persisted)
          await fixture('totem')
          await context.waitUntil(() => actor.bot.inventory.slots.some(item => item?.name === 'totem_of_undying'),
            { label: 'totem reaches player', timeoutMs: 5000 })
          const before = await snapshot()
          let totemPopped = false
          const onStatus = packet => { if (packet.entityId === actor.bot.entity.id && packet.entityStatus === TOTEM_USED) totemPopped = true }
          actor.bot._client.on('entity_status', onStatus)
          try {
            await context.waitUntil(async () => (await fixture('void-hit')).delivered,
              { label: 'lethal hit reaches the player after the arena world change', timeoutMs: 6000, intervalMs: 100 })
            await context.waitUntil(async () => totemPopped && !(await fixture('snapshot')).offHandTotem,
              { label: 'vanilla totem handles lethal hit while Adapt save is spent', timeoutMs: 7000 })
          } finally {
            actor.bot._client.off('entity_status', onStatus)
          }
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
