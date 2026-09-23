import { randomBytes } from 'node:crypto'
import { skillMatrix, assertSkillCoverage, xpEvidence } from './skill-matrix.mjs'

export async function runSkillSuite(context, requestedSkills = skillMatrix.map(entry => entry.skill)) {
    if (!Array.isArray(requestedSkills) || requestedSkills.length === 0 || new Set(requestedSkills).size !== requestedSkills.length || requestedSkills.some(skill => !skillMatrix.some(entry => entry.skill === skill))) throw new Error('Select distinct registered skills')
    const suffix = randomBytes(4).toString('hex')
    const actor = await context.connectActor(`AQAa${suffix}`)
    const opponent = await context.connectActor(`AQAb${suffix}`)
    const evidence = context.report.adapt = { skills: [], requestedSkills, actors: [actor.bot.username, opponent.bot.username], fixture: 'AdaptGameplayFixture', coverage: 'gameplay XP; no abilities or menus' }
    let sequence = 0
    let setup = false
    async function snapshot(player = actor) {
      const token = `s${++sequence}`
      const prefix = `ADAPT_QA SNAPSHOT ${token} `
      const message = await context.command(`/adaptqa snapshot ${player.bot.username} ${token}`, new RegExp(`^${prefix}`), 5000)
      return JSON.parse(message.slice(prefix.length))
    }
    async function equip(name, target = actor, destination = 'hand') {
      await context.waitUntil(() => target.bot.inventory.items().some(item => item.name === name), { label: `${name} fixture item`, timeoutMs: 5000 })
      await target.bot.equip(target.bot.inventory.items().find(item => item.name === name), destination)
    }
    function position(x, y, z) { return actor.bot.entity.position.clone().set(x, y, z) }
    async function blockAt(x, y, z, name) {
      await context.waitUntil(() => actor.bot.blockAt(position(x, y, z))?.name === name, { label: `fixture ${name} at ${x},${y},${z}`, timeoutMs: 5000 })
      return actor.bot.blockAt(position(x, y, z))
    }
    async function mine(x, y, z, name, tool) {
      if (tool) await equip(tool)
      await actor.bot.dig(await blockAt(x, y, z, name))
      await context.waitUntil(() => actor.bot.blockAt(position(x, y, z))?.name !== name, { label: `${name} broken by player`, timeoutMs: 5000 })
    }
    async function melee(tool, attacker = actor, target = opponent) {
      if (tool) await equip(tool, attacker)
      return attacker.actions.attackPlayer(target.bot, { hits: 1, minimumHealth: 6, timeoutMs: 10_000 })
    }
    async function action(skill) {
      switch (skill) {
        case 'agility': return actor.actions.walkCircle({ center: { x: 0, y: 100, z: 0 }, radius: 5, laps: 1, timeoutMs: 60_000 })
        case 'axes': return melee('wooden_axe')
        case 'swords': return melee('wooden_sword')
        case 'unarmed':
          await melee()
          await context.command('/adaptqa stage unarmed', /^ADAPT_QA STAGE unarmed$/, 5000)
          return melee(null, opponent, actor)
        case 'stealth':
          actor.bot.setControlState('sneak', true)
          try { return await melee() } finally { actor.bot.setControlState('sneak', false) }
        case 'tragoul': return melee(null, opponent, actor)
        case 'blocking': {
          await equip('wooden_sword', opponent)
          await actor.bot.lookAt(opponent.bot.entity.position.offset(0, 1, 0), true)
          actor.bot.activateItem(true)
          try {
            await actor.bot.waitForTicks(8)
            const health = actor.bot.health
            const target = opponent.bot.players[actor.bot.username]?.entity
            context.expect(Boolean(target), 'Shield defender is visible')
            await opponent.bot.lookAt(target.position.offset(0, 1, 0), true)
            opponent.bot.attack(target)
            await actor.bot.waitForTicks(12)
            context.expect(actor.bot.health === health, 'Shield prevented player damage', { before: health, after: actor.bot.health })
          } finally { actor.bot.deactivateItem() }
          return
        }
        case 'architect':
          await equip('stone_bricks')
          await actor.bot.placeBlock(await blockAt(1, 99, 1, 'stone'), position(0, 1, 0))
          await blockAt(1, 100, 1, 'stone_bricks')
          return
        case 'pickaxe': return mine(2, 100, 2, 'diamond_ore', 'iron_pickaxe')
        case 'excavation': return mine(3, 100, 2, 'clay', 'iron_shovel')
        case 'nether': return mine(2, 100, 2, 'wither_rose')
        case 'crafting': {
          const type = actor.bot.registry.itemsByName.oak_planks.id
          const recipe = actor.bot.recipesFor(type, null, 1, null)[0]
          context.expect(Boolean(recipe), 'Player has an available plank recipe')
          await actor.bot.craft(recipe, 1, null)
          context.expect(actor.bot.inventory.items().some(item => item.name === 'oak_planks'), 'Crafted planks arrived in inventory')
          return
        }
        case 'discovery':
          await actor.bot.activateBlock(await blockAt(-2, 100, 2, 'amethyst_block'))
          return
        case 'herbalism':
          await equip('apple')
          await actor.bot.consume()
          context.expect(actor.bot.food > 10, 'Eating replenished player hunger')
          return
        case 'brewing':
        case 'chronos':
          await equip('potion')
          await actor.bot.consume()
          await context.waitUntil(() => Object.values(actor.bot.entity.effects).some(effect => effect.id === actor.bot.registry.effectsByName.Speed?.id), { label: 'consumed swiftness effect', timeoutMs: 5000 })
          return
        case 'ranged': {
          await equip('bow')
          const health = opponent.bot.health
          await actor.bot.lookAt(opponent.bot.entity.position.offset(0, 1.1, 0), true)
          actor.bot.activateItem()
          await actor.bot.waitForTicks(22)
          actor.bot.deactivateItem()
          await context.waitUntil(() => opponent.bot.health < health, { label: 'arrow damaged opponent', timeoutMs: 5000 })
          return
        }
        case 'rift': {
          await equip('ender_pearl')
          const before = actor.bot.entity.position.clone()
          await actor.bot.look(0, -0.25, true)
          actor.bot.activateItem()
          actor.bot.deactivateItem()
          await context.waitUntil(() => actor.bot.entity.position.distanceTo(before) > 3 && actor.bot.entity.onGround, { label: 'ender pearl arrival', timeoutMs: 10_000 })
          return
        }
        case 'enchanting': {
          const table = await actor.bot.openEnchantmentTable(await blockAt(2, 100, 2, 'enchanting_table'))
          try {
            await table.putTargetItem(table.items().find(item => item.name === 'iron_sword'))
            await table.putLapis(table.items().find(item => item.name === 'lapis_lazuli'))
            try {
              await context.waitUntil(() => table.enchantments.some(option => option.level > 0), { label: 'enchantment offers', timeoutMs: 5000 })
            } finally {
              evidence.enchanting = { offers: table.enchantments.map(offer => ({ ...offer, expected: { ...offer.expected } })), slots: table.slots.slice(0, 2).map(item => item && { name: item.name, count: item.count, slot: item.slot }), level: actor.bot.experience.level }
            }
            await table.enchant(table.enchantments.findIndex(option => option.level > 0))
            const item = await table.takeTargetItem()
            const enchants = item?.componentMap?.get('enchantments')?.data?.enchantments
            context.expect(Array.isArray(enchants) && enchants.some(enchant => enchant.level > 0), 'Enchanted sword returned to inventory', { components: item?.components })
            evidence.enchanting.enchants = enchants
          } finally { table.close() }
          return
        }
        case 'hunter': {
          await equip('wooden_sword')
          await context.waitUntil(() => Object.values(actor.bot.entities).some(entity => entity.name === 'cow'), { label: 'hunter fixture cow', timeoutMs: 5000 })
          const cow = actor.bot.nearestEntity(entity => entity.name === 'cow')
          await actor.bot.lookAt(cow.position.offset(0, 0.8, 0), true)
          actor.bot.attack(cow)
          await context.waitUntil(() => !actor.bot.entities[cow.id], { label: 'cow killed by player', timeoutMs: 5000 })
          return
        }
        case 'taming': {
          await equip('wheat')
          await context.waitUntil(() => Object.values(actor.bot.entities).filter(entity => entity.name === 'cow').length === 2, { label: 'breeding pair', timeoutMs: 5000 })
          for (const cow of Object.values(actor.bot.entities).filter(entity => entity.name === 'cow')) {
            await actor.bot.lookAt(cow.position.offset(0, 0.7, 0), true)
            await actor.bot.activateEntity(cow)
          }
          await context.waitUntil(() => Object.values(actor.bot.entities).filter(entity => entity.name === 'cow').length === 3, { label: 'bred calf', timeoutMs: 30_000 })
          return
        }
        case 'kinetics':
          await actor.bot.look(-Math.PI / 2, 0, true)
          actor.bot.setControlState('forward', true)
          try { await context.waitUntil(() => actor.bot.entity.position.y < 101 && actor.bot.entity.onGround, { label: 'hay landing after real fall', timeoutMs: 10_000 }) }
          finally { actor.bot.clearControlStates() }
          return
        case 'seaborne': return mine(2, 99, 2, 'dirt', 'iron_shovel')
        default: throw new Error(`No gameplay action for ${skill}`)
      }
    }
    try {
      await context.step('prepare isolated Adapt arena and ordinary players', async () => {
        await context.command(`/adaptqa setup ${actor.bot.username} ${opponent.bot.username}`, /^ADAPT_QA SETUP /, 60_000)
        setup = true
        await context.waitUntil(async () => {
          try { assertSkillCoverage((await snapshot()).registered); return true } catch { return false }
        }, { label: 'all Adapt skills ready', timeoutMs: 30_000, intervalMs: 1000 })
        const initial = await snapshot()
        assertSkillCoverage(initial.registered, initial.enabled)
        context.expect(!initial.operator, 'Skill actor has ordinary permissions')
      })
      for (const { skill, action: description } of skillMatrix.filter(entry => requestedSkills.includes(entry.skill))) {
        await context.step(`earn ${skill} XP: ${description}`, async () => {
          await context.command(`/adaptqa stage ${skill}`, new RegExp(`^ADAPT_QA STAGE ${skill}$`), 10_000)
          await actor.bot.waitForTicks(4)
          const before = await snapshot()
          await action(skill)
          let after
          await context.waitUntil(async () => {
            after = await snapshot()
            return after.xp[skill] > before.xp[skill]
          }, { label: `${skill} gameplay XP increase`, timeoutMs: 12_000, intervalMs: 500 })
          evidence.skills.push({ ...xpEvidence(skill, before, after), action: description })
        })
      }
      context.expect(evidence.skills.length === requestedSkills.length, 'Every requested skill earned XP')
      if (requestedSkills.length === skillMatrix.length) assertSkillCoverage(evidence.skills.map(entry => entry.skill))
    } finally {
      for (const player of [actor, opponent]) {
        player.bot.deactivateItem()
        player.bot.clearControlStates()
        if (player.bot.currentWindow) player.bot.closeWindow(player.bot.currentWindow)
      }
      if (setup && !context.signal.aborted) await context.command('/adaptqa cleanup', /^ADAPT_QA CLEANUP$/, 15_000)
    }
}

export default {
  name: 'adapt-all-skills',
  description: 'Two ordinary players fight and earn actual gameplay XP in every registered Adapt skill.',
  run: runSkillSuite,
}
