import { randomBytes } from 'node:crypto'
import { fixtureJson } from './fixture-json.mjs'

export default {
  name: 'adapt-appearance',
  description: 'Verify translated menus, configured orb materials, and exactly-once orb consumption.',
  async run(context) {
    const suffix = randomBytes(3).toString('hex')
    const actor = await context.connectActor(`AQAstyle${suffix}`)
    const other = await context.connectActor(`AQApeer${suffix}`)
    const appearance = command => fixtureJson(context,
      `/adaptqa appearance ${actor.bot.username} ${command}`, `APPEARANCE ${command.split(' ')[0]}`, 10000)
    const preferences = command => fixtureJson(context,
      `/adaptqa preferences ${actor.bot.username} ${command}`, `PREFERENCES ${command.split(' ')[0]}`, 10000)
    const plain = text => text.replace(/§[0-9a-fk-orx]/gi, '')
    let setup = false
    context.report.appearance = { orbs: [] }
    try {
      await context.command(`/adaptqa setup ${actor.bot.username} ${other.bot.username}`, /^ADAPT_QA SETUP /, 30000)
      setup = true
      await context.step('Translated configuration and personal settings', async () => {
        let menu = await appearance('configure')
        context.expect(plain(menu.title) === 'Style settings', 'Configuration title uses language override', menu)
        actor.bot.closeWindow(actor.bot.currentWindow)
        menu = await appearance('configure core.$general')
        context.expect(menu.slots.some(item => item.name && plain(item.name).includes('Server locale')),
          'Config field label uses language override', menu)
        actor.bot.closeWindow(actor.bot.currentWindow)
        await preferences('learn agility-air-dash')
        await preferences('open agility-air-dash')
        menu = await appearance('snapshot')
        const reset = menu.slots.find(item => item.name && plain(item.name) === 'Restore choices')
        context.expect(reset?.material === 'COMPASS', 'Personal reset uses language and material overrides', menu)
        context.expect(Buffer.from(reset.encodedName, 'base64').toString().includes('§a'), 'Explicit green reset text is retained', reset)
        actor.bot.closeWindow(actor.bot.currentWindow)
      })
      for (const material of ['SNOWBALL', 'EXPERIENCE_BOTTLE', 'PLAYER_HEAD']) {
        await appearance(`models ${material}`)
        for (const kind of ['experience', 'knowledge']) {
          await context.step(`${material} ${kind} orb`, async () => {
            const before = await appearance(`give ${kind} main SURVIVAL`)
            context.expect(before.main.material === material, 'Configured orb material is applied', before)
            context.expect(before.main.model === (kind === 'experience' ? 71 : 72), 'Custom model is applied', before)
            const lore = Buffer.from(before.main.encodedLore.at(-1), 'base64').toString()
            context.expect(lore.includes('§a') && !lore.includes('§d'), 'Explicit orb lore color overrides default purple', { lore })
            await actor.bot.look(0, 0, true)
            actor.bot.activateItem()
            let after
            await context.waitUntil(async () => {
              after = await appearance('snapshot')
              return after.main.amount === 1
            }, { label: 'One orb is consumed', timeoutMs: 5000 })
            await actor.bot.waitForTicks(4)
            after = await appearance('snapshot')
            const metric = kind === 'experience' ? 'xp' : 'knowledge'
            const awarded = kind === 'experience' ? 7 * before.xpMultiplier : 7
            context.expect(Math.abs(after[metric] - before[metric] - awarded) < 0.000001,
              'One orb awards its value with the current XP multiplier', { before, after, awarded })
            context.expect(after.main.amount === 1, 'No second consumption occurs', after)
            context.expect(after.vanillaXp === before.vanillaXp, 'No vanilla XP is produced', after)
            context.report.appearance.orbs.push({ material, kind, awarded, consumed: 1 })
          })
        }
      }
      await context.step('Existing and offhand orbs remain usable after model changes', async () => {
        await appearance('models PLAYER_HEAD')
        const before = await appearance('give knowledge offhand CREATIVE')
        await appearance('models EXPERIENCE_BOTTLE')
        actor.bot.activateItem(true)
        let after
        await context.waitUntil(async () => {
          after = await appearance('snapshot')
          return after.offhand.amount === 1
        }, { label: 'Existing offhand orb consumed', timeoutMs: 5000 })
        context.expect(after.knowledge - before.knowledge === 7, 'Creative offhand orb awards once', { before, after })
        context.expect(after.offhand.material === 'PLAYER_HEAD', 'Existing orb keeps its appearance', after)
      })
    } finally {
      for (const player of [actor, other]) {
        player.bot.clearControlStates()
        if (player.bot.currentWindow) player.bot.closeWindow(player.bot.currentWindow)
      }
      if (setup && !context.signal.aborted) await context.command('/adaptqa cleanup', /^ADAPT_QA CLEANUP$/, 15000)
    }
  },
}
