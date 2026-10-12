import { randomBytes } from 'node:crypto'
import { readFile, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { fixtureJson } from './fixture-json.mjs'
import { serverErrors } from './runtime-log.mjs'

export default {
  name: 'adapt-player-settings-lock',
  description: 'Verify the watched global settings lock, saved choices, stale menus, mutation gates, live reconciliation, and unlearning.',
  async run(context) {
    context.expect(context.report.server.instance.startsWith('adapt-settings-lock-'), 'Settings lock fixture targets its disposable server')
    const directory = context.report.server.directory
    const config = join(directory, 'plugins', 'Adapt', 'adapt.toml')
    const log = join(directory, 'logs', 'latest.log')
    const original = await readFile(config, 'utf8')
    const initialLog = await readFile(log, 'utf8')
    context.expect(/^playerSettingsEnabled = true$/m.test(original), 'Player settings default to enabled')
    const locked = original.replace(/^playerSettingsEnabled = true$/m, 'playerSettingsEnabled = false')
    const suffix = randomBytes(4).toString('hex')
    const actor = await context.connectActor(`AQAl${suffix}`)
    const other = await context.connectActor(`AQAs${suffix}`)
    const action = (player, command) => fixtureJson(context,
      `/adaptqa preferences ${player.bot.username} ${command}`, `PREFERENCES ${command.split(' ')[0]}`, 10000)
    const snapshot = () => fixtureJson(context, `/adaptqa snapshot ${actor.bot.username} lock`, 'SNAPSHOT lock')
    const evidence = context.report.settingsLock = { reloads: [] }
    const id = 'agility-air-dash'
    const plain = value => value.replace(/§./g, '')
    const setting = (state, key) => state.settings.find(entry => entry.id === key)
    let adaptationWindow
    let skillWindow
    let setup = false
    let changed = false
    async function watch(enabled) {
      let state
      await context.waitUntil(async () => {
        state = await action(actor, `snapshot ${id}`)
        return state.playerSettingsEnabled === enabled
      }, { label: `Root TOML watcher applies playerSettingsEnabled=${enabled}`, timeoutMs: 10000, intervalMs: 100 })
      evidence.reloads.push({ playerSettingsEnabled: state.playerSettingsEnabled, learned: state.learned, active: state.active })
      return state
    }
    async function open(player, command) {
      const state = await action(player, command)
      await context.waitUntil(() => Boolean(player.bot.currentWindow), { label: `${command} inventory opens`, timeoutMs: 5000 })
      return state
    }
    try {
      await context.step('Save independent choices and retain pre-lock inventories', async () => {
        await context.command(`/adaptqa setup ${actor.bot.username} ${other.bot.username}`, /^ADAPT_QA SETUP /, 30000)
        setup = true
        const catalog = await action(actor, 'catalog')
        evidence.registeredAdaptations = catalog.adaptations.length
        evidence.registeredControls = catalog.adaptations.reduce((total, adaptation) => total + adaptation.controls, 0)
        await action(actor, `learn ${id}`)
        await action(actor, `set ${id} enabled OFF`)
        await action(actor, 'learn stealth-speed')
        await action(actor, 'set stealth-speed speed HALF')
        await action(other, 'learn agility-air-dash')
        const skill = await action(other, 'skill agility set OFF')
        context.expect(skill.accepted && skill.enabled === 'OFF', 'Skill master toggle saves while unlocked')
        evidence.before = await open(actor, `open ${id}`)
        adaptationWindow = actor.bot.currentWindow
        evidence.skillBefore = await open(other, 'skill agility open')
        skillWindow = other.bot.currentWindow
        evidence.adaptationSlot = evidence.before.slots.find(entry => plain(entry.name) === setting(evidence.before, 'enabled').label)?.slot
        evidence.skillSlot = evidence.skillBefore.slots.find(entry => plain(entry.name) === 'Skill adaptations enabled')?.slot
        evidence.adaptationResetSlot = evidence.before.slots.find(entry => entry.material === 'MILK_BUCKET')?.slot
        evidence.skillResetSlot = evidence.skillBefore.slots.find(entry => entry.material === 'MILK_BUCKET')?.slot
        for (const slot of [evidence.adaptationSlot, evidence.skillSlot, evidence.adaptationResetSlot, evidence.skillResetSlot]) {
          context.expect(Number.isInteger(slot), 'Unlocked inventory exposes its settings controls')
        }
        actor.bot.setControlState('sneak', true)
        await context.waitUntil(async () => {
          const state = await snapshot()
          evidence.halfSneakingSpeed = state.attributes['minecraft:sneaking_speed']
          return evidence.halfSneakingSpeed > 0.3
        }, { label: 'Saved Half stealth speed applies its live attribute', timeoutMs: 5000 })
      })
      await context.step('Hotload lock and reconcile connected players', async () => {
        await writeFile(config, locked)
        changed = true
        evidence.locked = await watch(false)
        context.expect(setting(evidence.locked, 'enabled').value === 'ON' && evidence.locked.active === evidence.locked.learned,
          'Global lock ignores saved adaptation Off and preserves learned level', evidence.locked)
        const skill = await action(other, 'skill agility')
        context.expect(skill.enabled === 'ON' && skill.stored === 'OFF', 'Global lock ignores saved skill Off without deleting it', skill)
        const stealth = await action(actor, 'snapshot stealth-speed')
        context.expect(setting(stealth, 'speed').value === 'FULL' && setting(stealth, 'speed').stored === 'HALF',
          'Global lock resolves server strength default without deleting saved Half')
        await context.waitUntil(async () => {
          evidence.reconciled = await snapshot()
          return evidence.reconciled.attributes['minecraft:sneaking_speed'] <= 0.300001
        }, { label: 'Config watcher removes the previous live stealth bonus', timeoutMs: 5000 })
        actor.bot.setControlState('sneak', false)
        await actor.bot.waitForTicks(2)
        actor.bot.setControlState('sneak', true)
        await context.waitUntil(async () => {
          evidence.fullSneakingSpeed = (await snapshot()).attributes['minecraft:sneaking_speed']
          return evidence.fullSneakingSpeed > evidence.halfSneakingSpeed + 0.1
        }, { label: 'New stealth activation uses the server Full default', timeoutMs: 5000 })
        actor.bot.setControlState('sneak', false)
      })
      await context.step('Reject stale inventory actions and every preference API mutation', async () => {
        evidence.staleMenus = []
        for (const menu of [
          { player: actor, original: adaptationWindow, before: evidence.before, command: `snapshot ${id}`, slots: [evidence.adaptationSlot, evidence.adaptationResetSlot] },
          { player: other, original: skillWindow, before: evidence.skillBefore, command: 'skill agility', slots: [evidence.skillSlot, evidence.skillResetSlot] },
        ]) {
          for (const slot of menu.slots) {
            const current = await action(menu.player, menu.command)
            const control = menu.before.slots.find(entry => entry.slot === slot)
            const present = current.slots.find(entry => entry.slot === slot)
            const stale = menu.player.bot.currentWindow === menu.original && present?.name === control.name
            if (stale) {
              await menu.player.bot.clickWindow(slot, 0, 0)
              await menu.player.bot.waitForTicks(2)
            } else {
              context.expect(!current.slots.some(entry => plain(entry.name) === plain(control.name)),
                'Watcher-refreshed inventory removes its previous settings control', { control, current })
            }
            evidence.staleMenus.push({ player: menu.player.bot.username, slot, clicked: stale, refreshed: !stale })
          }
        }
        evidence.staleAdaptation = await action(actor, `snapshot ${id}`)
        evidence.staleSkill = await action(other, 'skill agility')
        context.expect(setting(evidence.staleAdaptation, 'enabled').stored === 'OFF'
          && evidence.staleSkill.stored === 'OFF', 'Stale controls cannot replace or reset either saved Off')
        evidence.rejectedSet = await action(actor, `try-set ${id} enabled ON`)
        evidence.rejectedReset = await action(actor, `reset ${id}`)
        evidence.rejectedSkillSet = await action(other, 'skill agility set ON')
        evidence.rejectedSkillReset = await action(other, 'skill agility reset')
        for (const result of [evidence.rejectedSet, evidence.rejectedReset, evidence.rejectedSkillSet, evidence.rejectedSkillReset]) {
          context.expect(result.accepted === false, 'Locked preference mutation reports rejection')
        }
        if (actor.bot.currentWindow) actor.bot.closeWindow(actor.bot.currentWindow)
        if (other.bot.currentWindow) other.bot.closeWindow(other.bot.currentWindow)
        evidence.catalog = await action(actor, 'lock-audit')
        context.expect(evidence.catalog.passed && evidence.catalog.adaptations === evidence.registeredAdaptations
          && evidence.catalog.controls === evidence.registeredControls && evidence.catalog.skills > 1,
          'Every registered adaptation and skill uses server defaults and rejects writes/resets', evidence.catalog)
      })
      await context.step('Hide newly opened settings while permitting unlearning', async () => {
        evidence.menu = await open(actor, `open ${id}`)
        context.expect(evidence.menu.settings.every(entry => !entry.visible), 'Adaptation settings report hidden')
        context.expect(!evidence.menu.slots.some(entry => entry.material === 'MILK_BUCKET'
          || evidence.menu.settings.some(control => plain(entry.name) === control.label)), 'New adaptation menu has no settings or reset controls')
        const tile = evidence.menu.slots.find(entry => / I$/.test(plain(entry.name)))?.slot
        context.expect(Number.isInteger(tile), 'Learned first level remains present in locked menu')
        await actor.bot.clickWindow(tile, 0, 0)
        await context.waitUntil(async () => {
          evidence.unlearned = await action(actor, `snapshot ${id}`)
          return evidence.unlearned.learned === 0 && evidence.unlearned.active === 0
        }, { label: 'Normal menu click unlearns locked adaptation', timeoutMs: 5000 })
        evidence.skillMenu = await open(other, 'skill agility open')
        context.expect(!evidence.skillMenu.slots.some(entry => entry.material === 'MILK_BUCKET'
          || plain(entry.name) === 'Skill adaptations enabled'), 'New skill menu has no settings or reset controls')
      })
      await context.step('Hotload unlock and restore saved choices', async () => {
        await writeFile(config, original)
        changed = false
        await watch(true)
        await action(actor, `learn ${id}`)
        evidence.restored = await action(actor, `snapshot ${id}`)
        evidence.skillRestored = await action(other, 'skill agility')
        evidence.strengthRestored = await action(actor, 'snapshot stealth-speed')
        context.expect(setting(evidence.restored, 'enabled').value === 'OFF' && evidence.restored.active === 0,
          'Unlock restores the original adaptation Off choice')
        context.expect(evidence.skillRestored.enabled === 'OFF', 'Unlock restores the original skill Off choice')
        context.expect(setting(evidence.strengthRestored, 'speed').value === 'HALF', 'Unlock restores the original Half strength choice')
        if (actor.bot.currentWindow) actor.bot.closeWindow(actor.bot.currentWindow)
        evidence.unlockedMenu = await open(actor, `open ${id}`)
        context.expect(evidence.unlockedMenu.slots.some(entry => plain(entry.name) === setting(evidence.unlockedMenu, 'enabled').label),
          'New unlocked menu exposes settings again')
        const lines = (await readFile(log, 'utf8')).slice(initialLog.length)
        evidence.errors = serverErrors(lines)
        context.expect(evidence.reloads.length === 2 && !evidence.reloads[0].playerSettingsEnabled && evidence.reloads[1].playerSettingsEnabled,
          'Normal watcher applies both root config reloads', evidence.reloads)
        context.expect(evidence.errors.length === 0, 'Settings lock emits no server runtime errors', evidence.errors)
      })
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
