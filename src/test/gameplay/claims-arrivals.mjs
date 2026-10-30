import { randomBytes } from 'node:crypto'
import { fixtureJson } from './fixture-json.mjs'

const OVERWORLD = 'minecraft:overworld'
const NETHER = 'minecraft:the_nether'
const NO_FALLBACK = 'No safe location outside the claim was found. Please contact an administrator.'

export default {
  name: 'adapt-claims-arrivals',
  description: 'Exercise real death and respawn, ability teleport completion, reload, and safe failure when no loaded respawn fallback exists.',
  async run(context) {
    const actors = {}
    const suffix = randomBytes(3).toString('hex')
    const evidence = context.report.claimsArrivals = { actors: {}, respawns: [], abilities: [] }
    context.bot.physicsEnabled = false
    const fixture = (actor, action) => fixtureJson(context,
      `/claimsqa ${actor.bot.username} ${action}`, `CLAIMS ${action.split(' ')[0]}`, 45000)
    const snapshot = actor => fixture(actor, 'arrival-snapshot')
    const inspectUntil = async (actor, predicate, label) => {
      let state
      await context.waitUntil(async () => {
        state = await snapshot(actor)
        return predicate(state)
      }, { label, timeoutMs: 20000, intervalMs: 100 })
      return state
    }
    const inside = (state, point) => point.world === state.anchor.world && state.city.chunks.some(chunk =>
      chunk.x === Math.floor(point.x / 16) && chunk.z === Math.floor(point.z / 16))
    const safeEvents = state => {
      context.expect(state.events.every(event => !['teleport', 'arrival'].includes(event.kind)
        || event.cancelled || event.allowed), 'No actual teleport or dimension arrival enters forbidden land', state.events)
      context.expect(state.lifecycle.filter(event => ['respawn', 'postRespawn'].includes(event.kind))
        .every(event => event.allowed), 'Both respawn selection and actual placement are allowed', state.lifecycle)
    }
    const respawn = async (actor, citizen) => {
      const before = await fixture(actor, 'arrival-respawn town')
      const after = await inspectUntil(actor, state => !state.dead && !state.pending
        && state.lifecycle.some(event => event.kind === 'postRespawn'), 'The actual death and respawn cycle completes')
      context.expect(after.lifecycle.some(event => event.kind === 'death'), 'The server processes a real player death', after.lifecycle)
      const selected = after.lifecycle.find(event => event.kind === 'respawn')
      const placed = after.lifecycle.find(event => event.kind === 'postRespawn')
      context.expect(inside(after, after.location) === citizen && inside(after, placed.to) === citizen,
        citizen ? 'The citizen respawns inside their own town' : 'The outsider is never initially placed in the town', after)
      context.expect(selected.loaded && selected.allowed, 'Respawn uses an already loaded allowed location', selected)
      if (citizen) {
        context.expect(JSON.stringify(selected.to) === JSON.stringify(after.requested),
          'Own-town respawn retains the requested destination', selected)
      }
      safeEvents(after)
      evidence.respawns.push({ actor: actor.bot.username, before, after })
    }
    const ability = async (actor, cause, citizen) => {
      await fixture(actor, `arrival-ability ${cause}`)
      const after = await inspectUntil(actor, state => state.ability?.done && !state.pending,
        'The ability future completes after the teleport')
      context.expect(after.ability.result === true && after.ability.allowed && !after.ability.failure
        && after.ability.arrivalsAtCompletion === 1,
        'The returned future succeeds only at an allowed actual destination', after.ability)
      context.expect(after.ability.submitted.world === NETHER && after.ability.completed.world === OVERWORLD,
        'Future completion follows the actual dimension transfer', after.ability)
      context.expect(inside(after, after.ability.completed) === citizen,
        citizen ? 'Allowed ability travel retains the town destination' : 'Denied ability travel completes outside all town chunks', after.ability)
      context.expect(after.events.filter(event => event.kind === 'arrival').length === 1,
        'Ability travel has exactly one actual arrival', after.events)
      const committed = after.events.filter(event => event.kind === 'teleport' && !event.cancelled)
      context.expect(committed.length === 1 && committed[0].cause === (cause === 'default' ? 'PLUGIN' : cause),
        'The committed ability teleport preserves its requested cause', committed)
      safeEvents(after)
      evidence.abilities.push({ actor: actor.bot.username, cause, after })
      return after
    }

    try {
      await context.step('Ordinary actors receive a town and the loaded respawn terrain', async () => {
        for (const [role, marker] of [['founder', 'f'], ['member', 'm'], ['attacker', 'a']]) {
          const actor = await context.connectActor(`AQAc${marker}${suffix}`)
          actors[role] = actor
          await actor.bot.waitForTicks(20)
          actor.bot.physicsEnabled = false
          const state = await fixture(actor, `arrival-prepare ${role}`)
          context.expect(!state.operator && !state.bypass, `${role} has no operator or Claims bypass`)
          evidence.actors[role] = actor.bot.username
        }
      })
      await context.step('An outsider respawns at a loaded outside fallback before safe relocation', () => respawn(actors.attacker, false))
      await context.step('The founder and member keep their own-town respawn', async () => {
        await respawn(actors.founder, true)
        await respawn(actors.member, true)
      })
      await context.step('Both ability teleport overloads report success only after a safe redirected arrival', async () => {
        await ability(actors.attacker, 'default', false)
        await ability(actors.attacker, 'ENDER_PEARL', false)
      })
      await context.step('A larger player redirects past a ceiling that fits a normal standing player', async () => {
        const setup = await fixture(actors.attacker, 'arrival-clearance normal')
        try {
          const normal = await ability(actors.attacker, 'default', false)
          context.expect(normal.bodyClear && normal.height < 2
            && normal.location.x === setup.location.x && normal.location.y === setup.location.y
            && normal.location.z === setup.location.z,
          'The normal player fits at the original destination below the low ceiling', normal)
          await fixture(actors.attacker, 'arrival-clearance large')
          const large = await ability(actors.attacker, 'default', false)
          context.expect(large.scale === 1.32 && large.height > 2.3 && large.bodyClear,
            'The enlarged player arrives with its full actual body clear of terrain', large)
          context.expect(large.location.x !== normal.location.x || large.location.y !== normal.location.y
            || large.location.z !== normal.location.z,
          'The enlarged body cannot reuse the two-block-high arrival space', { normal: normal.location, large: large.location })
          evidence.scaled = { setup, normal, large }
        } finally {
          await fixture(actors.attacker, 'arrival-clearance restore')
        }
      })
      await context.step('An allowed ability teleport still reaches the requested town location', () => ability(actors.founder, 'default', true))
      await context.step('Claims reload retains the town and actual native portal protection', async () => {
        const before = await snapshot(actors.founder)
        const reloaded = await fixture(actors.founder, 'portal-reload')
        context.expect(before.city.id === reloaded.city.id && reloaded.anchorMaterial === 'LODESTONE',
          'Configuration reload retains the town and physical anchor', reloaded)
        await fixture(actors.attacker, 'portal-enter stationary')
        const after = await inspectUntil(actors.attacker, state => !state.pending && state.location.world === OVERWORLD
          && state.events.some(event => event.kind === 'arrival'), 'A real portal traversal completes after reload')
        context.expect(after.location.x >= 4073 && after.location.x < 4074,
          'Reloaded protection skips the town and the portal frame crossing its boundary', after.location)
        context.expect(after.events.some(event => event.kind === 'portal' && event.cancelled),
          'The real native portal event passes through Claims protection', after.events)
        safeEvents(after)
        evidence.reload = { before, reloaded, after }
      })
      await context.step('No loaded safe respawn fallback disconnects before a client respawn', async () => {
        evidence.noFallback = []
        for (const site of ['blocked', 'claimed']) {
          const actor = actors.attacker
          let expectedKick
          let respawnPackets = 0
          let died = false
          const markDeath = () => { died = true }
          const countRespawn = () => { if (died) respawnPackets++ }
          actor.bot.on('death', markDeath)
          actor.bot._client.on('respawn', countRespawn)
          const listeners = actor.bot.listeners('kicked').map(listener => {
            const scoped = (...args) => {
              if (JSON.stringify(args[0]).includes(NO_FALLBACK)) expectedKick = args[0]
              else listener(...args)
            }
            actor.bot.off('kicked', listener)
            actor.bot.on('kicked', scoped)
            return { listener, scoped }
          })
          try {
            let history
            actors.attacker = await actor.reconnectAfter(async () => {
              const source = await fixture(actor, `arrival-respawn ${site}`)
              context.expect(source.sourceAllowed === (site === 'blocked') && !source.bypass && !source.operator,
                'The no-fallback case starts at the intended permission state without bypass', source)
              await context.waitUntil(() => Boolean(expectedKick), { label: 'Exact no-fallback rejection', timeoutMs: 10000 })
              await context.waitUntil(async () => {
                history = await fixture(actors.founder, `arrival-history ${actor.bot.username}`)
                return history.lifecycle.some(event => event.kind === 'quit')
              }, { label: 'Server completes the rejected player disconnect', timeoutMs: 5000 })
              evidence.noFallback.push(history)
              await fixture(actors.founder, 'arrival-restore')
            }, { timeoutMs: 20000 })
            actors.attacker.bot.physicsEnabled = false
            context.expect(Boolean(expectedKick) && respawnPackets === 0,
              'The exact Claims refusal disconnects before sending a respawn packet', { expectedKick, respawnPackets })
            context.expect(history.lifecycle.some(event => event.kind === 'kick' && !event.cancelled),
              'The server actually issues the no-fallback kick', history)
            context.expect(!history.lifecycle.some(event => ['arrival', 'postRespawn'].includes(event.kind)),
              'The refusal aborts actual respawn placement for either source permission state', history)
            const quit = history.lifecycle.find(event => event.kind === 'quit')
            context.expect(quit && JSON.stringify(quit.to) === JSON.stringify(history.source),
              'The player disconnects at the original source without an intermediate relocation', history)
            history.kick = expectedKick
            history.respawnPackets = respawnPackets
          } finally {
            actor.bot._client.off('respawn', countRespawn)
            actor.bot.off('death', markDeath)
            for (const { listener, scoped } of listeners) {
              actor.bot.off('kicked', scoped)
              actor.bot.on('kicked', listener)
            }
          }
        }
      })
      evidence.completed = true
      return evidence
    } finally {
      if (actors.founder) await fixture(actors.founder, 'arrival-restore')
      for (const actor of Object.values(actors)) {
        actor.bot.clearControlStates()
        actor.bot.physicsEnabled = false
        await fixture(actor, 'arrival-cleanup')
      }
    }
  },
}
