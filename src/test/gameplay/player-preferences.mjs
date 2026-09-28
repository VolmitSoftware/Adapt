import { randomBytes } from 'node:crypto'
import { fixtureJson } from './fixture-json.mjs'

export default {
  name: 'adapt-player-preferences',
  description: 'Verify registered controls, all adaptation and skill enable gates, isolated player choices, and inventory updates.',
  async run(context) {
    const suffix = randomBytes(4).toString('hex')
    const actor = await context.connectActor(`AQAp${suffix}`)
    const other = await context.connectActor(`AQAo${suffix}`)
    const action = (player, command) => fixtureJson(context,
      `/adaptqa preferences ${player.bot.username} ${command}`, `PREFERENCES ${command.split(' ')[0]}`, 10000)
    const report = context.report.preferences = { adaptations: [], menus: [] }
    let setup = false
    try {
      await context.command(`/adaptqa setup ${actor.bot.username} ${other.bot.username}`, /^ADAPT_QA SETUP /, 30000)
      setup = true
      const catalog = await action(actor, 'catalog')
      context.expect(catalog.adaptations.length === 312, 'All 312 registered adaptations are present')
      for (const adaptation of catalog.adaptations) {
        context.expect(adaptation.controls > 0, `${adaptation.id} has personal controls`)
        const audit = await action(actor, `audit ${adaptation.id}`)
        context.expect(audit.passed && audit.controls === adaptation.controls && audit.choices >= 2,
          `${adaptation.id} preserves progression, honors both enable scopes and resolves every unlocked choice`, audit)
        report.adaptations.push(audit)
      }
      for (const id of ['agility-air-dash', 'pickaxe-veinminer', 'crafting-masterwork', 'ranged-trajectory-sight']) {
        await action(actor, `learn ${id}`)
        await action(other, `learn ${id}`)
        let state = await action(actor, `open ${id}`)
        await context.waitUntil(() => Boolean(actor.bot.currentWindow), { label: `${id} menu opens`, timeoutMs: 5000 })
        const window = actor.bot.currentWindow
        const enabled = state.settings.find(setting => setting.id === 'enabled')
        const slot = state.slots.find(item => item.name.replace(/§./g, '') === enabled.label)?.slot
        context.expect(Number.isInteger(slot), `${id} Enabled control is visible`)
        await actor.bot.clickWindow(slot, 0, 0)
        await context.waitUntil(async () => {
          state = await action(actor, `snapshot ${id}`)
          return state.settings.find(setting => setting.id === 'enabled')?.value === 'OFF'
        }, { label: `${id} switch applies`, timeoutMs: 5000 })
        context.expect(actor.bot.currentWindow === window, `${id} keeps the same inventory`)
        context.expect(state.slots.find(item => item.slot === slot)?.material === 'RED_STAINED_GLASS_PANE', `${id} slot turns red`)
        const untouched = await action(other, `snapshot ${id}`)
        context.expect(untouched.settings.find(setting => setting.id === 'enabled')?.value === 'ON', `${id} leaves another player's choice unchanged`)
        await actor.bot.clickWindow(slot, 0, 0)
        await actor.bot.waitForTicks(2)
        state = await action(actor, `snapshot ${id}`)
        context.expect(state.settings.find(setting => setting.id === 'enabled')?.value === 'ON', `${id} switches back on`)
        context.expect(actor.bot.currentWindow === window, `${id} reverse switch keeps the inventory`)
        report.menus.push({ id, slot, sameWindow: true, isolated: true })
        actor.bot.closeWindow(window)
      }
      let skill = await action(actor, 'skill agility open')
      await context.waitUntil(() => Boolean(actor.bot.currentWindow), { label: 'Skill menu opens', timeoutMs: 5000 })
      const skillWindow = actor.bot.currentWindow
      const switchSlot = skill.slots.find(item => item.name.replace(/§./g, '') === 'Skill adaptations enabled')?.slot
      context.expect(Number.isInteger(switchSlot), 'Skill master control is visible')
      await actor.bot.clickWindow(switchSlot, 0, 0)
      await actor.bot.waitForTicks(2)
      skill = await action(actor, 'skill agility')
      context.expect(skill.enabled === 'OFF' && actor.bot.currentWindow === skillWindow, 'Skill toggles in the same window')
      const disabled = await action(actor, 'snapshot agility-air-dash')
      context.expect(disabled.active === 0 && disabled.settings.find(setting => setting.id === 'enabled')?.value === 'ON',
        'Skill off suppresses active behavior while preserving child On')
      const reset = skill.slots.find(item => item.material === 'MILK_BUCKET')?.slot
      context.expect(Number.isInteger(reset), 'Skill reset is present')
      await actor.bot.clickWindow(reset, 0, 0)
      await actor.bot.waitForTicks(2)
      skill = await action(actor, 'skill agility')
      context.expect(skill.enabled === 'ON' && actor.bot.currentWindow === skillWindow, 'Skill reset restores its default in place')
      report.controls = report.adaptations.reduce((sum, entry) => sum + entry.controls, 0)
      report.choices = report.adaptations.reduce((sum, entry) => sum + entry.choices, 0)
    } finally {
      for (const player of [actor, other]) {
        player.bot.clearControlStates()
        if (player.bot.currentWindow) player.bot.closeWindow(player.bot.currentWindow)
      }
      if (setup && !context.signal.aborted) await context.command('/adaptqa cleanup', /^ADAPT_QA CLEANUP$/, 15000)
    }
  },
}
