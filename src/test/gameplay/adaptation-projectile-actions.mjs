import { observeParticlePackets } from './feedback.mjs'

function point(actor, x, y, z) {
    return actor.bot.entity.position.clone().set(x, y, z)
}

function count(actor, name) {
    return actor.bot.inventory.slots.reduce((sum, item) => sum + (item?.name === name ? item.count : 0), 0)
}

function prepare(item, sneak = false) {
    return async ({ actor, equip }) => {
        actor.bot.clearControlStates()
        if (item) await equip(item)
        actor.bot.setControlState('sneak', sneak)
        await actor.bot.waitForTicks(5)
    }
}

async function target(input) {
    const state = (await input.snapshot()).projectile.target
    input.context.expect(Boolean(state), 'Projectile target exists')
    await input.context.waitUntil(() => Boolean(input.actor.bot.entities[state.entityId]), {
        label: 'projectile target is client-visible', timeoutMs: 5000,
    })
    return input.actor.bot.entities[state.entityId]
}

async function releaseArrow(input, aim, offsetAim) {
    await input.actor.bot.lookAt(aim, true)
    input.actor.bot.activateItem()
    await input.actor.bot.waitForTicks(25)
    if (offsetAim) {
        await input.actor.bot.lookAt(offsetAim, true)
        await input.actor.bot.waitForTicks(2)
    }
    input.actor.bot.deactivateItem()
}

function procTrigger(kind) {
    return async input => {
        const { actor, context, snapshot } = input
        const before = await snapshot()
        const initialArrows = count(actor, 'arrow')
        const spawned = new Set()
        const onSpawn = entity => { if (entity.name === 'arrow') spawned.add(entity.id) }
        actor.bot.on('entitySpawn', onSpawn)
        let after = before
        let success = false
        let trials = 0
        try {
            while (!success && trials < 16) {
                const victim = await target(input)
                const health = after.projectile.target.health
                await releaseArrow(input, victim.position.offset(0, 0.8, 0))
                await context.waitUntil(async () => {
                    after = await snapshot()
                    return after.projectile.target.health < health
                }, { label: `real arrow hit ${trials + 1}`, timeoutMs: 5000, intervalMs: 50 })
                await actor.bot.waitForTicks(3)
                after = await snapshot()
                trials++
                if (kind === 'levitation') success = after.projectile.target.effects['minecraft:levitation'] !== undefined
                else if (kind === 'pin') success = after.projectile.target.attributes['minecraft:movement_speed'] < before.projectile.target.attributes['minecraft:movement_speed']
                else success = count(actor, 'arrow') > initialArrows - trials
                context.expect(after.projectile.target.health > 0, 'Repeated shot target remains alive')
            }
            context.expect(spawned.size >= trials, 'Every trial launched an actual client-visible arrow')
            const probability = kind === 'levitation' ? 0.7 : kind === 'pin' ? 0.54 : 0.8
            return { before, after, trials, success, arrowsConsumed: initialArrows - count(actor, 'arrow'), noProcRiskAtLimit: (1 - probability) ** 16 }
        } finally {
            actor.bot.deactivateItem()
            actor.bot.off('entitySpawn', onSpawn)
        }
    }
}

function verifyProc({ context, unlearned, active }) {
    context.expect(unlearned.trials === 16 && !unlearned.success, 'All sixteen unlearned real hits lack the adaptation outcome')
    context.expect(active.success, `Learned natural shots must produce an observed proc within sixteen attempts; no-proc risk ${active.noProcRiskAtLimit}`)
    return report(['sixteen unlearned arrows lack the adaptation outcome', 'bounded learned natural arrow trials produce the actual effect or refund'], unlearned, active)
}

async function heartseeker(input) {
    const victim = await target(input)
    const before = await input.snapshot()
    await releaseArrow(input, victim.position.offset(0, 0.8, 0), victim.position.offset(0, 0.8, 3.5))
    await input.actor.bot.waitForTicks(45)
    return { before, after: await input.snapshot() }
}

async function ricochet(input) {
    const { actor, snapshot } = input
    const before = await snapshot()
    const spawned = new Set()
    const onSpawn = entity => { if (entity.name === 'arrow') spawned.add(entity.id) }
    actor.bot.on('entitySpawn', onSpawn)
    try {
        await releaseArrow(input, point(actor, 6.5, 101.7, 0.5))
        await actor.bot.waitForTicks(45)
        return { before, after: await snapshot(), arrowEntities: spawned.size }
    } finally {
        actor.bot.off('entitySpawn', onSpawn)
    }
}

function glowing(actor, id) {
    return Boolean((actor.bot.entities[id]?.metadata?.[0] ?? 0) & 0x40)
}

async function preview(input) {
    const { actor, opponent, snapshot } = input
    const victim = await target(input)
    const before = await snapshot()
    const arrowsBefore = count(actor, 'arrow')
    const actorArc = []
    const observerArc = []
    const collect = destination => packet => {
        if (packet.name === 'dust' && packet.position.x > 3 && packet.position.x < 9) destination.push(packet.position)
    }
    const stopActor = observeParticlePackets(actor.bot, collect(actorArc))
    const stopObserver = observeParticlePackets(opponent.bot, collect(observerArc))
    try {
        await actor.bot.lookAt(victim.position.offset(0, 0.9, 0), true)
        actor.bot.activateItem()
        await actor.bot.waitForTicks(40)
        const during = { shooterGlow: glowing(actor, victim.id), observerGlow: glowing(opponent, victim.id), state: await snapshot() }
        actor.bot.setQuickBarSlot(8)
        await actor.bot.waitForTicks(10)
        actor.bot.deactivateItem()
        return { before, during, actorArc: actorArc.length, observerArc: observerArc.length, glowAfter: glowing(actor, victim.id), arrowsConsumed: arrowsBefore - count(actor, 'arrow') }
    } finally {
        actor.bot.deactivateItem()
        stopActor()
        stopObserver()
    }
}

async function webshot(input) {
    const victim = await target(input)
    await input.actor.bot.lookAt(victim.position.offset(0, 0.8, 0), true)
    input.actor.bot.activateItem()
    input.actor.bot.deactivateItem()
    await input.actor.bot.waitForTicks(15)
    const after = await input.snapshot()
    await input.actor.bot.waitForTicks(115)
    return { after, expired: await input.snapshot(), remaining: count(input.actor, 'snowball') }
}

async function bloodTrail(input) {
    const victim = await target(input)
    const before = await input.snapshot()
    const shooterDisplays = new Set()
    const observerDisplays = new Set()
    const observe = destination => entity => { if (entity.name === 'block_display') destination.add(entity.id) }
    const ownListener = observe(shooterDisplays)
    const observerListener = observe(observerDisplays)
    input.actor.bot.on('entitySpawn', ownListener)
    input.opponent.bot.on('entitySpawn', observerListener)
    try {
        await input.actor.bot.lookAt(victim.position.offset(0, 0.7, 0), true)
        input.actor.bot.attack(victim)
        await input.actor.bot.waitForTicks(45)
        return { before, after: await input.snapshot(), shooterDisplays: shooterDisplays.size, observerDisplays: observerDisplays.size }
    } finally {
        input.actor.bot.off('entitySpawn', ownListener)
        input.opponent.bot.off('entitySpawn', observerListener)
    }
}

async function snare(input) {
    const before = await input.snapshot()
    const hooks = count(input.actor, 'tripwire_hook')
    const floor = input.actor.bot.blockAt(point(input.actor, 3, 99, 0))
    input.context.expect(Boolean(floor), 'Snare floor is visible')
    await input.actor.bot.activateBlock(floor, point(input.actor, 0, 1, 0))
    await input.actor.bot.waitForTicks(20)
    return { before, after: await input.snapshot(), used: hooks - count(input.actor, 'tripwire_hook') }
}

async function hunterSpeed(input) {
    const { actor, opponent, context, snapshot } = input
    await actor.bot.lookAt(actor.bot.entity.position.offset(0, 1.6, 15), true)
    const before = await snapshot()
    const victim = opponent.bot.entities[actor.bot.entity.id]
    context.expect(Boolean(victim), 'Speed-burst defender is visible')
    await opponent.bot.lookAt(victim.position.offset(0, 1, 0), true)
    opponent.bot.attack(victim)
    await actor.bot.waitForTicks(12)
    const idleStart = actor.bot.entity.position.clone()
    await actor.bot.waitForTicks(8)
    const idleDrift = actor.bot.entity.position.distanceTo(idleStart)
    const start = actor.bot.entity.position.clone()
    const velocities = []
    const onVelocity = packet => {
        if (packet.entityId === actor.bot.entity.id) velocities.push({ velocity: packet.velocity, forward: actor.bot.getControlState('forward') })
    }
    actor.bot._client.on('entity_velocity', onVelocity)
    actor.bot.setControlState('forward', true)
    try {
        await actor.bot.waitForTicks(60)
        const result = { before, after: await snapshot(), distance: actor.bot.entity.position.z - start.z, idleDrift, velocities, activeImpulses: velocities.length }
        if (before.learned['hunter-speed'] > 0) {
            await context.command(`/adapt clear adaptations player=${actor.bot.username}`, /Cleared adaptations/, 5000)
            await actor.bot.waitForTicks(4)
            const clearedStart = actor.bot.entity.position.clone()
            const clearedPackets = velocities.length
            await actor.bot.waitForTicks(60)
            result.clearedDistance = actor.bot.entity.position.z - clearedStart.z
            result.clearedImpulses = velocities.length - clearedPackets
            context.expect((await snapshot()).learned['hunter-speed'] === 0, 'Clear adaptations removes Hunter Speed while moving')
            actor.bot.clearControlStates()
            await context.command('/adaptqa stage projectile-speed', /^ADAPT_QA STAGE projectile-speed$/, 10000)
            await context.command(`/adaptqa learn hunter-speed ${before.learned['hunter-speed']}`, /^ADAPT_QA LEARN hunter-speed /, 5000)
            await actor.bot.waitForTicks(6)
            await actor.bot.lookAt(actor.bot.entity.position.offset(0, 1.6, 15), true)
            const secondVictim = opponent.bot.entities[actor.bot.entity.id]
            context.expect(Boolean(secondVictim), 'Relearned speed-burst defender is visible')
            await opponent.bot.lookAt(secondVictim.position.offset(0, 1, 0), true)
            opponent.bot.attack(secondVictim)
            actor.bot.setControlState('forward', true)
            await actor.bot.waitForTicks(12)
            context.expect((await snapshot()).stats['hunter.speed.activations'] > result.after.stats['hunter.speed.activations'], 'A fresh real hit starts another burst before unlearning')
            await context.command(`/adapt determine adaptationTarget=hunter:hunter-speed assign=false force=true level=1 player=${actor.bot.username}`, /Unlearned .*now at level 0/, 5000)
            await actor.bot.waitForTicks(4)
            const unlearnedStart = actor.bot.entity.position.clone()
            const unlearnedPackets = velocities.length
            await actor.bot.waitForTicks(60)
            result.unlearnedDistance = actor.bot.entity.position.z - unlearnedStart.z
            result.unlearnedImpulses = velocities.length - unlearnedPackets
            context.expect((await snapshot()).learned['hunter-speed'] === 0, 'Unlearning removes Hunter Speed while moving')
        }
        return result
    } finally {
        actor.bot._client.off('entity_velocity', onVelocity)
        actor.bot.clearControlStates()
    }
}

async function trophy(input) {
    const { actor, context, snapshot } = input
    actor.bot.setQuickBarSlot(8)
    await actor.bot.waitForTicks(3)
    context.expect(!actor.bot.heldItem, 'Each trophy trial uses one bare-handed hit without sword sweep damage')
    const initial = await snapshot()
    let trials = 0
    let powder = 0
    let after = initial
    for (const state of Object.values(initial.projectile.trophies)) {
        if (powder > 0) break
        await context.waitUntil(() => Boolean(actor.bot.entities[state.entityId]), { label: 'precision kill target visible', timeoutMs: 5000 })
        const victim = actor.bot.entities[state.entityId]
        await actor.bot.waitForTicks(14)
        await actor.bot.lookAt(victim.position.offset(0, 0.8, 0), true)
        actor.bot.attack(victim)
        await context.waitUntil(() => !actor.bot.entities[state.entityId], { label: 'natural sneak fist kill removes blaze', timeoutMs: 5000 })
        await actor.bot.waitForTicks(4)
        trials++
        after = await snapshot()
        powder = (after.projectile.drops.BLAZE_POWDER ?? 0) + count(actor, 'blaze_powder')
    }
    return { trials, powder, after, noTrophyRiskAtLimit: 0.56 ** 20 }
}

async function smash(input) {
    const { actor, context, snapshot } = input
    const before = await snapshot()
    actor.bot.setControlState('jump', true)
    try {
        await actor.bot.waitForTicks(4)
        actor.bot.setControlState('jump', false)
        context.expect(!actor.bot.entity.onGround, 'Ground smash arms during an actual airborne jump')
        actor.bot.setControlState('sneak', true)
        await context.waitUntil(() => actor.bot.entity.onGround, { label: 'natural smash landing', timeoutMs: 4000, intervalMs: 20 })
        await actor.bot.waitForTicks(12)
        return { before, after: await snapshot() }
    } finally {
        actor.bot.clearControlStates()
    }
}

async function throwingAxe(input) {
    const victim = await target(input)
    const before = await input.snapshot()
    input.context.expect(input.actor.bot.entity.position.distanceTo(victim.position) > 5, 'Throw target is outside ordinary melee reach')
    await input.actor.bot.lookAt(victim.position.offset(0, 0.8, 0), true)
    input.actor.bot.swingArm('right')
    await input.actor.bot.waitForTicks(100)
    return { before, after: await input.snapshot(), axes: count(input.actor, 'iron_axe') }
}

async function logswap(input) {
    const { actor, context } = input
    const table = actor.bot.blockAt(point(actor, 2, 100, 2))
    context.expect(table?.name === 'crafting_table', 'Log conversion crafting table exists')
    await actor.bot.activateBlock(table)
    await context.waitUntil(() => actor.bot.currentWindow?.type === 'minecraft:crafting', { label: 'log conversion crafting grid', timeoutMs: 5000 })
    const window = actor.bot.currentWindow
    try {
        for (let slot = 1; slot <= 9; slot++) {
            const name = slot === 9 ? 'oak_sapling' : 'birch_log'
            const item = window.slots.slice(window.inventoryStart).find(candidate => candidate?.name === name)
            context.expect(Boolean(item), 'Log conversion ingredient exists')
            const source = item.slot
            const remainder = item.count > 1
            await actor.bot.clickWindow(source, 0, 0)
            await actor.bot.waitForTicks(2)
            await actor.bot.clickWindow(slot, 1, 0)
            await actor.bot.waitForTicks(2)
            if (remainder) {
                await actor.bot.clickWindow(source, 0, 0)
                await actor.bot.waitForTicks(2)
            }
        }
        await context.waitUntil(() => window.slots[0]?.name === 'oak_log', { label: 'eight oak log recipe preview', timeoutMs: 5000 })
        await actor.bot.clickWindow(0, 0, 1)
        await actor.bot.waitForTicks(8)
    } finally {
        actor.bot.closeWindow(window)
    }
    await actor.bot.waitForTicks(4)
    return { oak: count(actor, 'oak_log'), birch: count(actor, 'birch_log'), sapling: count(actor, 'oak_sapling') }
}

function report(assertions, unlearned, active) {
    return { assertions, measurements: { unlearned, active } }
}

export const projectileBehaviorCases = new Map([
    ['ranged-floaters', {
        stage: 'projectile-procs', prepare: prepare('bow'), trigger: procTrigger('levitation'), verify: verifyProc, negativeWindowTicks: 10,
        description: 'bounded real arrow hits apply actual levitation to the victim',
        sound: 'minecraft:entity.shulker.shoot', soundVolume: 0.6, soundPitch: 1.45, particle: 'end_rod',
    }],
    ['ranged-pinning-shot', {
        stage: 'projectile-procs', prepare: prepare('bow'), trigger: procTrigger('pin'), verify: verifyProc, negativeWindowTicks: 10,
        description: 'bounded real arrow hits reduce the actual victim movement attribute',
        sound: 'minecraft:block.bell.use', soundVolume: 1.1, soundPitch: 0.48, particle: 'dust',
    }],
    ['ranged-recovery', {
        stage: 'projectile-procs', prepare: prepare('bow'), trigger: procTrigger('recovery'), verify: verifyProc, negativeWindowTicks: 10,
        description: 'bounded real arrow hits refund ammunition to the shooter',
        sound: 'minecraft:entity.item.pickup', soundVolume: 0.5, soundPitch: 1.4, particle: 'dust',
    }],
    ['ranged-heartseeker', {
        stage: 'projectile-heart', prepare: prepare('bow'), trigger: heartseeker, negativeWindowTicks: 10,
        description: 'locking a cow then releasing off-target still guides the arrow into that cow',
        sound: 'minecraft:entity.arrow.hit_player', soundVolume: 0.8, soundPitch: 1.2, particle: 'dust',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.after.projectile.target.health === unlearned.before.projectile.target.health, 'Ordinary off-target arrow misses the cow')
            context.expect(active.after.projectile.target.health < active.before.projectile.target.health, 'Learned locked off-target arrow damages the actual cow')
            return report(['unlearned off-target release misses', 'learned locked off-target release guides into and damages the cow'], unlearned, active)
        },
    }],
    ['ranged-ricochet-bolt', {
        stage: 'projectile-bounce', prepare: prepare('bow'), trigger: ricochet, negativeWindowTicks: 10,
        description: 'a real wall collision creates a replacement arrow that makes another physical impact',
        sound: 'minecraft:block.anvil.hit', soundVolume: 0.85, soundPitch: 1.27, particle: 'electric_spark',
        async verify({ context, unlearned, active }) {
            const unique = state => new Set(state.after.projectile.impacts.map(impact => impact.entityId)).size
            context.expect(unlearned.arrowEntities === 1 && unique(unlearned) === 1, 'Unlearned shot has one physical arrow and impact')
            context.expect(active.arrowEntities > 1 && unique(active) > 1, 'Learned collision creates additional client-visible arrows that also physically collide')
            return report(['ordinary arrow stops at its first collision', 'learned wall collision launches a replacement that makes a second physical impact'], unlearned, active)
        },
    }],
    ['ranged-trajectory-sight', {
        stage: 'projectile-preview', prepare: prepare('bow'), trigger: preview, negativeWindowTicks: 10,
        description: 'drawing a bow sends a private predicted arc and target glow, then removes glow on cancellation',
        sound: 'minecraft:block.note_block.hat', soundVolume: 0.35, soundPitch: 1.4, particle: 'dust',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.actorArc === 0 && !unlearned.during.shooterGlow, 'Unlearned bow draw has no predictive arc or glow')
            context.expect(active.actorArc > 10 && active.observerArc === 0, 'Learned predicted arc reaches beyond the bow and is private to its owner')
            context.expect(active.during.shooterGlow && !active.during.observerGlow && !active.during.state.projectile.target.glowing, 'Predicted victim glow is client-specific rather than a global entity mutation')
            context.expect(!active.glowAfter && active.arrowsConsumed === 0, 'Cancelling the draw removes predictive glow without firing ammunition')
            return report(['unlearned bow draw has no predicted arc or target glow', 'learned draw privately previews the trajectory and victim', 'cancelling clears glow and consumes no arrow'], unlearned, active)
        },
    }],
    ['ranged-webshot', {
        stage: 'projectile-web', prepare: prepare('snowball'), trigger: webshot, negativeWindowTicks: 10,
        description: 'a naturally thrown prepared web snare creates real temporary cobweb blocks',
        sound: 'minecraft:block.wool.place', soundVolume: 0.9, soundPitch: 0.7, particle: 'cloud',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.after.projectile.webs === 0, 'Unlearned prepared snowball creates no cobwebs')
            context.expect(active.after.projectile.webs > 0, 'Learned snowball impact creates actual cobweb blocks')
            context.expect(active.expired.projectile.webs === 0, 'Temporary learned cobwebs expire naturally')
            context.expect(unlearned.remaining === 0 && active.remaining === 0, 'Both trials consume the real thrown snare')
            return report(['unlearned throw creates no cobwebs', 'learned impact creates actual cobweb blocks and later removes them'], unlearned, active)
        },
    }],
    ['hunter-blood-trail', {
        stage: 'projectile-blood', trigger: bloodTrail, negativeWindowTicks: 10,
        description: 'a naturally wounded moving cow leaves private client-visible trail segments',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.after.projectile.target.health < unlearned.before.projectile.target.health && unlearned.shooterDisplays === 0, 'Control hit wounds the cow without creating trail displays')
            context.expect(active.after.projectile.target.health < active.before.projectile.target.health && active.shooterDisplays > 0, 'Learned real wound produces actual client display segments')
            context.expect(active.observerDisplays === 0, 'Blood trail display segments are private to the hunter')
            return report(['ordinary cow wound emits no trail displays', 'learned moving wound creates display segments only for its hunter'], unlearned, active)
        },
    }],
    ['hunter-snare-line', {
        stage: 'projectile-snare', prepare: prepare('tripwire_hook'), trigger: snare, negativeWindowTicks: 10,
        description: 'naturally placing a prepared snare consumes it and roots a nearby hostile mob',
        sound: 'minecraft:block.tripwire.click_on', soundVolume: 0.8, soundPitch: 0.8, particle: 'dust',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.used === 0 && unlearned.after.projectile.target.attributes['minecraft:movement_speed'] === unlearned.before.projectile.target.attributes['minecraft:movement_speed'], 'Unlearned snare neither consumes the item nor roots target')
            context.expect(active.used === 1 && active.after.projectile.target.attributes['minecraft:movement_speed'] < active.before.projectile.target.attributes['minecraft:movement_speed'], 'Learned actual snare consumes one item and reduces hostile movement attribute')
            return report(['unlearned prepared snare remains unused', 'learned snare placement consumes its item and roots the actual hostile'], unlearned, active)
        },
    }],
    ['hunter-speed', {
        stage: 'projectile-speed', trigger: hunterSpeed, negativeWindowTicks: 10,
        description: 'a real opponent hit grants faster physical movement with a hunger penalty',
        sound: 'minecraft:entity.ender_dragon.flap', soundVolume: 0.5, soundPitch: 1.5, particle: 'cloud',
        async verify({ context, unlearned, active }) {
            context.expect(active.activeImpulses > 0 && unlearned.activeImpulses === 0, 'Only the learned burst applies server velocity impulses while holding forward')
            context.expect(active.clearedImpulses === 0 && active.unlearnedImpulses === 0, 'Clear and unlearn both stop server burst impulses while forward remains held')
            context.expect(active.clearedDistance >= unlearned.distance * 0.9 && active.unlearnedDistance >= unlearned.distance * 0.9, 'Ordinary forward walking remains available after clearing and unlearning')
            context.expect(active.distance > unlearned.distance + 0.4, 'Learned defender moves farther during the same real forward-input window')
            context.expect(active.after.effects.some(entry => entry.type === 'minecraft:hunger') && !unlearned.after.effects.some(entry => entry.type === 'minecraft:hunger'), 'Only learned speed burst incurs the actual hunger penalty')
            context.expect(active.idleDrift < 0.25 && unlearned.idleDrift < 0.25, 'Knockback settles without movement keys instead of driving a speed burst')
            context.expect(active.clearedDistance <= unlearned.distance + 0.4, 'Clearing adaptations returns an active burst to ordinary walking speed')
            context.expect(active.unlearnedDistance <= unlearned.distance + 0.4, 'Unlearning returns a newly activated burst to ordinary walking speed')
            return report(['unlearned hit establishes physical walking distance', 'learned hit grants faster real movement and hunger', 'neutral input does not sustain knockback', 'clear adaptations and unlearning both stop active bursts'], unlearned, active)
        },
    }],
    ['hunter-trophy-skinner', {
        stage: 'projectile-trophies', prepare: prepare(undefined, true), trigger: trophy, negativeWindowTicks: 10,
        description: 'bounded natural sneak kills produce blaze powder that vanilla blazes never drop',
        sound: 'minecraft:entity.wolf.shake', soundVolume: 0.55, soundPitch: 1.35, particle: 'happy_villager',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.trials === 20 && unlearned.powder === 0, 'Twenty ordinary precision blaze kills produce no blaze powder')
            context.expect(active.powder > 0, `Learned precision kills must produce actual trophy powder within twenty trials; no-trophy risk ${active.noTrophyRiskAtLimit}`)
            return report(['twenty unlearned sneak kills yield no Adapt-only blaze powder', 'bounded learned sneak kills yield actual trophy powder'], unlearned, active)
        },
    }],
    ['axe-ground-smash', {
        stage: 'projectile-smash', prepare: prepare('iron_axe'), trigger: smash, negativeWindowTicks: 10,
        description: 'sneaking during a real axe-held jump damages a nearby cow on landing',
        sound: 'minecraft:entity.zombie.attack_iron_door', soundVolume: 0.6, soundPitch: 0.4, particle: 'dust',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.after.projectile.target.health === unlearned.before.projectile.target.health, 'Ordinary sneak landing causes no remote cow damage')
            context.expect(active.after.projectile.target.health < active.before.projectile.target.health, 'Learned landing damages actual nearby cow without a melee strike')
            return report(['unlearned sneak landing leaves nearby cow unharmed', 'learned airborne arming and landing deal actual area damage'], unlearned, active)
        },
    }],
    ['axe-throwing-axe', {
        stage: 'projectile-axe', prepare: prepare('iron_axe'), trigger: throwingAxe, negativeWindowTicks: 10,
        description: 'an air swing throws an axe into a distant cow and returns the damaged tool',
        sound: 'minecraft:item.trident.throw', soundVolume: 0.8, soundPitch: 1.2, particle: 'crit',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.after.projectile.target.health === unlearned.before.projectile.target.health && unlearned.after.projectile.heldDamage === 0, 'Unlearned air swing cannot hit the distant cow or damage its axe')
            context.expect(active.after.projectile.target.health < active.before.projectile.target.health, 'Learned thrown axe removes actual distant target health')
            context.expect(active.axes === 1 && active.after.projectile.heldDamage === 3, 'Maximum-level throw returns the actual axe with its configured durability cost')
            return report(['unlearned air swing cannot strike beyond melee range', 'learned throw damages the distant cow and returns one axe after paying durability'], unlearned, active)
        },
    }],
    ['axe-logswap', {
        stage: 'projectile-logswap', trigger: logswap, negativeWindowTicks: 10,
        description: 'actual crafting converts eight birch logs and one oak sapling into eight oak logs',
        sound: 'minecraft:block.sweet_berry_bush.pick_berries', soundVolume: 0.6, soundPitch: 1.2, particle: 'happy_villager',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.oak === 0 && unlearned.birch === 8 && unlearned.sapling === 1, 'Denied unlearned recipe preserves all ingredients')
            context.expect(active.oak === 8 && active.birch === 0 && active.sapling === 0, 'Learned custom recipe consumes exact ingredients and yields eight oak logs')
            return report(['unlearned log conversion is denied without ingredient loss', 'learned actual crafting conserves eight logs while consuming one sapling'], unlearned, active)
        },
    }],
])
