import { randomBytes } from 'node:crypto'
import { readFile, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { fixtureJson } from './fixture-json.mjs'

export default {
  name: 'adapt-preference-policy-reload',
  description: 'Verify actual watched TOML policy changes override and restore a connected player’s saved choice.',
  async run(context) {
    const directory = context.report.server.directory
    context.expect(context.report.server.instance.startsWith('adapt-catalog-'), 'Policy fixture targets its disposable server')
    const config = join(directory, 'plugins', 'Adapt', 'adaptations', 'enchanting-bookshelf-attunement.toml')
    const log = join(directory, 'logs', 'latest.log')
    const original = await readFile(config, 'utf8')
    const initialLog = await readFile(log, 'utf8')
    const locked = original.replace(/(\[playerPreferences\.power\]\r?\n)([\s\S]*?)(?=\n\[|$)/,
      (_, header, body) => header + body.replace(/^playerEditable = true$/m, 'playerEditable = false')
        .replace(/^defaultValue = "FULL"$/m, 'defaultValue = "QUARTER"'))
    context.expect(locked !== original, 'Generated power policy has expected editable Full default')
    const suffix = randomBytes(4).toString('hex')
    const actor = await context.connectActor(`AQAwp${suffix}`)
    const other = await context.connectActor(`AQAwo${suffix}`)
    const preference = command => fixtureJson(context,
      `/adaptqa preferences ${actor.bot.username} ${command}`, `PREFERENCES ${command.split(' ')[0]}`)
    const offers = () => fixtureJson(context, `/adaptqa catalog-behavior ${actor.bot.username} offers`, 'CATALOG_BEHAVIOR offers')
    const id = 'enchanting-bookshelf-attunement'
    const evidence = context.report.policyReload = {}
    let setup = false
    let changed = false
    async function resolved(expected) {
      let state
      await context.waitUntil(async () => {
        state = await preference(`snapshot ${id}`)
        return state.settings.find(setting => setting.id === 'power')?.value === expected
      }, { label: `Watched policy resolves ${expected}`, timeoutMs: 10000, intervalMs: 100 })
      return state
    }
    try {
      await context.command(`/adaptqa setup ${actor.bot.username} ${other.bot.username}`, /^ADAPT_QA SETUP /, 30000)
      setup = true
      await preference(`learn ${id}`)
      await preference(`set ${id} power HALF`)
      evidence.before = await offers()
      context.expect(evidence.before.cost === 8, 'Saved Half choice initially contributes three offer levels')
      await writeFile(config, locked)
      changed = true
      evidence.locked = await resolved('QUARTER')
      evidence.lockedOffers = await offers()
      context.expect(evidence.lockedOffers.cost === 6, 'Disk policy lock changes actual offer contribution')
      await writeFile(config, original)
      changed = false
      evidence.restored = await resolved('HALF')
      evidence.restoredOffers = await offers()
      context.expect(evidence.restoredOffers.cost === 8, 'Restoring disk policy restores saved Half contribution')
      const lines = (await readFile(log, 'utf8')).slice(initialLog.length).split('\n')
      evidence.hotloads = lines.filter(line => line.includes('Hotloaded') && line.includes('enchanting-bookshelf-attunement.toml'))
      evidence.errors = lines.filter(line => /\/(?:ERROR|SEVERE)\]/.test(line))
      context.expect(evidence.hotloads.length >= 2, 'Normal watcher logs both policy reloads', evidence.hotloads)
      context.expect(evidence.errors.length === 0, 'Policy reload emits no runtime errors', evidence.errors)
    } finally {
      if (changed) await writeFile(config, original)
      for (const player of [actor, other]) {
        player.bot.clearControlStates()
        if (player.bot.currentWindow) player.bot.closeWindow(player.bot.currentWindow)
      }
      if (setup && !context.signal.aborted) await context.command('/adaptqa cleanup', /^ADAPT_QA CLEANUP$/, 15000)
    }
  },
}
