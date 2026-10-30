import { randomBytes } from 'node:crypto'
import { fixtureJson } from './fixture-json.mjs'

const OVERWORLD = 'minecraft:overworld'
const UNDERDARK = 'bracken:the_underdark'
const HEIGHT = -59

export default {
  name: 'adapt-claims-travel',
  description: 'Verify coordinated Bracken Underdark arrivals, claim permissions, preparation footprints and pending travel lifecycle on Paper.',
  async run(context) {
    const suffix = randomBytes(3).toString('hex')
    const actors = {}
    const evidence = context.report.claimsTravel = { actors: {}, journeys: [] }
    context.bot.physicsEnabled = false
    const fixture = (actor, action) => fixtureJson(context,
      `/claimsqa ${actor.bot.username} ${action}`, `CLAIMS ${action.split(' ')[0]}`, 45000)
    const snapshot = actor => fixture(actor, 'travel-snapshot')
    const contains = (city, position) => position.world === OVERWORLD && city.chunks.some(chunk =>
      chunk.x === Math.floor(position.x / 16) && chunk.z === Math.floor(position.z / 16))
    const inspectUntil = async (actor, predicate, label, timeoutMs = 15000) => {
      let state
      await context.waitUntil(async () => {
        state = await snapshot(actor)
        return predicate(state)
      }, { label, timeoutMs, intervalMs: 100 })
      return state
    }
    const source = async (actor, site) => {
      const state = await fixture(actor, `travel-source ${site}`)
      context.expect(state.location.world === UNDERDARK && state.location.y === 260,
        'The route starts at the actual Underdark crossing threshold', state.location)
      await fixture(actor, 'travel-watch')
      return state
    }
    const journey = async (actor, site, inside) => {
      const before = await source(actor, site)
      context.bot.chat(`/execute as ${actor.bot.username} at @s run function bracken:dimension/crossing/underdark_to_overworld`)
      const after = await inspectUntil(actor,
        state => state.travel.seq > before.travel.seq && state.travel.state === 0 && state.travel.result === 1,
        `${actor.bot.username} completes the real Bracken route`)
      context.expect(after.location.world === OVERWORLD && after.location.y === HEIGHT,
        'Arrival retains the intended cave height', after.location)
      context.expect(contains(after.city, after.location) === inside,
        inside ? 'The citizen arrives inside their own town' : 'The rejected destination redirects outside all town chunks', after.location)
      context.expect(!after.pending && after.fireResistance,
        'Only completed travel releases the pending request and applies the route effect', after)
      context.expect(after.landing.feet === 'AIR' && after.landing.head === 'AIR'
        && ['DEEPSLATE', 'POLISHED_DEEPSLATE'].includes(after.landing.floor),
      'The approved destination receives the floor, feet and head preparation', after.landing)
      const arrivals = after.events.filter(event => event.kind === 'arrival')
      const teleports = after.events.filter(event => event.kind === 'teleport' && !event.cancelled)
      context.expect(arrivals.length === 1 && teleports.length === 1,
        'The server observes exactly one committed teleport and one dimension arrival', after.events)
      context.expect(after.events.every(event => event.cancelled || event.allowed),
        'No uncancelled teleport or actual intermediate arrival enters a forbidden town', after.events)
      if (!inside) {
        context.expect(arrivals.every(event => !contains(after.city, event.to)),
          'Every actual arrival remains outside the town', arrivals)
        context.expect(JSON.stringify(after.blocks[site]) === JSON.stringify(before.blocks[site]),
          'Denied arrival preparation leaves the requested protected column intact', { before: before.blocks[site], after: after.blocks[site] })
      } else {
        context.expect(after.location.x === before.location.x && after.location.z === before.location.z,
          'Allowed arrivals retain the requested horizontal position', { before: before.location, after: after.location })
      }
      evidence.journeys.push({ actor: actor.bot.username, site, before, after })
      return after
    }

    try {
      context.bot.chat('/data merge storage bracken:config {dimension_travel:false,travel_mode:"provider_required"}')
      await context.step('Ordinary actors receive a town and a separate member homestead at cave height', async () => {
        for (const [role, marker] of [['founder', 'f'], ['member', 'm'], ['attacker', 'a']]) {
          const actor = await context.connectActor(`AQAct${marker}${suffix}`)
          actors[role] = actor
          await actor.bot.waitForTicks(20)
          actor.bot.physicsEnabled = false
          const state = await fixture(actor, `travel-prepare ${role}${role === 'member' ? ` ${actors.founder.bot.username}` : ''}`)
          context.expect(!state.operator && !state.bypass, `${role} has no operator or Claims bypass permission`, state)
          context.expect(state.city.chunks.length === 18, 'The town includes both disjoint 3 by 3 anchor footprints')
          evidence.actors[role] = { name: actor.bot.username, player: state.player }
        }
        const state = await snapshot(actors.member)
        context.expect(Object.keys(state.plots).length === 1 && state.plots[state.player].chunks.length === 9,
          'The member owns the separate full nine-chunk homestead', state.plots)
      })

      await context.step('An outsider crosses only to an unclaimed cave destination', () => journey(actors.attacker, 'town', false))
      await context.step('The founder may arrive in the town', () => journey(actors.founder, 'town', true))
      await context.step('A member may arrive in the town', () => journey(actors.member, 'town', true))
      await context.step('Main and personal anchors survive rejected arrival preparation', async () => {
        await journey(actors.founder, 'anchor', false)
        await journey(actors.member, 'anchor', false)
        await journey(actors.member, 'personalAnchor', false)
        const state = await snapshot(actors.member)
        context.expect(state.blocks.anchor.feet === 'LODESTONE' && state.blocks.personalAnchor.feet === 'LODESTONE',
          'Both physical anchors remain intact after the attempted arrivals', state.blocks)
      })
      await context.step('The founder cannot prepare another citizen’s homestead column', () => journey(actors.founder, 'homestead', false))
      await context.step('The homestead owner may prepare and enter their own column', () => journey(actors.member, 'homestead', true))
      await context.step('An uncommitted approval expires without moving or applying completion effects', async () => {
        await source(actors.founder, 'town')
        const held = await fixture(actors.founder, 'travel-hold')
        context.expect(held.pending, 'The genuine travel service holds the prepared request')
        const expired = await inspectUntil(actors.founder, state => !state.pending, 'Pending request expires after its bounded deadline')
        context.expect(expired.location.world === UNDERDARK && !expired.fireResistance && expired.events.length === 0,
          'Timeout does not teleport or emit the route’s completion effect', expired)
        evidence.timeout = { held, expired }
      })
      await context.step('Claims reload invalidates pending travel and the next route still completes', async () => {
        await source(actors.founder, 'town')
        const held = await fixture(actors.founder, 'travel-hold')
        context.expect(held.pending, 'The reload begins with a live prepared request')
        await fixture(actors.founder, 'reload')
        const reset = await snapshot(actors.founder)
        context.expect(!reset.pending && reset.location.world === UNDERDARK && !reset.fireResistance,
          'Reload cancels the approval before any teleport or completion effect', reset)
        evidence.reload = { held, reset }
        await journey(actors.founder, 'town', true)
      })
      evidence.completed = true
      return evidence
    } finally {
      for (const actor of Object.values(actors)) {
        actor.bot.clearControlStates()
        await fixture(actor, 'cleanup')
      }
    }
  },
}
