import { randomBytes } from 'node:crypto'
import { fixtureJson } from './fixture-json.mjs'
import { decodeSoundPacket } from './feedback.mjs'

export default {
  name: 'adapt-catalog-preference-behavior',
  description: 'Exercise production option consumers, private click feedback, server locks and owned effect cleanup.',
  async run(context) {
    const suffix = randomBytes(4).toString('hex')
    const actor = await context.connectActor(`AQAcp${suffix}`)
    const other = await context.connectActor(`AQAco${suffix}`)
    const evidence = context.report.catalogBehavior = { sounds: [], offers: [], pickup: [], realClientSound: 'not verified' }
    const preference = (player, command) => fixtureJson(context,
      `/adaptqa preferences ${player.bot.username} ${command}`, `PREFERENCES ${command.split(' ')[0]}`)
    const behavior = (player, command) => fixtureJson(context,
      `/adaptqa catalog-behavior ${player.bot.username} ${command}`, `CATALOG_BEHAVIOR ${command.split(' ')[0]}`)
    const id = 'enchanting-bookshelf-attunement'
    let setup = false
    let locked = false
    async function click(slot, label, name = 'ui.button.click', pitch = 1.2) {
      const received = []
      const nearby = []
      const receive = packet => received.push(decodeSoundPacket(actor.bot.registry, packet))
      const overhear = packet => nearby.push(decodeSoundPacket(other.bot.registry, packet))
      const matches = sound => sound.name === name && Math.abs(sound.pitch - pitch) < 0.001
        && Math.abs(sound.volume - 0.3) < 0.001
      actor.bot._client.on('sound_effect', receive)
      other.bot._client.on('sound_effect', overhear)
      try {
        await actor.bot.clickWindow(slot, 0, 0)
        await context.waitUntil(() => received.some(matches), { label, timeoutMs: 4000 })
        await actor.bot.waitForTicks(4)
        context.expect(received.filter(matches).length === 1, `${label} sends one matching packet`, { received })
        context.expect(!nearby.some(matches), `${label} is private`, { nearby })
        evidence.sounds.push({ label, received, nearby })
      } finally {
        actor.bot._client.off('sound_effect', receive)
        other.bot._client.off('sound_effect', overhear)
      }
    }
    try {
      await context.command(`/adaptqa setup ${actor.bot.username} ${other.bot.username}`, /^ADAPT_QA SETUP /, 30000)
      setup = true
      await context.command('/adaptqa effects true', /^ADAPT_QA EFFECTS true$/, 5000)
      await preference(actor, `learn ${id}`)
      await preference(other, `learn ${id}`)
      let state = await preference(actor, `open ${id}`)
      await context.waitUntil(() => Boolean(actor.bot.currentWindow), { label: 'Bookshelf controls open', timeoutMs: 5000 })
      const window = actor.bot.currentWindow
      const power = state.settings.find(setting => setting.id === 'power')
      const slot = state.slots.find(item => item.name.replace(/§./g, '') === power.label)?.slot
      context.expect(Number.isInteger(slot), 'Bookshelf power control is present')
      const original = await behavior(actor, 'offers')
      await click(slot, 'Power mode change')
      state = await preference(actor, `snapshot ${id}`)
      context.expect(actor.bot.currentWindow === window, 'Mode changes preserve the inventory')
      context.expect(state.settings.find(setting => setting.id === 'power').value === 'HALF', 'Power cycles to Half')
      const reduced = await behavior(actor, 'offers')
      const isolated = await behavior(other, 'offers')
      context.expect(original.cost > reduced.cost && reduced.cost > 5, 'Power preference reduces actual earned offer contribution', { original, reduced })
      context.expect(isolated.cost === original.cost && isolated.level === original.level, 'Another player keeps full offer power')
      evidence.offers.push({ original, reduced, isolated })
      await behavior(actor, 'power-lock true')
      locked = true
      state = await preference(actor, `open ${id}`)
      await actor.bot.waitForTicks(3)
      await click(slot, 'Server-locked power click', 'block.note_block.bass', 0.7)
      state = await preference(actor, `snapshot ${id}`)
      context.expect(state.settings.find(setting => setting.id === 'power').value === 'QUARTER', 'Server lock forces the server default')
      const forced = await behavior(actor, 'offers')
      const forcedOther = await behavior(other, 'offers')
      context.expect(forced.cost < reduced.cost && forced.cost === forcedOther.cost, 'Server lock changes actual offers for both players')
      evidence.offers.push({ forced, forcedOther })
      await behavior(actor, 'power-lock false')
      locked = false
      state = await preference(actor, `open ${id}`)
      context.expect(state.settings.find(setting => setting.id === 'power').value === 'HALF', 'Unlock restores the saved personal choice')
      await actor.bot.waitForTicks(3)
      const reset = state.slots.find(item => item.material === 'MILK_BUCKET')?.slot
      context.expect(Number.isInteger(reset), 'Reset control exists')
      await click(reset, 'Reset click', 'ui.button.click', 0.9)
      const restored = await behavior(actor, 'offers')
      context.expect(restored.cost === original.cost, 'Reset restores the actual default contribution')
      await preference(actor, `set ${id} enabled OFF`)
      const disabled = await behavior(actor, 'offers')
      context.expect(disabled.cost === 5 && disabled.level === 1, 'Disabled adaptation leaves offers unchanged')
      evidence.offers.push({ restored, disabled })
      const seedsOnly = await behavior(actor, 'pickup true')
      const allDrops = await behavior(other, 'pickup false')
      context.expect(seedsOnly.seeds === 2 && seedsOnly.wheat === 0 && seedsOnly.ordinaryDrops.includes('WHEAT'),
        'Seed-only pickup leaves wheat as ordinary drops', seedsOnly)
      context.expect(allDrops.seeds === 2 && allDrops.wheat === 3 && allDrops.ordinaryDrops.length === 0,
        'Other player still picks up both crops and seeds', allDrops)
      evidence.pickup.push({ seedsOnly, allDrops })
      const polymath = await behavior(actor, 'polymath')
      context.expect(polymath.active === 0 && polymath.learned > 0 && !polymath.sources.includes('discovery-polymath')
        && polymath.sources.includes('fixture-other') && polymath.sources.includes('unowned'),
        'Disabling Polymath removes only its owned XP grants and preserves progression', polymath)
      evidence.polymath = polymath
      await preference(actor, 'learn chronos-overtime')
      state = await preference(actor, 'open chronos-overtime')
      await context.waitUntil(() => Boolean(actor.bot.currentWindow), { label: 'Paged Overtime controls open', timeoutMs: 5000 })
      await actor.bot.waitForTicks(3)
      const pagedWindow = actor.bot.currentWindow
      const rowStart = pagedWindow.inventoryStart - 9
      const firstPage = state.slots.filter(item => item.slot > rowStart && item.slot < rowStart + 7)
      await click(rowStart, 'Previous at first settings page', 'block.note_block.bass', 0.7)
      context.expect(actor.bot.currentWindow === pagedWindow, 'Blocked page navigation preserves the menu')
      await click(rowStart + 7, 'Next settings page', 'item.book.page_turn', 1.1)
      state = await preference(actor, 'snapshot chronos-overtime')
      const secondPage = state.slots.filter(item => item.slot > rowStart && item.slot < rowStart + 7)
      context.expect(actor.bot.currentWindow === pagedWindow && JSON.stringify(firstPage) !== JSON.stringify(secondPage),
        'Paged controls change in the existing inventory')
      evidence.paging = { sameWindow: true, firstPage, secondPage }
    } finally {
      if (locked && !context.signal.aborted) await behavior(actor, 'power-lock false')
      for (const player of [actor, other]) {
        player.bot.clearControlStates()
        if (player.bot.currentWindow) player.bot.closeWindow(player.bot.currentWindow)
      }
      if (setup && !context.signal.aborted) await context.command('/adaptqa cleanup', /^ADAPT_QA CLEANUP$/, 15000)
    }
  },
}
