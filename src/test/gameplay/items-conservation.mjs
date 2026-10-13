import { fixtureJson } from './fixture-json.mjs'

const total = (state, type) => [...state.inventory, ...state.drops].filter(item => item.type === type).reduce((sum, item) => sum + item.amount, 0)
const stat = (state, key) => state.stats[key] ?? 0

export default {
  name: 'adapt-items-conservation',
  description: 'Verify temporary container refunds, committed crafting rewards, grindstone consumption, and exact recipe ingredients.',
  async run(context) {
    const baseline = context.options.command === 'baseline'
    const actor = await context.connectActor('AQAItemRetention')
    const other = await context.connectActor('AQAItemObserver')
    const evidence = context.report.itemConservation = { baseline }
    const fixture = command => fixtureJson(context, `/adaptqa items-audit ${actor.bot.username} ${command}`, `ITEMS_AUDIT ${command.split(' ')[0]}`, 10000)
    const learn = adaptation => context.command(`/adaptqa learn ${adaptation} 5`, /^ADAPT_QA LEARN /, 10000)
    const configure = (adaptation, field, value) => fixture(`configure ${adaptation} ${field} ${value}`)
    const snapshot = () => fixture('snapshot')
    const click = async (slot, button = 0, mode = 0) => {
      await actor.bot.clickWindow(slot, button, mode)
      await actor.bot.waitForTicks(3)
    }
    const open = async type => {
      const position = actor.bot.entity.position.clone().set(2, 100, 2)
      await context.waitUntil(() => Boolean(actor.bot.blockAt(position)), { label: 'Inventory anchor loads', timeoutMs: 5000 })
      await actor.bot.activateBlock(actor.bot.blockAt(position))
      await context.waitUntil(() => actor.bot.currentWindow?.type === `minecraft:${type}`, { label: `${type} opens`, timeoutMs: 5000 })
      return actor.bot.currentWindow
    }
    const put = async (window, slot, name, amount = 1, predicate = () => true) => {
      const item = window.slots.slice(window.inventoryStart, window.inventoryEnd).find(item => item?.name === name && item.count >= amount && predicate(item))
      context.expect(Boolean(item), `${amount} ${name} available for slot ${slot}`)
      const source = item.slot
      await click(source)
      if (item.count === amount) await click(slot)
      else {
        for (let index = 0; index < amount; index++) await click(slot, 1)
        await click(source)
      }
    }
    const fill = async window => {
      await fixture('fill')
      await context.waitUntil(() => window.slots.slice(window.inventoryStart, window.inventoryEnd).every(item => item?.name === 'stone' && item.count === 64),
        { label: 'Every player inventory slot is full', timeoutMs: 5000 })
    }
    let setup = false
    try {
      await context.command(`/adaptqa setup ${actor.bot.username} ${other.bot.username}`, /^ADAPT_QA SETUP /, 30000)
      setup = true
      await context.step('Scaffold expiry retains named shulker and its three diamonds', async () => {
        await learn('architect-scaffolder')
        await configure('architect-scaffolder', 'minDurationSeconds', 3)
        await configure('architect-scaffolder', 'maxDurationSeconds', 3)
        const before = await fixture('prepare scaffolder')
        const original = before.inventory.find(item => item.type === 'PURPLE_SHULKER_BOX')
        context.expect(original?.name === 'Retention Box' && original.contents.some(item => item.type === 'DIAMOND' && item.amount === 3), 'Original shulker contains named metadata and three diamonds', before)
        actor.bot.setQuickBarSlot(0)
        actor.bot.setControlState('sneak', true)
        await actor.bot.waitForTicks(3)
        const anchor = actor.bot.entity.position.clone().set(2, 100, 2)
        const placed = anchor.offset(0, 0, -1)
        await actor.bot.placeBlock(actor.bot.blockAt(anchor), anchor.clone().set(0, 0, -1))
        await context.waitUntil(() => actor.bot.blockAt(placed)?.name === 'purple_shulker_box', { label: 'Real survival placement creates shulker', timeoutMs: 5000 })
        const placedContents = await context.command('/data get block 2 100 1 Items', /block data/, 5000)
        context.expect(/diamond/.test(placedContents), 'Placed shulker retains three diamonds', placedContents)
        await context.waitUntil(() => actor.bot.blockAt(placed)?.name === 'air', { label: 'Scaffold expires', timeoutMs: 7000 })
        await actor.bot.waitForTicks(5)
        actor.bot.setControlState('sneak', false)
        const after = await snapshot()
        const returned = [...after.inventory, ...after.drops].filter(item => item.type === 'PURPLE_SHULKER_BOX')
        context.expect(returned.length === 1 && returned[0].amount === 1, 'Exactly one shulker is refunded', after)
        if (baseline) {
          context.expect(returned[0].name === '' && !returned[0].contents?.length && total(after, 'DIAMOND') === 0, 'Baseline reproduces lost name and stored diamonds', after)
        } else {
          context.expect(returned[0].name === 'Retention Box' && returned[0].contents.some(item => item.type === 'DIAMOND' && item.amount === 3), 'Refund preserves its source name and contained diamonds', after)
          context.expect(total(after, 'DIAMOND') === 0, 'Contained diamonds are not refunded a second time as loose items', after)
        }
        evidence.scaffolder = { before, placedContents, after }
      })
      await context.step('Provisioner requires a committed craft before awarding food', async () => {
        await learn('crafting-provisioner')
        for (const [field, value] of [['bonusChanceBase', 1], ['bonusChanceFactor', 0], ['bonusChanceMax', 1]]) await configure('crafting-provisioner', field, value)
        await fixture('prepare provisioner')
        const window = await open('crafting')
        try {
          for (const slot of [1, 2, 3]) await put(window, slot, 'wheat')
          await context.waitUntil(() => window.slots[0]?.name === 'bread', { label: 'Bread recipe is available', timeoutMs: 5000 })
          await fill(window)
          const before = await snapshot()
          const matrix = window.slots.slice(1, 10).map(item => item && { name: item.name, count: item.count })
          for (let attempt = 0; attempt < 4; attempt++) await click(0, 0, 1)
          await actor.bot.waitForTicks(5)
          const blocked = await snapshot()
          context.expect(blocked.crafted === 0 && JSON.stringify(matrix) === JSON.stringify(window.slots.slice(1, 10).map(item => item && { name: item.name, count: item.count })), 'Blocked clicks consume no wheat and commit no bread', blocked)
          context.expect(baseline ? total(blocked, 'BREAD') > 0 : total(blocked, 'BREAD') === 0, baseline ? 'Baseline reproduces free bonus bread' : 'Blocked crafting awards no bread', blocked)
          await fixture('space')
          await click(0, 0, 1)
          await actor.bot.waitForTicks(5)
          const committed = await snapshot()
          context.expect(committed.crafted === 1 && total(committed, 'BREAD') - total(blocked, 'BREAD') === 4, 'One actual bread craft produces its one output plus three bonus portions', committed)
          context.expect(stat(committed, 'crafting.provisioner.bonus-portions') - stat(blocked, 'crafting.provisioner.bonus-portions') === 3, 'Successful craft records exactly three bonus portions', committed)
          evidence.provisioner = { before, blocked, committed }
        } finally { actor.bot.closeWindow(window) }
      })
      await context.step('Grindstone recovery requires consumption of its enchanted source', async () => {
        await learn('enchanting-grindstone-recovery')
        for (const [field, value] of [['recoverChanceBase', 1], ['recoverChanceFactor', 0], ['maxRecoverChance', 1]]) await configure('enchanting-grindstone-recovery', field, value)
        await fixture('prepare grindstone')
        const window = await open('grindstone')
        try {
          await put(window, 0, 'iron_pickaxe')
          await context.waitUntil(() => window.slots[2]?.name === 'iron_pickaxe', { label: 'Disenchanted output is available', timeoutMs: 5000 })
          await fill(window)
          const before = await snapshot()
          for (let attempt = 0; attempt < 3; attempt++) {
            await fixture('clear-cooldown')
            await click(2, 0, 1)
          }
          await actor.bot.waitForTicks(5)
          const blocked = await snapshot()
          context.expect(blocked.top.some(item => item.slot === 0 && item.type === 'IRON_PICKAXE') && window.slots[2]?.name === 'iron_pickaxe', 'Blocked clicks leave the enchanted source and result present', blocked)
          context.expect(baseline ? total(blocked, 'ENCHANTED_BOOK') > 0 && blocked.experience > before.experience : total(blocked, 'ENCHANTED_BOOK') === 0 && blocked.experience === before.experience,
            baseline ? 'Baseline reproduces unconsumed-source book and XP rewards' : 'Unconsumed source grants no books or XP', blocked)
          await fixture('clear-cooldown')
          await context.command(`/clear ${actor.bot.username}`, /Removed|No items/, 5000)
          await actor.bot.waitForTicks(3)
          await click(2, 0, 1)
          await actor.bot.waitForTicks(5)
          const committed = await snapshot()
          context.expect(!committed.top.some(item => item.slot <= 2) && total(committed, 'ENCHANTED_BOOK') - total(blocked, 'ENCHANTED_BOOK') === 1, 'Actual output extraction consumes its source and awards exactly one recovered book', committed)
          context.expect(committed.experience > blocked.experience, 'Committed grindstone extraction grants XP', committed)
          evidence.grindstone = { before, blocked, committed }
        } finally { actor.bot.closeWindow(window) }
      })
      for (const mode of ['bulk', 'bulk-valid']) {
        await context.step(`${mode} batch uses exact recipe ingredients`, async () => {
          await learn('crafting-bulk-artisan')
          const before = await fixture(`prepare ${mode}`)
          const window = await open('crafting')
          try {
            await put(window, 1, 'diamond', 1, item => item.components?.some(component => component.type === 'custom_name'))
            await context.waitUntil(() => window.slots[0]?.name === 'emerald', { label: 'Exact-choice recipe output appears', timeoutMs: 5000 })
            await click(0, 0, 1)
            await actor.bot.waitForTicks(8)
            const after = await snapshot()
            const plain = after.inventory.filter(item => item.type === 'DIAMOND' && item.name === '').reduce((sum, item) => sum + item.amount, 0)
            const marked = after.inventory.filter(item => item.type === 'DIAMOND' && item.name === 'Marked Diamond').reduce((sum, item) => sum + item.amount, 0)
            context.expect(total(after, 'EMERALD') === (baseline || mode === 'bulk-valid' ? 9 : 1), baseline ? 'Baseline reproduces eight bonus outputs from plain substitutes' : 'Output count reflects available exact ingredients', after)
            context.expect(baseline ? plain === 0 && marked === (mode === 'bulk-valid' ? 8 : 0) : plain === 8 && marked === 0, baseline ? 'Baseline consumes plain diamonds while retaining marked inventory inputs' : 'Batch preserves plain diamonds and consumes only marked ingredients', after)
            evidence[mode] = { before, after }
          } finally { actor.bot.closeWindow(window) }
        })
      }
    } finally {
      actor.bot.clearControlStates()
      other.bot.clearControlStates()
      if (actor.bot.currentWindow) actor.bot.closeWindow(actor.bot.currentWindow)
      if (setup && !context.signal.aborted) await context.command('/adaptqa cleanup', /^ADAPT_QA CLEANUP$/, 15000)
    }
  },
}
