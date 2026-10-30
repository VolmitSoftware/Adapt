import { randomBytes } from 'node:crypto'
import { readFile, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { fixtureJson } from './fixture-json.mjs'
import { synchronizePlayerInput } from './player-input.mjs'
import { diggingEnchantments, synchronizeDiggingEnchantments } from './player-digging.mjs'

const RETAINED_FILE = '.adapt-claims-retained.json'
const HEIGHT = 100

export const claimsAdaptationIds = Object.freeze([
  'claims-founding', 'claims-fortification', 'claims-citizenship', 'claims-expansion', 'claims-homestead',
])

export function claimsEntryRoute(origin, destination) {
  if (origin.y !== HEIGHT || destination.y !== HEIGHT) throw new Error('Claims entry route requires the level arena apron')
  const route = []
  let x = origin.x
  let z = origin.z
  for (const axis of ['z', 'x']) {
    while (Math.abs((axis === 'x' ? x : z) - destination[axis]) > 0.01) {
      const current = axis === 'x' ? x : z
      const next = current + Math.sign(destination[axis] - current) * Math.min(8, Math.abs(destination[axis] - current))
      if (axis === 'x') x = next
      else z = next
      route.push({ x, y: HEIGHT, z })
    }
  }
  return route
}

export function claimsAdaptationEvidence(report) {
  if (!report?.completed || !Array.isArray(report.adaptations)
      || report.adaptations.length !== claimsAdaptationIds.length
      || new Set(report.adaptations.map(entry => entry.name)).size !== claimsAdaptationIds.length) {
    throw new Error('Claims adaptation evidence requires the completed dedicated gameplay suite')
  }
  for (const id of claimsAdaptationIds) {
    const entry = report.adaptations.find(candidate => candidate.name === id)
    if (entry?.behavior?.status !== 'passed' || !entry.behavior.assertions?.length
        || entry.behavior.assertions.some(assertion => typeof assertion !== 'string' || !assertion.trim())) {
      throw new Error(`Claims adaptation evidence is missing behavioral assertions for ${id}`)
    }
  }
  return report.adaptations
}

export async function runClaimsSuite(context, { mode = context.options.command || 'all' } = {}) {
    context.expect(['all', 'retain', 'verify-restart', 'homestead'].includes(mode), 'Claims mode is all, retain, verify-restart or homestead')
    const retained = mode === 'verify-restart'
      ? JSON.parse(await readFile(join(context.server.directory, RETAINED_FILE), 'utf8')) : null
    const suffix = randomBytes(3).toString('hex')
    const actors = {}
    const stops = []
    const evidence = context.report.claims = { mode, actors: {}, connections: {}, thresholds: [], adaptations: [] }
    let preserve = false
    let sites
    const fixture = (actor, action) => fixtureJson(context,
      `/claimsqa ${actor.bot.username} ${action}`, `CLAIMS ${action.split(' ')[0]}`, 30000)
    const snapshot = actor => fixture(actor, 'snapshot')
    const point = (actor, x, y, z) => actor.bot.entity.position.clone().set(x, y, z)
    const block = (actor, site, y = HEIGHT) => actor.bot.blockAt(point(actor, site.x, y, site.z))
    const equip = async (actor, name) => {
      await context.waitUntil(() => actor.bot.inventory.items().some(item => item.name === name),
        { label: `${actor.bot.username} receives ${name}`, timeoutMs: 5000 })
      await actor.bot.equip(actor.bot.inventory.items().find(item => item.name === name), 'hand')
    }
    const walk = async (actor, x, z) => {
      await actor.actions.walkRoute([{ x, y: HEIGHT, z }], { timeoutMs: 30000 })
      await actor.bot.waitForTicks(3)
      context.expect(Math.hypot(actor.bot.entity.position.x - x, actor.bot.entity.position.z - z) < 1.5,
        `${actor.bot.username} physically walks to the target`)
    }
    const inspectUntil = async (actor, predicate, label) => {
      let state
      await context.waitUntil(async () => {
        state = await snapshot(actor)
        return predicate(state)
      }, { label, timeoutMs: 10000, intervalMs: 100 })
      return state
    }
    const dig = async (actor, site) => {
      await equip(actor, 'netherite_pickaxe')
      await context.waitUntil(() => block(actor, site)?.name !== 'air',
        { label: 'Survival dig target is visible', timeoutMs: 5000 })
      await actor.bot.dig(block(actor, site), true)
      await actor.bot.waitForTicks(3)
    }
    const place = async (actor, site, material) => {
      await equip(actor, material)
      const floor = block(actor, site, HEIGHT - 1)
      context.expect(floor?.name === 'stone', 'The placement uses the supplied solid floor')
      await actor.bot.activateBlock(floor, point(actor, 0, 1, 0), point(actor, 0.5, 1, 0.5))
      await actor.bot.waitForTicks(6)
    }
    const shovel = async (actor, site, y = HEIGHT) => {
      await equip(actor, 'iron_shovel')
      await actor.bot.activateBlock(block(actor, site, y))
      await actor.bot.waitForTicks(3)
    }
    const raid = async (actor, key, count, required = 16, initial = 0) => {
      for (let index = 1; index <= count; index++) {
        await dig(actor, sites[key])
        const state = await snapshot(actor)
        const completed = initial + index / required
        if (completed < 1 - 1e-9) {
          context.expect(state.blocks[key] !== 'air', `${key} remains intact before its final completed break`, { index, state })
          context.expect(Math.abs(state.progress[key]?.breaking - completed) < 1e-8,
            `${key} records exactly one completed survival break`, { index, completed, progress: state.progress[key] })
        } else {
          context.expect(state.blocks[key] === 'air', `${key} breaks at the exact resistance threshold`, { index, state })
        }
      }
      evidence.thresholds.push({ actor: actor.bot.username, block: key, attempts: count, initial, required })
    }

    const claimHomestead = async () => {
      const { founder, member } = actors
      await context.step('Shovel expansion supplies disjoint and overlapping homestead footprints', async () => {
        const targets = []
        for (let x = 258; x <= 260; x++) {
          for (let z = 255; z <= 257; z++) targets.push({ x, z })
        }
        for (let z = 258; z <= 259; z++) {
          for (let x = 257; x <= 259; x++) targets.push({ x, z })
        }
        for (const chunk of targets) {
          const target = { x: chunk.x * 16 + 8, z: chunk.z * 16 + 8 }
          await walk(founder, target.x + 0.5, target.z + 2.5)
          await shovel(founder, target, HEIGHT - 1)
        }
        const expanded = await snapshot(founder)
        context.expect(expanded.city.chunks.length === 24, 'Fifteen edge-adjacent shovel selections expand the city')
        evidence.adaptations.push(proof('claims-expansion', [
          'The founder walks to and selects fifteen edge-adjacent chunks with the selected shovel.',
          'City ownership grows from nine to twenty-four full-height chunks.',
        ], { chunks: expanded.city.chunks }))
      })
      await context.step('Homestead activation rejects shared edge and corner chunks around the main lodestone', async () => {
        const before = await snapshot(member)
        for (const key of ['overlapEdge', 'overlapCorner']) {
          const site = sites[key]
          await walk(member, site.x + 0.5, site.z + 3.5)
          await place(member, site, 'lodestone')
          const rejection = member.waitForMessage('cannot overlap the main city anchor', 5000)
          await shovel(member, site)
          await rejection
          const state = await snapshot(member)
          context.expect(!state.plots[state.player], `${key} activation creates no personal plot`)
          context.expect(state.city.chunks.length === before.city.chunks.length,
            `${key} rejection preserves city ownership`)
          context.expect(state.blocks[key] === 'lodestone', `${key} rejection preserves the physical lodestone`)
        }
      })
      await context.step('A homestead three chunks from the main lodestone activates', async () => {
        await walk(member, sites.personalAnchor.x + 0.5, sites.personalAnchor.z + 3.5)
        await place(member, sites.personalAnchor, 'lodestone')
        await shovel(member, sites.personalAnchor)
        const state = await snapshot(member)
        context.expect(state.plots[state.player]?.chunks.length === 9, 'Member owns a complete personal nine-chunk footprint')
        evidence.homestead = { rejected: [[2, 0], [2, 2]], accepted: [3, 0], plot: state.plots[state.player] }
      })
    }

    try {
      for (const [role, marker] of [['founder', 'f'], ['member', 'm'], ['attacker', 'a']]) {
        const actor = await context.connectActor(retained?.actors[role] || `AQAc${marker}${suffix}`)
        actors[role] = actor
        stops.push(synchronizePlayerInput(actor.bot))
        stops.push(synchronizeDiggingEnchantments(actor.bot))
        await actor.bot.waitForTicks(20)
        const state = await fixture(actor, retained ? 'resume' : `prepare ${role}`)
        context.expect(!state.operator && !state.bypass && state.survival,
          `${role} is an ordinary survival player without Claims bypass`, state)
        await equip(actor, 'netherite_pickaxe')
        const enchantments = diggingEnchantments(actor.bot, actor.bot.heldItem)
        context.expect(enchantments.some(entry => entry.name === 'efficiency' && entry.lvl === 5)
          && enchantments.some(entry => entry.name === 'unbreaking' && entry.lvl === 3),
        `${role} retains Efficiency V and Unbreaking III for real survival digging`, { enchantments })
        evidence.actors[role] = actor.bot.username
        evidence.connections[role] = { location: state.location, player: state.player }
        sites = state.sites
      }
      const { founder, member, attacker } = actors

      if (retained) {
        await context.step('Claims ownership and normalized raid work survive a server process restart', async () => {
          const state = await snapshot(founder)
          context.expect(state.processId !== retained.processId, 'Restart verification uses a different server process')
          context.expect(state.siteCity?.id === retained.cityId, 'The city keeps its persistent identity')
          context.expect(state.siteCity.chunks.length === retained.chunks, 'All city chunks survive restart')
          context.expect(Object.keys(state.plots).length === 1, 'The personal plot survives restart')
          context.expect(Math.abs(state.progress.stone?.breaking - 0.5) < 1e-8, 'The half-completed stone raid survives restart')
          evidence.restart = state
        })
        await context.step('The relocated outsider walks into the city from the safe unclaimed apron', async () => {
          const before = await snapshot(attacker)
          const contains = location => before.siteCity.chunks.some(chunk => chunk.x === Math.floor(location.x / 16)
            && chunk.z === Math.floor(location.z / 16))
          context.expect(!contains(before.location), 'The outsider reconnects outside every city chunk', before.location)
          context.expect(before.location.y === HEIGHT, 'The validated reconnect location is on the level arena apron', before.location)
          const movements = attacker.bot.pathfinder.movements
          const sprinting = movements.allowSprinting
          const route = claimsEntryRoute(before.location, { x: 4106.5, y: HEIGHT, z: 4107.5 })
          evidence.reentry = { from: before.location, route }
          movements.allowSprinting = false
          try {
            for (const waypoint of route) {
              await walk(attacker, waypoint.x, waypoint.z)
            }
            const entered = await snapshot(attacker)
            evidence.reentry.to = entered.location
            context.expect(contains(entered.location) && entered.location.y === HEIGHT,
              'Ordinary grounded movement crosses the city boundary without teleporting', entered.location)
          } finally {
            movements.allowSprinting = sprinting
            evidence.reentry.final = (await snapshot(attacker)).location
          }
        })
        await context.step('Persisted raid work completes after the remaining exact survival breaks', async () => {
          await raid(attacker, 'stone', 8, 16, 0.5)
        })
      } else {
        await context.step('A shovel activates a real placed lodestone into a nine-chunk city', async () => {
          await place(founder, sites.anchor, 'lodestone')
          context.expect((await snapshot(founder)).blocks.anchor === 'lodestone', 'Founder places the physical lodestone')
          await shovel(founder, sites.anchor)
          const state = await inspectUntil(founder, current => current.city?.chunks.length === 9, 'Nine-chunk city activation')
          context.expect(state.city.ranks.fortification === 1, 'Founder activates the city at rank-one Fortification')
          evidence.founding = state
          evidence.adaptations.push(proof('claims-founding', [
            'The ordinary founder places a lodestone and activates it with a shovel.',
            'Activation claims the complete nine-chunk footprint with rank-one Fortification.',
          ], { city: state.city }))
        })

        await context.step('Shovel invitation requires the member to accept', async () => {
          await equip(founder, 'iron_shovel')
          const target = founder.bot.players[member.bot.username]?.entity
          context.expect(Boolean(target), 'Founder sees the member within interaction reach')
          await founder.bot.activateEntity(target)
          await founder.bot.waitForTicks(3)
          context.expect(!(await snapshot(member)).city, 'An invitation alone does not grant membership')
          member.bot.chat('/adapt claims accept')
          const state = await inspectUntil(member, current => Boolean(current.city), 'Member acceptance')
          context.expect(state.city.members.includes(state.player), 'Accepted member appears in city membership')
          evidence.adaptations.push(proof('claims-citizenship', [
            'Shovel invitation alone grants no membership.',
            'The invited ordinary player accepts and appears in city membership.',
          ], { members: state.city.members }))
        })

        if (mode === 'homestead') {
          await claimHomestead()
          await context.step('Configuration reload preserves the nonoverlapping homestead', async () => {
            const state = await fixture(member, 'reload')
            context.expect(state.city.chunks.length === 24 && state.plots[state.player]?.chunks.length === 9,
              'Reload preserves the city and personal nine-chunk footprint')
          })
          evidence.completed = true
          return evidence
        }

        await context.step('Another protection plugin denies completed breaks without Claims raid credit', async () => {
          await fixture(attacker, 'deny true')
          await dig(attacker, sites.stone)
          const state = await snapshot(attacker)
          context.expect(state.blocks.stone === 'stone' && !state.progress.stone,
            'An externally denied survival break leaves the block and raid progress unchanged', state)
          await fixture(attacker, 'deny false')
        })

        await context.step('Survival breaking shares progress and yields no intermediate drops or XP', async () => {
          const before = await snapshot(attacker)
          await raid(attacker, 'stone', mode === 'retain' ? 8 : 15)
          const intermediate = await snapshot(attacker)
          context.expect((intermediate.inventory.cobblestone ?? 0) === (before.inventory.cobblestone ?? 0),
            'Intermediate completed breaks produce no cobblestone')
          context.expect(intermediate.experience === before.experience, 'Intermediate breaks produce no vanilla XP')
          context.expect(intermediate.claimsXp === before.claimsXp, 'Raiding gives no Claims XP')
          if (mode !== 'retain') {
            await fixture(attacker, 'late-deny')
            await dig(attacker, sites.stone)
            const denied = await snapshot(attacker)
            context.expect(denied.blocks.stone === 'stone' && denied.progress.stone.breaking === 15 / 16,
              'A protection denial after the probe does not credit the final break', denied)
            await raid(attacker, 'stone', 1, 16, 15 / 16)
          }
        })

        await context.step('Survival placement consumes one item only at attempt sixty-four', async () => {
          const before = await snapshot(attacker)
          await fixture(attacker, 'deny true')
          await place(attacker, sites.placement, 'dirt')
          const denied = await snapshot(attacker)
          context.expect(!denied.progress.placement && denied.inventory.dirt === before.inventory.dirt,
            'Externally denied placement gives no progress and consumes no dirt')
          await fixture(attacker, 'deny false')
          for (let count = 1; count <= 64; count++) {
            if (count === 64) {
              await fixture(attacker, 'late-deny')
              await place(attacker, sites.placement, 'dirt')
              const denied = await snapshot(attacker)
              context.expect(denied.blocks.placement === 'air' && denied.progress.placement.placement === 63 / 64
                && denied.inventory.dirt === before.inventory.dirt,
              'A late placement denial earns no final credit and consumes no item', denied)
            }
            await place(attacker, sites.placement, 'dirt')
            const state = await snapshot(attacker)
            context.expect(state.blocks.placement === (count === 64 ? 'dirt' : 'air'),
              `Placement ${count} respects the exact threshold`, state)
            context.expect(state.inventory.dirt === before.inventory.dirt - (count === 64 ? 1 : 0),
              `Placement ${count} has normal final-only item consumption`, state)
            if (count < 64) context.expect(Math.abs(state.progress.placement?.placement - count / 64) < 1e-8,
              'Every eligible placement records one normalized attempt', state.progress.placement)
          }
          evidence.thresholds.push({ block: 'placement', attempts: 64, required: 64 })
        })

        await context.step('A bound Rift Access pearl cannot open a claimed chest through an intact wall', async () => {
          await walk(attacker, 4108.5, 4107.5)
          await fixture(attacker, 'access true')
          await equip(attacker, 'ender_pearl')
          await attacker.bot.lookAt(point(attacker, 4108.5, HEIGHT + 4, 4107.5), true)
          attacker.bot.activateItem()
          await attacker.bot.waitForTicks(20)
          context.expect(!attacker.bot.currentWindow, 'The claimed chest stays closed behind the intact stone wall')
          await fixture(attacker, 'access false')
          await equip(attacker, 'ender_pearl')
          attacker.bot.activateItem()
          await context.waitUntil(() => Boolean(attacker.bot.currentWindow),
            { label: 'The same bound pearl opens the chest after the wall is removed', timeoutMs: 10000 })
          const window = attacker.bot.currentWindow
          context.expect(window.slots.some(item => item?.name === 'diamond' && item.count === 3),
            'Unobstructed remote access exposes the real chest inventory')
          attacker.bot.closeWindow(window)
          await attacker.bot.waitForTicks(3)
          evidence.remoteAccess = { blockedByWall: true, openedWithClearReach: true }
        })

        await context.step('An attacker physically reaching a container can loot it', async () => {
          await equip(attacker, 'netherite_pickaxe')
          const chest = await attacker.bot.openContainer(block(attacker, sites.chest))
          try {
            const diamonds = chest.containerItems().find(item => item.name === 'diamond')
            context.expect(diamonds?.count === 3, 'The claimed chest contains the supplied loot')
            await attacker.bot.clickWindow(diamonds.slot, 0, 1)
            const looted = await inspectUntil(attacker, state => state.inventory.diamond === 3,
              'The server commits the real chest shift-click into the attacker inventory')
            context.expect(looted.inventory.diamond === 3, 'The attacker receives the actual looted diamonds', looted)
            evidence.looting = { inventory: looted.inventory, location: looted.location }
          } finally {
            attacker.bot.closeWindow(chest)
          }
        })

        await claimHomestead()

        await context.step('The founder faces personal resistance while the plot owner edits normally', async () => {
          await walk(founder, sites.personalStone.x + 0.5, sites.personalStone.z + 3.5)
          await dig(founder, sites.personalStone)
          const protectedState = await snapshot(founder)
          context.expect(protectedState.blocks.personalStone === 'stone', 'Founder cannot directly remove another member personal block')
          context.expect(Math.abs(protectedState.progress.personalStone?.breaking - 1 / 16) < 1e-8,
            'Founder uses online personal resistance')
          await walk(member, sites.personalStone.x + 0.5, sites.personalStone.z + 3.5)
          await dig(member, sites.personalStone)
          context.expect((await snapshot(member)).blocks.personalStone === 'air', 'The personal owner breaks their own block once')
          evidence.adaptations.push(proof('claims-homestead', [
            'The member activates a complete personal nine-chunk footprint inside expanded city land.',
            'Shared edge and corner chunks with the main lodestone footprint are rejected.',
            'The founder earns one sixteenth personal raid progress instead of directly breaking the block.',
            'The personal owner breaks their own block with one completed survival break.',
          ], { founderProgress: protectedState.progress.personalStone.breaking }))
        })

        await context.step('Configuration reload retains land, plots and raid work', async () => {
          const before = await snapshot(founder)
          const after = await fixture(founder, 'reload')
          context.expect(after.siteCity.id === before.siteCity.id && after.siteCity.chunks.length === before.siteCity.chunks.length,
            'Reload preserves the city identity and chunk ownership')
          context.expect(Object.keys(after.plots).length === 1, 'Reload preserves personal ownership')
          if (mode === 'retain') context.expect(Math.abs(after.progress.stone?.breaking - 0.5) < 1e-8,
            'Reload preserves completed raid work')
        })

        if (mode === 'retain') {
          const state = await fixture(founder, 'flush')
          const record = { processId: state.processId, actors: evidence.actors, cityId: state.siteCity.id,
            chunks: state.siteCity.chunks.length }
          await writeFile(join(context.server.directory, RETAINED_FILE), JSON.stringify(record, null, 2))
          evidence.retained = record
          preserve = true
          return evidence
        }
      }

      await context.step('Kicking a member retains personal ownership until the personal anchor is breached', async () => {
        founder.bot.chat(`/adapt claims kick ${member.bot.username}`)
        const state = await inspectUntil(member, current => !current.city, 'Member removal')
        context.expect(Boolean(state.plots[state.player]), 'Removed member keeps their personal plot')
        await walk(attacker, sites.personalAnchor.x + 0.5, sites.personalAnchor.z + 3.5)
        await raid(attacker, 'personalAnchor', 16)
        const after = await snapshot(attacker)
        context.expect(Object.keys(after.plots).length === 0 && after.siteCity,
          'Breaching the personal anchor returns its land to the surviving city')
      })

      await context.step('Breaching the main anchor releases city ownership and keeps remaining buildings', async () => {
        await walk(member, sites.anchor.x + 0.5, sites.anchor.z + 3.5)
        await raid(member, 'anchor', 8)
        await walk(attacker, sites.anchor.x + 0.5, sites.anchor.z + 3.5)
        await raid(attacker, 'anchor', 8, 16, 0.5)
        const state = await snapshot(attacker)
        context.expect(!state.siteCity && Object.keys(state.plots).length === 0, 'The destroyed main anchor releases every owned chunk and plot')
        context.expect(state.blocks.chest === 'chest' && state.blocks.placement === 'dirt', 'Existing buildings remain after release')
        context.expect(state.claimsXp === 0, 'Claiming, building and raiding give no automatic Claims XP')
        evidence.released = state
      })
      await context.step('Reconciliation releases ownership after an unobserved anchor removal', async () => {
        await walk(founder, sites.anchor.x + 0.5, sites.anchor.z + 3.5)
        await place(founder, sites.anchor, 'lodestone')
        await shovel(founder, sites.anchor)
        await inspectUntil(founder, state => state.siteCity?.chunks.length === 9, 'Recovery trial city activation')
        const orphaned = await fixture(founder, 'orphan')
        context.expect(orphaned.siteCity && orphaned.blocks.anchor === 'air',
          'The fixture reproduces a physical anchor removal before its ownership update')
        await fixture(founder, 'reload')
        const repaired = await inspectUntil(founder, state => !state.siteCity, 'Missing-anchor ownership reconciliation')
        context.expect(repaired.blocks.chest === 'chest' && repaired.blocks.placement === 'dirt',
          'Reconciliation releases land without removing buildings')
        evidence.anchorRecovery = { before: orphaned.siteCity.id, released: !repaired.siteCity }
      })

      await context.step('Claims XP changes only through its explicit administrative grant command', async () => {
        const before = await snapshot(attacker)
        context.bot.chat(`/adapt claims xp 125 ${attacker.bot.username}`)
        const after = await inspectUntil(attacker, state => state.claimsXp === before.claimsXp + 125, 'Exact administrative Claims XP grant')
        evidence.administrativeXp = { before: before.claimsXp, after: after.claimsXp, granted: 125 }
        context.expect(!after.operator && !after.bypass, 'Administrative progression does not grant the recipient bypass')
      })
      if (!retained) {
        evidence.adaptations.push(proof('claims-fortification', [
          'An external protection denial awards no breaking or placement progress.',
          'Online rank-one resistance requires sixteen completed survival breaks and sixty-four eligible placements.',
          'Intermediate actions yield no drops, vanilla XP or item consumption; the final placement consumes one item.',
          'Two attackers share the main-anchor progress and complete its sixteenth break together.',
          'Anchor destruction releases ownership and preserves remaining buildings.',
        ], { thresholds: evidence.thresholds, released: evidence.released }))
        evidence.completed = true
      }
      return evidence
    } finally {
      for (const actor of Object.values(actors)) {
        actor.bot.clearControlStates()
        actor.bot.deactivateItem()
        if (actor.bot.currentWindow) actor.bot.closeWindow(actor.bot.currentWindow)
      }
      if (!preserve) {
        for (const actor of Object.values(actors)) await fixture(actor, 'cleanup')
      }
      for (const stop of stops) stop()
    }
}

function proof(name, assertions, measurements) {
  return { name, suite: 'adapt-claims',
    behavior: { status: 'passed', assertions },
    sound: { status: 'not-applicable', assertions: [] },
    particles: { status: 'not-applicable', assertions: [] }, measurements }
}

export default {
  name: 'adapt-claims',
  description: 'Exercise ordinary survival founding, citizenship, raiding, personal ownership, looting, anchor destruction and persisted raid progress on Paper or Folia.',
  run: runClaimsSuite,
}
