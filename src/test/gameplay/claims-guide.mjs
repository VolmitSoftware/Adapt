const ADAPTATIONS = [
  ['founding', ['Learn Founding', 'All nine chunks', 'Allowed worlds', '-1000', '1000', 'Flight', 'Buildings remain', 'confirm=true']],
  ['fortification', ['survival block breaks', 'no drops or XP', 'Up to 4', '300 seconds', 'share progress', 'restarts', 'Creative mode']],
  ['citizenship', ['/adapt claims accept', '120 seconds', 'only one city', 'PvP', 'Walls block', 'preserves personal', 'safe ground']],
  ['expansion', ['Only the founder', 'Touching a corner', '25 total', 'initial nine', 'rank-one resistance', '/adapt claims info']],
  ['homestead', ['Learn Homestead', 'main anchor chunk', 'Everyone else', 'preserves your plot', 'Buildings remain', 'confirm=true']],
]

function plain(value) {
  if (Array.isArray(value)) return value.map(plain)
  if (value && typeof value === 'object') {
    if ('type' in value && 'value' in value) return plain(value.value)
    return Object.fromEntries(Object.entries(value).map(([key, child]) => [key, plain(child)]))
  }
  return value
}

function componentText(component) {
  const value = plain(component)
  if (typeof value === 'string') {
    if (value.startsWith('{') || value.startsWith('[')) {
      try { return componentText(JSON.parse(value)) } catch { }
    }
    return value.replace(/§./g, '')
  }
  if (Array.isArray(value)) return value.map(componentText).join('')
  return value && typeof value === 'object'
    ? componentText(value.text ?? '') + componentText(value.extra ?? []) : ''
}

function itemText(item) {
  if (!item) return ''
  return [componentText(item.customName), ...(item.customLore ?? []).map(componentText)]
    .join(' ').replace(/\s+/g, ' ').trim()
}

function menuText(window) {
  return window.slots.slice(0, window.inventoryStart).map(itemText).join(' ')
}

export default {
  name: 'adapt-claims-guide',
  description: 'Read each Claims guide through real rank-menu clicks and verify Back and close behavior.',
  async run(context) {
    const bot = context.bot
    const configured = context.options.command === 'custom'
    const replacements = configured ? new Map([['-1000', '-600'], ['1000', '600'], ['Up to 4', 'Up to 2'],
      ['300 seconds', '75 seconds'], ['120 seconds', '45 seconds'], ['25 total', '30 total']]) : new Map()
    context.report.claimsGuides = []
    try {
      await bot.waitForTicks(40)
      for (const [name, expected] of ADAPTATIONS) {
        await context.step(`${name} exposes controls, rules, and ownership guidance`, async () => {
          await context.command(`/adapt gui target=adaptation:claims-${name}`, /Opened GUI/, 10000)
          await context.waitUntil(() => Boolean(bot.currentWindow)
            && menuText(bot.currentWindow).includes('Right-click for controls'),
          { label: `${name} rank help affordance`, timeoutMs: 5000 })
          const ranks = bot.currentWindow
          const slot = ranks.slots.slice(0, ranks.inventoryStart)
            .findIndex(item => itemText(item).includes('Right-click for controls'))
          const before = itemText(ranks.slots[slot])
          await bot.clickWindow(slot, 1, 0)
          await context.waitUntil(() => Boolean(bot.currentWindow) && bot.currentWindow !== ranks
            && menuText(bot.currentWindow).includes('Back to adaptation ranks'),
          { label: `${name} guide opens`, timeoutMs: 5000 })
          const guide = bot.currentWindow
          const text = menuText(guide)
          for (const phrase of expected) {
            const value = replacements.get(phrase) ?? phrase
            context.expect(text.includes(value), `${name} explains ${value}`, { text })
          }
          context.expect(!/\{(?:worlds|minimum|maximum|rate|seconds|capacity)\}/.test(text), `${name} resolves configured values`)
          const back = guide.slots.slice(0, guide.inventoryStart)
            .findIndex(item => itemText(item).includes('Back to adaptation ranks'))
          await bot.clickWindow(back, 0, 0)
          await context.waitUntil(() => Boolean(bot.currentWindow) && bot.currentWindow !== guide
            && menuText(bot.currentWindow).includes('Right-click for controls'),
          { label: `${name} returns to rank menu`, timeoutMs: 5000 })
          context.expect(itemText(bot.currentWindow.slots[slot]) === before,
            `${name} guide leaves rank, cost, and learning state unchanged`)
          await bot.clickWindow(slot, 1, 0)
          await context.waitUntil(() => Boolean(bot.currentWindow)
            && menuText(bot.currentWindow).includes('Back to adaptation ranks'),
          { label: `${name} guide reopens`, timeoutMs: 5000 })
          bot.closeWindow(bot.currentWindow)
          await bot.waitForTicks(10)
          context.expect(!bot.currentWindow, `${name} close does not reopen the rank menu`)
          context.report.claimsGuides.push({ adaptation: name, text, back: true, unchangedProgression: true, close: true })
        })
      }
    } finally {
      if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
      bot.clearControlStates()
    }
  },
}
