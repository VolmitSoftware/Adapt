import { randomBytes } from 'node:crypto'
import { fixtureJson } from './fixture-json.mjs'
import { synchronizePlayerInput } from './player-input.mjs'

const OVERWORLD = 'minecraft:overworld'
const NETHER = 'minecraft:the_nether'
const END = 'minecraft:the_end'

export default {
  name: 'adapt-claims-portals',
  description: 'Exercise actual vanilla Nether and End portal traversal, claim-safe native exits, preparation protection and mounted cancellation on Paper.',
  async run(context) {
    const actors = {}
    const stops = []
    const suffix = randomBytes(3).toString('hex')
    const evidence = context.report.claimsPortals = { actors: {}, journeys: [] }
    context.bot.physicsEnabled = false
    const fixture = (actor, action) => fixtureJson(context,
      `/claimsqa ${actor.bot.username} ${action}`, `CLAIMS ${action.split(' ')[0]}`, 60000)
    const snapshot = actor => fixture(actor, 'portal-snapshot')
    const inspectUntil = async (actor, predicate, label, timeoutMs = 15000) => {
      let state
      await context.waitUntil(async () => {
        state = await snapshot(actor)
        return predicate(state)
      }, { label, timeoutMs, intervalMs: 50 })
      return state
    }
    const contains = (state, point) => point.world === state.anchor.world && state.city.chunks.some(chunk =>
      chunk.x === Math.floor(point.x / 16) && chunk.z === Math.floor(point.z / 16))
    const halt = actor => {
      actor.bot.clearControlStates()
      actor.bot.physicsEnabled = false
    }
    const journey = async (actor, { source = 'nether', inside = false, destination = OVERWORLD } = {}) => {
      const before = await fixture(actor, `portal-enter ${source}`)
      const stopped = () => halt(actor)
      actor.bot.once('respawn', stopped)
      try {
        if (!['stationary', 'end'].includes(source)) {
          await context.waitUntil(() => {
            const position = actor.bot.entity.position
            const floor = position.clone().set(before.location.x, before.location.y - 1, before.location.z)
            const portal = position.clone().set(before.location.x, before.location.y, before.location.z + 2)
            return position.distanceTo(position.clone().set(before.location.x, before.location.y, before.location.z)) < 0.2
              && actor.bot.blockAt(floor)?.name === 'stone' && actor.bot.blockAt(portal)?.name === 'nether_portal'
          }, { label: 'Client receives the portal approach and its solid floor', timeoutMs: 10000 })
          actor.bot.physicsEnabled = true
          const target = actor.bot.entity.position.clone().offset(0, 1.5, 3)
          await actor.bot.lookAt(target, true)
          actor.bot.setControlState('forward', true)
        }
        const after = await inspectUntil(actor, state => !state.pending && state.location.world === destination
          && state.events.some(event => event.kind === 'arrival'), 'Actual portal traversal completes')
        halt(actor)
        await context.sleep(150)
        const settled = await snapshot(actor)
        const portal = settled.events.find(event => event.kind === 'portal')
        const arrivals = settled.events.filter(event => event.kind === 'arrival')
        context.expect(Boolean(portal), 'The real portal block produces PlayerPortalEvent', settled.events)
        context.expect(arrivals.length === 1 && arrivals[0].allowed,
          'Exactly one actual dimension arrival reaches an allowed destination', arrivals)
        context.expect(settled.events.every(event => !['portal', 'teleport', 'arrival'].includes(event.kind)
          || event.cancelled || event.allowed), 'No intermediate forbidden teleport or arrival occurs', settled.events)
        if (destination !== NETHER) {
          context.expect(contains(settled, settled.location) === inside,
            inside ? 'The citizen reaches their own town' : 'The destination stays outside every claimed chunk', settled.location)
        }
        context.expect(settled.anchorMaterial === 'LODESTONE', 'The main anchor remains intact')
        if (portal.cause === 'NETHER_PORTAL') {
          const scale = portal.from.world === NETHER ? 8 : 1 / 8
          context.expect(portal.to.x === Math.floor(portal.from.x * scale)
            && portal.to.z === Math.floor(portal.from.z * scale),
          'Vanilla supplies the correct 8x coordinate scaling before claim-aware resolution', portal)
          context.expect(arrivals[0].cooldown > 0, 'Committed native travel sets a positive portal cooldown', arrivals[0])
          context.expect(Object.values(arrivals[0].velocity).every(Number.isFinite),
            'Native travel preserves finite motion after arrival', arrivals[0].velocity)
          await context.sleep(1000)
          const held = await snapshot(actor)
          context.expect(held.location.world === destination && held.events.filter(event => event.kind === 'arrival').length === 1,
            'Standing at the destination portal does not immediately bounce back', held.events)
        }
        evidence.journeys.push({ actor: actor.bot.username, source, before, after: settled })
        return settled
      } finally {
        actor.bot.off('respawn', stopped)
        halt(actor)
      }
    }
    const allowedCreations = state => state.events.filter(event => event.kind === 'create' && !event.cancelled)
    const outsideCreation = state => {
      const created = allowedCreations(state)
      context.expect(created.length > 0, 'Vanilla creates a real replacement portal', state.events)
      for (const event of created) {
        context.expect(event.blocks.every(block => block.allowed && !contains(state, block)),
          'Every created frame, support and clearance block stays outside protected land', event.blocks)
        context.expect(event.blocks.some(block => block.material === 'OBSIDIAN')
          && event.blocks.some(block => block.material === 'NETHER_PORTAL'),
        'The creation event contains the native frame and active portal', event.blocks)
      }
    }

    try {
      await context.step('Ordinary actors receive native portal layouts and a disjoint homestead', async () => {
        for (const [role, marker] of [['founder', 'f'], ['member', 'm'], ['attacker', 'a']]) {
          const actor = await context.connectActor(`AQAc${marker}${suffix}`)
          actors[role] = actor
          stops.push(synchronizePlayerInput(actor.bot))
          await actor.bot.waitForTicks(20)
          actor.bot.physicsEnabled = false
          const state = await fixture(actor, `portal-prepare ${role}`)
          context.expect(!state.operator && !state.bypass, `${role} has no Claims bypass or operator permission`)
          context.expect(state.city.chunks.length === 18, 'The test town includes two disjoint 3 by 3 footprints')
          evidence.actors[role] = actor.bot.username
        }
      })
      await context.step('Native POI search skips the outsider’s town portal and the frame crossing its boundary', async () => {
        const state = await journey(actors.attacker)
        context.expect(state.location.x >= 4073 && state.location.x < 4074,
          'The outsider selects the permitted perpendicular portal beyond the boundary decoy', state.location)
        context.expect(allowedCreations(state).length === 0, 'The allowed existing portal is reused without construction')
        const portal = state.events.find(event => event.kind === 'portal')
        const arrival = state.events.find(event => event.kind === 'arrival')
        context.expect(Math.abs(portal.motion.x) + Math.abs(portal.motion.z) > 0.001,
          'Physical portal entry supplies nonzero horizontal motion', portal.motion)
        context.expect(Math.abs(arrival.velocity.x) > Math.abs(arrival.velocity.z),
          'The perpendicular exit rotates forward motion onto its horizontal axis', arrival.velocity)
      })
      await context.step('The founder and member reuse their own town portal', async () => {
        for (const actor of [actors.founder, actors.member]) {
          const state = await journey(actor, { inside: true })
          context.expect(state.location.x >= 4104 && state.location.x <= 4106,
            'Citizen travel uses the existing town rectangle', state.location)
          context.expect(allowedCreations(state).length === 0, 'Citizen reuse creates no replacement portal')
        }
      })
      await context.step('Claims configuration reload preserves native portal protection', async () => {
        const before = await snapshot(actors.founder)
        const reloaded = await fixture(actors.founder, 'portal-reload')
        context.expect(reloaded.city.id === before.city.id && reloaded.anchorMaterial === 'LODESTONE',
          'Reload preserves the town and its main lodestone', reloaded)
        const state = await journey(actors.attacker)
        context.expect(state.location.x >= 4073 && state.location.x < 4074 && allowedCreations(state).length === 0,
          'Reloaded protection still skips the forbidden town and boundary frame', state.location)
        evidence.reload = { before, reloaded, after: state }
      })
      await context.step('Overworld entry applies the reciprocal Nether coordinate scale', async () => {
        const state = await journey(actors.founder, { source: 'overworld', destination: NETHER })
        context.expect(state.location.x >= 512 && state.location.x <= 514 && state.location.y < 128,
          'The return exit uses the original portal below the Nether roof', state.location)
      })
      await context.step('With no permitted existing exit, vanilla creates an outsider portal outside all claims', async () => {
        await fixture(actors.founder, 'portal-layout empty')
        outsideCreation(await journey(actors.attacker, { source: 'stationary' }))
      })
      await context.step('An unclaimed destination whose creation footprint crosses the boundary is relocated', async () => {
        await fixture(actors.founder, 'portal-layout boundary')
        const state = await journey(actors.attacker, { source: 'stationary' })
        const portal = state.events.find(event => event.kind === 'portal')
        context.expect(!contains(state, portal.to) && portal.to.x === 4078,
          'The requested center is outside town while the full creation footprint crosses its edge', portal.to)
        outsideCreation(state)
      })
      await context.step('Native portal construction cannot replace even its founder’s lodestone support block', async () => {
        await fixture(actors.founder, 'portal-layout anchor')
        const state = await journey(actors.founder, { source: 'stationary' })
        context.expect(state.events.some(event => event.kind === 'create' && event.cancelled
          && event.blocks.some(block => !block.allowed && block.before === 'LODESTONE')),
        'The actual native creation event is vetoed for its protected support block', state.events)
        outsideCreation(state)
      })
      await context.step('The founder cannot create an exit through another citizen’s homestead', async () => {
        await fixture(actors.founder, 'portal-layout homestead')
        const state = await journey(actors.founder, { source: 'stationary' })
        context.expect(state.personalMaterial === 'LODESTONE', 'The personal lodestone remains intact')
        outsideCreation(state)
      })
      await context.step('Unsupported mounted portal travel is cancelled with the ride intact', async () => {
        await fixture(actors.founder, 'portal-layout initial')
        const mounted = await fixture(actors.attacker, 'portal-mount')
        context.expect(mounted.mounted, 'The actor is riding the actual boat inside a portal', mounted)
        const state = await inspectUntil(actors.attacker,
          current => current.events.some(event => event.kind === 'vehiclePortal'), 'Mounted native portal attempt')
        context.expect(state.events.filter(event => event.kind === 'vehiclePortal').every(event => event.cancelled),
          'Every observed mounted EntityPortalEvent is cancelled', state.events)
        context.expect(state.location.world === NETHER && state.mounted && state.vehicle === mounted.vehicle
          && !state.events.some(event => event.kind === 'arrival'),
        'The same boat and player remain mounted in the source dimension', state)
        evidence.mounted = { mounted, state }
      })
      await context.step('End travel redirects outsiders and vetoes protected platform replacement', async () => {
        await fixture(actors.founder, 'portal-layout end')
        const outsider = await journey(actors.attacker, { source: 'end', destination: END })
        context.expect(allowedCreations(outsider).length === 0,
          'The outsider redirect does not create a platform inside the claimed destination')
        const citizen = await journey(actors.founder, { source: 'end', destination: END, inside: true })
        context.expect(citizen.events.some(event => event.kind === 'create' && event.cancelled
          && event.blocks.some(block => block.before === 'LODESTONE' && !block.allowed)),
        'Vanilla End platform preparation is vetoed when it would replace the protected anchor', citizen.events)
      })
      evidence.completed = true
      return evidence
    } finally {
      for (const actor of Object.values(actors)) {
        halt(actor)
        await fixture(actor, 'portal-cleanup')
      }
      for (const stop of stops) stop()
    }
  },
}
