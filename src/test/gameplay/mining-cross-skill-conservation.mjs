import { randomBytes } from 'node:crypto'
import { join } from 'node:path'
import { fixtureJson } from './fixture-json.mjs'
import { errorsSince, logPosition } from './runtime-log.mjs'

export default {
  name: 'adapt-mining-cross-skill-conservation',
  description: 'Verify area mining preserves tools, dispatches sibling adaptations, charges configured durability and respects block protection.',
  async run(context) {
    context.expect(context.report.server.instance.startsWith('adapt-mining-fixes-'), 'Mining checks target their disposable instance')
    const suffix = randomBytes(4).toString('hex')
    const actor = await context.connectActor(`AQAM${suffix}`)
    const other = await context.connectActor(`AQAN${suffix}`)
    const fixture = command => fixtureJson(context,
      `/adaptqa mining-audit ${actor.bot.username} ${command}`, `MINING_AUDIT ${command.split(' ')[0]}`, 10000)
    const evidence = context.report.miningCrossSkills = { cases: [] }
    const count = (state, type, source) => state.items.filter(item => item.type === type && (!source || item.source === source))
      .reduce((sum, item) => sum + item.amount, 0)
    const log = await logPosition(join(context.report.server.directory, 'logs', 'latest.log'))
    let setup = false
    try {
      await context.command(`/adaptqa setup ${actor.bot.username} ${other.bot.username}`, /^ADAPT_QA SETUP /, 30000)
      setup = true
      for (const mode of ['pact', 'masterwork', 'inventory', 'tunneler', 'grave', 'free', 'cost', 'deny']) {
        await context.step(`Record real survival mining: ${mode}`, async () => {
          actor.bot.clearControlStates()
          const before = await fixture(`prepare ${mode === 'deny' ? 'inventory' : mode}`)
          if (mode === 'deny') await fixture('deny')
          actor.bot.setQuickBarSlot(0)
          actor.bot.setControlState('sneak', true)
          const target = actor.bot.entity.position.clone().set(3, 100, 0)
          await context.waitUntil(() => ['stone', 'dirt'].includes(actor.bot.blockAt(target)?.name),
            { label: 'Fresh mining plane reaches client', timeoutMs: 5000 })
          await actor.bot.dig(actor.bot.blockAt(target))
          await actor.bot.waitForTicks(12)
          const after = await fixture('snapshot')
          const tool = after.items.find(item => item.type === (mode === 'tunneler' || mode === 'grave' ? 'DIAMOND_SHOVEL' : 'DIAMOND_PICKAXE'))
          const expectedBlocks = mode === 'deny' ? 1 : 9
          const broken = after.blocks.filter(block => block === 'AIR').length
          let valid = broken === expectedBlocks && Boolean(tool)
          if (mode === 'pact') valid &&= tool.damage <= 1560
          if (mode === 'masterwork') valid &&= tool.damage === 1569 && tool.maximum === 2000
          if (mode === 'inventory' || mode === 'deny') valid &&= count(after, 'COBBLESTONE', 'inventory') === expectedBlocks && count(after, 'COBBLESTONE', 'drop') === 0
          if (mode === 'tunneler') valid &&= count(after, 'DIRT', 'inventory') === 9 && count(after, 'DIRT', 'drop') === 0
          if (mode === 'grave') valid &&= after.graveLoot - before.graveLoot === 9
          if (mode === 'free') valid &&= tool.damage === 1
          if (mode === 'cost') valid &&= tool.damage === 25
          evidence.cases.push({ mode, before, after, valid })
        })
      }
      evidence.log = await errorsSince(log)
      context.expect(evidence.log.errors.length === 0, 'Area mining emits no server errors', evidence.log)
      context.expect(evidence.cases.every(result => result.valid), 'Every area-mining interaction conserves tools, costs and sibling rewards', evidence.cases)
    } finally {
      actor.bot.clearControlStates()
      other.bot.clearControlStates()
      if (setup && !context.signal.aborted) await context.command('/adaptqa cleanup', /^ADAPT_QA CLEANUP$/, 15000)
    }
  },
}
