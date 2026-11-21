import { randomBytes } from 'node:crypto'
import { fixtureJson } from './fixture-json.mjs'
import { aimWhileUsingItem } from './aim-while-using.mjs'
import { synchronizePlayerInput } from './player-input.mjs'
import { synchronizePlayerVelocity } from './player-velocity.mjs'

const FLIGHT_TICKS = 50

function angleDegrees(a, b) {
    const lengths = Math.hypot(a.x, a.y, a.z) * Math.hypot(b.x, b.y, b.z)
    if (lengths <= 1e-9) return 0
    const cosine = (a.x * b.x + a.y * b.y + a.z * b.z) / lengths
    return Math.acos(Math.max(-1, Math.min(1, cosine))) * 180 / Math.PI
}

// Replays the flight the way a vanilla client does: it moves the arrow by its last known velocity every tick and
// takes each velocity the server sends, so a server that steers without telling the client draws a straight arrow.
function replay(samples, origin, gravity) {
    let position = { ...origin }
    let velocity = samples[0]?.velocity ?? { x: 0, y: 0, z: 0 }
    const path = [{ ...position }]
    for (let tick = 1; tick <= FLIGHT_TICKS; tick++) {
        const sent = samples.find(sample => sample.tick === tick)
        if (sent) velocity = sent.velocity
        position = { x: position.x + velocity.x, y: position.y + velocity.y, z: position.z + velocity.z }
        velocity = { x: velocity.x * 0.99, y: velocity.y * 0.99 - (gravity ? 0.05 : 0), z: velocity.z * 0.99 }
        path.push({ ...position })
    }
    return path
}

export default {
    name: 'adapt-heartseeker-flight',
    description: 'Shoot off-target at a Heartseeker lock and measure what the shooter\'s client is told about the arrow each tick.',
    async run(context) {
        const level = Number(context.options.command ?? '5')
        if (!Number.isInteger(level) || level < 0) throw new Error('Pass --command <level>, 0 for the unlearned control')
        const suffix = randomBytes(4).toString('hex')
        const actor = await context.connectActor(`AQAh${suffix}`)
        const opponent = await context.connectActor(`AQAi${suffix}`)
        const stops = [actor, opponent].flatMap(player => [synchronizePlayerInput(player.bot), synchronizePlayerVelocity(player.bot)])
        const evidence = context.report.heartseekerFlight = { level, shots: [] }
        let sequence = 0
        let setup = false
        const snapshot = () => {
            const token = `h${++sequence}`
            return fixtureJson(context, `/adaptqa snapshot ${actor.bot.username} ${token}`, `SNAPSHOT ${token}`)
        }
        try {
            await context.command(`/adaptqa setup ${actor.bot.username} ${opponent.bot.username}`, /^ADAPT_QA SETUP /, 60000)
            setup = true
            await context.waitUntil(async () => (await snapshot()).chronos.serverTick > 0, { label: 'snapshot', timeoutMs: 5000 })
            await actor.bot.waitForTicks(70)
            await context.command(`/adaptqa learn ranged-heartseeker ${level}`, /ADAPT_QA LEARN /, 5000)
            for (let shot = 0; shot < 2; shot++) {
                await context.step(`heartseeker flight level ${level} shot ${shot + 1}`, async () => {
                    actor.bot.clearControlStates()
                    await context.command('/adaptqa stage projectile-heart', /^ADAPT_QA STAGE projectile-heart$/, 10000)
                    await actor.bot.waitForTicks(8)
                    const bow = actor.bot.inventory.items().find(item => item.name === 'bow')
                    context.expect(Boolean(bow), 'Fixture provides a bow')
                    await actor.bot.equip(bow, 'hand')
                    await actor.bot.waitForTicks(4)
                    const before = await snapshot()
                    const state = before.projectile.target
                    await context.waitUntil(() => Boolean(actor.bot.entities[state.entityId]), { label: 'cow visible', timeoutMs: 5000 })
                    const cow = actor.bot.entities[state.entityId]
                    const aim = cow.position.offset(0, 0.8, 0)
                    const samples = []
                    let arrowId
                    let releasedTick = 0
                    let tick = 0
                    const onTick = () => { tick++ }
                    const onSpawn = packet => {
                        const entity = actor.bot.entities[packet.entityId]
                        if (arrowId === undefined && releasedTick > 0 && entity?.name === 'arrow') {
                            arrowId = packet.entityId
                            samples.push({ tick: 0, kind: 'spawn', velocity: { ...packet.velocity }, position: { x: packet.x, y: packet.y, z: packet.z } })
                        }
                    }
                    const onVelocity = packet => {
                        if (packet.entityId === arrowId) samples.push({ tick: tick - releasedTick, kind: 'velocity', velocity: { ...packet.velocity } })
                    }
                    actor.bot.on('physicsTick', onTick)
                    actor.bot._client.on('spawn_entity', onSpawn)
                    actor.bot._client.on('entity_velocity', onVelocity)
                    try {
                        await actor.bot.lookAt(aim, true)
                        actor.bot.activateItem()
                        await actor.bot.waitForTicks(25)
                        await aimWhileUsingItem(context, actor.bot, cow.position.offset(0, 0.8, 3.5), async () => (await snapshot()).location)
                        releasedTick = tick
                        actor.bot.deactivateItem()
                        await actor.bot.waitForTicks(FLIGHT_TICKS)
                    } finally {
                        actor.bot.off('physicsTick', onTick)
                        actor.bot._client.off('spawn_entity', onSpawn)
                        actor.bot._client.off('entity_velocity', onVelocity)
                        actor.bot.deactivateItem()
                    }
                    const after = await snapshot()
                    context.expect(samples.length > 0, 'Shooter client saw the arrow spawn')
                    const spawn = samples[0]
                    const turns = samples.filter(sample => sample.kind === 'velocity')
                    const maxTurn = turns.reduce((max, sample) => Math.max(max, angleDegrees(spawn.velocity, sample.velocity)), 0)
                    const path = replay(samples, spawn.position, level <= 0)
                    const target = { x: aim.x, y: aim.y, z: aim.z }
                    const closest = path.reduce((min, point) => Math.min(min, Math.hypot(point.x - target.x, point.y - target.y, point.z - target.z)), Infinity)
                    const result = {
                        shot: shot + 1,
                        damaged: after.projectile.target.health < before.projectile.target.health,
                        healthBefore: before.projectile.target.health,
                        healthAfter: after.projectile.target.health,
                        velocityPackets: turns.length,
                        maxTurnDegrees: Number(maxTurn.toFixed(2)),
                        clientClosestApproach: Number(closest.toFixed(2)),
                        spawnVelocity: spawn.velocity,
                        firstTurns: turns.slice(0, 6).map(sample => ({ tick: sample.tick, angle: Number(angleDegrees(spawn.velocity, sample.velocity).toFixed(2)) })),
                    }
                    evidence.shots.push(result)
                    process.stderr.write(`HEARTSEEKER_FLIGHT ${JSON.stringify(result)}\n`)
                    if (level > 0) {
                        context.expect(result.damaged, 'Locked off-target arrow damages the cow')
                        context.expect(result.maxTurnDegrees > 10, 'Shooter client is told the arrow turns toward the lock')
                        context.expect(result.clientClosestApproach < 2.5, 'Client-replayed flight reaches the cow')
                    } else {
                        context.expect(!result.damaged, 'Unlearned off-target arrow misses the cow')
                    }
                })
                await actor.bot.waitForTicks(30)
            }
        } finally {
            for (const player of [actor, opponent]) {
                player.bot.clearControlStates()
                player.bot.deactivateItem()
            }
            if (setup && !context.signal.aborted) await context.command('/adaptqa cleanup', /^ADAPT_QA CLEANUP$/, 15000)
            for (const stop of stops) stop()
        }
    },
}
