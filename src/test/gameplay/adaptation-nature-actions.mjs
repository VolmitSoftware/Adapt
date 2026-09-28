import { decodeSoundPacket } from './feedback.mjs'

function result(assertions, unlearned, active) {
    return { assertions, measurements: { unlearned, active } }
}

function stat(state, name) {
    return state.nature.stats[name] ?? 0
}

function buffed(before, after) {
    return after.effects.length > before.effects.length
        || after.attributes['minecraft:movement_speed'] > before.attributes['minecraft:movement_speed']
        || after.attributes['minecraft:jump_strength'] > before.attributes['minecraft:jump_strength']
}

async function prepare(input, item, attacker = false) {
    input.actor.bot.clearControlStates()
    input.opponent.bot.clearControlStates()
    if (item) await input.equip(item, attacker ? input.opponent : input.actor)
    await input.actor.bot.look(0, 0, true)
    await input.actor.bot.waitForTicks(16)
}

async function hazard(input, leech) {
    const before = await input.snapshot()
    input.actor.bot.setControlState('forward', true)
    await input.actor.bot.waitForTicks(6)
    input.actor.bot.clearControlStates()
    let after
    let intervals = 0
    for (; intervals < 20; intervals++) {
        await input.actor.bot.waitForTicks(10)
        after = await input.snapshot()
        if (leech && after.effects.some(effect => effect.type === 'minecraft:regeneration')) break
    }
    return { before: { health: before.health, food: before.food, negated: stat(before, 'nether.fire-resist.negated') },
        after: { health: after.health, food: after.food, effects: after.effects, negated: stat(after, 'nether.fire-resist.negated') }, intervals }
}

async function pact(input) {
    const before = await input.snapshot()
    let after = before
    let hits = 0
    for (; hits < 20; hits++) {
        const target = input.opponent.bot.entities[input.actor.bot.entity.id]
        input.context.expect(Boolean(target), 'Blood Pact defender is visible to the ordinary sword attacker')
        await input.opponent.bot.lookAt(target.position.offset(0, 1, 0), true)
        input.opponent.bot.attack(target)
        await input.actor.bot.waitForTicks(16)
        after = await input.snapshot()
        input.context.expect(after.health < before.health, 'Blood Pact receives actual health damage')
        if (buffed(before, after)) break
    }
    return { hits: Math.min(20, hits + 1), healthLost: before.health - after.health, buffed: buffed(before, after),
        effects: after.effects, attributes: after.attributes, sacrificed: stat(after, 'tragoul.blood-pact.health-sacrificed') - stat(before, 'tragoul.blood-pact.health-sacrificed') }
}

async function guardian(input) {
    const initial = await input.snapshot()
    let attempts = 0
    let latest
    for (; attempts < 20; attempts++) {
        const before = await input.snapshot()
        const target = input.opponent.bot.entities[input.actor.bot.entity.id]
        input.context.expect(Boolean(target), 'Guardian owner is visible to the bow shooter')
        await input.opponent.bot.lookAt(target.position.offset(0, 1, 0), true)
        input.opponent.bot.activateItem()
        await input.opponent.bot.waitForTicks(12)
        input.opponent.bot.deactivateItem()
        await input.actor.bot.waitForTicks(15)
        const after = await input.snapshot()
        latest = { ownerDamage: before.health - after.health, petDamage: before.nature.targets[0].health - after.nature.targets[0].health }
        input.context.expect(latest.ownerDamage > 0 || latest.petDamage > 0, 'Natural arrow damages either the owner or intercepting pet')
        if (latest.petDamage > 0) break
    }
    const after = await input.snapshot()
    return { attempts: Math.min(20, attempts + 1), latest, ownerDamage: initial.health - after.health,
        petDamage: initial.nature.targets[0].health - after.nature.targets[0].health,
        intercepts: stat(after, 'taming.guardian-instinct.intercepts') - stat(initial, 'taming.guardian-instinct.intercepts') }
}

async function empathy(input) {
    const before = await input.snapshot()
    let attempts = 0
    let matchingSounds = 0
    const soundsPerAttempt = []
    const onSound = packet => {
        const sound = decodeSoundPacket(input.actor.bot.registry, packet)
        if (sound.name === 'entity.wolf.pant' && Math.abs(sound.volume - 0.7) < 0.00001 && Math.abs(sound.pitch - 1.2) < 0.00001) matchingSounds++
    }
    input.actor.bot._client.on('sound_effect', onSound)
    try {
        for (const candidate of before.nature.targets) {
            const entity = input.actor.bot.entities[candidate.id]
            input.context.expect(Boolean(entity), 'Wild wolf is visible before natural feeding')
            const soundsBefore = matchingSounds
            await input.actor.bot.lookAt(entity.position.offset(0, 0.5, 0), true)
            input.actor.bot.activateEntity(entity)
            await input.actor.bot.waitForTicks(8)
            attempts++
            const after = await input.snapshot()
            soundsPerAttempt.push(matchingSounds - soundsBefore)
            const forced = stat(after, 'taming.wild-empathy.tames') - stat(before, 'taming.wild-empathy.tames')
            if (forced > 0) {
                return { attempts, forced, target: after.nature.targets.find(value => value.id === candidate.id),
                    soundsPerAttempt, forcedSounds: soundsPerAttempt.at(-1) }
            }
        }
        return { attempts, forced: 0, soundsPerAttempt }
    } finally {
        input.actor.bot._client.off('sound_effect', onSound)
    }
}

async function flowers(input) {
    const before = await input.snapshot()
    let attempts = 0
    for (let z = -1; z >= -3; z--) {
        for (let x = -2; x <= 2; x++) {
            const target = input.actor.bot.blockAt(input.actor.bot.entity.position.clone().set(x, 100, z))
            input.context.expect(target?.name === 'dandelion', 'Lucky-drop trial starts with an ordinary flower')
            await input.actor.bot.dig(target)
            await input.actor.bot.waitForTicks(3)
            attempts++
        }
    }
    const after = await input.snapshot()
    const rewards = [...after.knowledge.inventory, ...after.knowledge.drops].filter(item => !['minecraft:dandelion', 'minecraft:air'].includes(item.type))
    return { attempts, rewards, procs: stat(after, 'herbalism.luck.lucky-drops') - stat(before, 'herbalism.luck.lucky-drops') }
}

async function bone(input) {
    const before = await input.snapshot()
    let kills = 0
    for (const target of before.nature.targets) {
        const entity = input.actor.bot.entities[target.id]
        input.context.expect(Boolean(entity), 'Bone Harvest source cow is visible')
        await input.actor.bot.lookAt(entity.position.offset(0, 0.7, 0), true)
        input.actor.bot.attack(entity)
        await input.actor.bot.waitForTicks(26)
        kills++
        const killed = await input.snapshot()
        input.context.expect(killed.nature.targets.find(value => value.id === target.id).dead, 'Natural axe strike kills the source mob')
        if (killed.nature.globes.length === 0) continue
        const globe = killed.nature.globes[0]
        await input.actor.bot.lookAt(input.actor.bot.entity.position.clone().set(globe.x, input.actor.bot.entity.position.y + 1, globe.z), true)
        input.actor.bot.setControlState('forward', true)
        try {
            await input.context.waitUntil(async () => {
                const state = await input.snapshot()
                return stat(state, 'tragoul.bone-harvest.orbs-collected') > stat(killed, 'tragoul.bone-harvest.orbs-collected')
                    && !state.nature.globes.some(value => value.id === globe.id)
            }, {
                label: 'owner naturally walks into and collects the spawned globe', timeoutMs: 3000, intervalMs: 100,
            })
        } finally { input.actor.bot.clearControlStates() }
        const after = await input.snapshot()
        return { kills, globe: globe.type, collected: true, buffed: buffed(before, after),
            removed: !after.nature.globes.some(value => value.id === globe.id),
            inventoryContainsGlobe: after.knowledge.inventory.some(item => item.type === globe.type) }
    }
    return { kills, collected: false, buffed: false }
}

async function barter(input) {
    const first = await input.snapshot()
    const piglin = input.actor.bot.entities[first.nature.targets[0].id]
    input.context.expect(Boolean(piglin), 'Ordinary adult piglin is visible')
    const totals = items => items.reduce((result, item) => {
        result[item.type] = (result[item.type] ?? 0) + item.amount
        return result
    }, {})
    const available = state => totals([...state.knowledge.inventory, ...state.knowledge.drops])
    const trades = []
    for (let attempt = 0; attempt < 20; attempt++) {
        const beforeItems = available(await input.snapshot())
        await input.actor.bot.lookAt(piglin.position.offset(0, 1, 0), true)
        input.actor.bot.activateEntity(piglin)
        await input.context.waitUntil(async () => (await input.snapshot()).nature.barters.length > attempt, {
            label: 'ordinary gold barter completes naturally', timeoutMs: 10000, intervalMs: 200,
        })
        const after = await input.snapshot()
        const exchange = after.nature.barters[attempt]
        input.context.expect(exchange.ordinaryStacks > 0 && exchange.outcome.length > 0, 'Natural barter has an uncancelled item outcome')
        const expectedItems = totals(exchange.outcome)
        let actualItems
        await input.context.waitUntil(async () => {
            const afterItems = available(await input.snapshot())
            actualItems = Object.fromEntries(Object.keys(expectedItems).map(type => [type, (afterItems[type] ?? 0) - (beforeItems[type] ?? 0)]))
            return Object.entries(expectedItems).every(([type, amount]) => actualItems[type] === amount)
        }, { label: 'every barter output appears as real dropped or carried items', timeoutMs: 3000, intervalMs: 100 })
        trades.push({ expectedItems, actualItems })
        if (exchange.outcome.length > exchange.ordinaryStacks) return { attempts: attempt + 1, boosted: true, exchange, expectedItems, actualItems, trades }
        await input.actor.bot.waitForTicks(4)
    }
    return { attempts: 20, boosted: false, trades }
}

async function strider(input) {
    const before = await input.snapshot()
    const entity = input.actor.bot.entities[before.nature.targets[0].id]
    input.context.expect(Boolean(entity), 'Saddled strider is visible over the lava pool')
    input.actor.bot.mount(entity)
    await input.context.waitUntil(() => Boolean(input.actor.bot.vehicle), { label: 'natural strider mounting succeeds', timeoutMs: 4000 })
    const mountedAt = Date.now()
    await input.context.waitUntil(() => Date.now() - mountedAt >= 300, { label: 'strider rider remains mounted before dismount', timeoutMs: 1000, intervalMs: 50 })
    input.actor.bot.setControlState('sneak', true)
    await input.context.waitUntil(() => !input.actor.bot.vehicle, { label: 'sneak input naturally dismounts the strider', timeoutMs: 4000, intervalMs: 50 })
    input.actor.bot.setControlState('sneak', false)
    await input.actor.bot.waitForTicks(15)
    const after = await input.snapshot()
    return { rescues: stat(after, 'nether.strider-bond.lava-rescues') - stat(before, 'nether.strider-bond.lava-rescues'),
        floor: after.nature.floor, location: after.location, inLava: after.nature.inLava,
        dismounts: after.nature.dismounts, dismounted: !input.actor.bot.vehicle,
        feedback: [before, after].map(state => ({ world: state.world, position: state.feedbackPosition, effects: state.effectsEnabled, tps: state.tps, loadBand: state.feedbackLoadBand })) }
}

async function fishing(input) {
    for (let attempt = 0; attempt < 20; attempt++) {
        const before = await input.snapshot()
        await input.actor.bot.lookAt(input.actor.bot.entity.position.clone().set(0.5, 98.8, -8.5), true)
        input.actor.bot.activateItem()
        try {
            await input.context.waitUntil(async () => (await input.snapshot()).nature.bites > before.nature.bites, {
                label: `real fishing bobber receives natural bite ${attempt + 1}`, timeoutMs: 45000, intervalMs: 100,
            })
        } catch (error) {
            const failed = await input.snapshot()
            throw new Error(`${error.message}; fishing state ${JSON.stringify(failed.nature)}`, { cause: error })
        }
        input.actor.bot.activateItem()
        await input.context.waitUntil(async () => (await input.snapshot()).nature.catches.length > attempt, {
            label: 'natural bite is reeled into an actual caught item', timeoutMs: 4000, intervalMs: 100,
        })
        const after = await input.snapshot()
        const caught = after.nature.catches[attempt]
        input.context.expect(caught.ordinary.amount > 0, 'Natural fishing produces an ordinary caught item')
        if (caught.extra.length > 0) return { attempts: attempt + 1, caught }
        await input.actor.bot.waitForTicks(5)
    }
    return { attempts: 20, caught: { extra: [] } }
}

export const natureBehaviorCases = new Map([
    ['nether-blaze-leech', {
        stage: 'nature-blaze', negativeWindowTicks: 1, prepare: input => prepare(input), trigger: input => hazard(input, true),
        sound: 'minecraft:entity.blaze.ambient', soundVolume: 0.45, soundPitch: 1.4, particle: 'happy_villager', particleCount: 3,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.after.health < unlearned.before.health && unlearned.after.food <= unlearned.before.food && unlearned.after.effects.length === 0,
                'Unlearned natural magma damage neither feeds nor regenerates the player')
            context.expect(active.after.food > active.before.food && active.after.effects.some(effect => effect.type === 'minecraft:regeneration'),
                'Learned natural fire damage restores food and grants regeneration within twenty intervals')
            return result(['unlearned magma damage has no leech benefit', 'learned natural hazard proc grants actual food and regeneration'], unlearned, active)
        },
    }],
    ['nether-fire-resist', {
        stage: 'nature-fire', negativeWindowTicks: 1, prepare: input => prepare(input), trigger: input => hazard(input, false),
        sound: 'minecraft:block.fire.extinguish', soundVolume: 0.3, soundPitch: 1.5, particle: 'splash', particleCount: 3,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.before.health > unlearned.after.health && unlearned.after.negated === unlearned.before.negated,
                'Unlearned ordinary fire exposure causes uncancelled damage')
            context.expect(active.after.negated > active.before.negated && active.before.health - active.after.health < unlearned.before.health - unlearned.after.health,
                'Learned equal-duration fire exposure cancels hits and loses less actual health')
            return result(['unlearned fire damages health', 'learned fire cancellation reduces actual health loss over equal exposure'], unlearned, active)
        },
    }],
    ['nether-piglin-broker', {
        stage: 'nature-broker', negativeWindowTicks: 1, prepare: input => prepare(input, 'gold_ingot'), trigger: barter,
        sound: 'minecraft:entity.piglin.admiring_item', soundVolume: 0.9, soundPitch: 1.25, particle: 'wax_on', particleCount: 6,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.attempts === 20 && !unlearned.boosted, 'Twenty ordinary unlearned barters preserve their vanilla output list')
            context.expect(active.boosted && active.exchange.outcome.length > active.exchange.ordinaryStacks,
                'Learned actual gold barter adds tangible item output beyond its original vanilla roll')
            context.expect(Object.entries(active.expectedItems).every(([type, amount]) => active.actualItems[type] === amount),
                'Every boosted barter item exists in world drops or player inventory with the exact output amount')
            return result(['twenty unlearned natural gold trades deliver ordinary output without an extra stack',
                'learned real barter adds a stack and every output item is observed in world drops or player inventory'], unlearned, active)
        },
    }],
    ['nether-strider-bond', {
        stage: 'nature-strider', negativeWindowTicks: 1, trigger: strider,
        async prepare(input) {
            await prepare(input)
            await input.context.waitUntil(async () => {
                const state = await input.snapshot()
                return state.effectsEnabled && state.feedbackPosition?.world === state.world
                    && Math.hypot(state.feedbackPosition.x - state.location.x, state.feedbackPosition.z - state.location.z) < 2
            }, { label: 'Strider rescue observer registered in the actual Nether world', timeoutMs: 5000, intervalMs: 100 })
            await input.actor.bot.waitForTicks(6)
        },
        sound: 'minecraft:entity.strider.happy', soundVolume: 0.6, soundPitch: 1.2, particle: 'cloud', particleCount: 6,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.dismounted && unlearned.rescues === 0 && unlearned.inLava, 'Unlearned natural strider dismount remains in lava')
            context.expect(active.dismounted && !active.inLava && active.rescues === 1 && active.floor !== 'minecraft:lava' && active.floor !== 'minecraft:air',
                'Learned real strider dismount rescues the rider onto solid ground')
            return result(['unlearned strider dismount remains over lava', 'learned dismount teleports the actual rider to safe solid ground'], unlearned, active)
        },
    }],
    ['herbalism-luck', {
        stage: 'nature-luck', negativeWindowTicks: 1, prepare: input => prepare(input), trigger: flowers,
        sound: 'minecraft:entity.experience_orb.pickup', soundVolume: 0.6, soundPitch: 1.4, particle: 'wax_on', particleCount: 2,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.attempts === 15 && unlearned.rewards.length === 0 && unlearned.procs === 0, 'Fifteen unlearned flowers drop only flowers')
            context.expect(active.rewards.length > 0 && active.attempts === 15
                && active.rewards.every(item => ['minecraft:potato', 'minecraft:carrot', 'minecraft:beetroot', 'minecraft:apple'].includes(item.type))
                && active.rewards.reduce((total, item) => total + item.amount, 0) === active.procs,
                'Every learned lucky proc produces one actual usable food item across all fifteen natural flower breaks')
            return result(['unlearned flower drops contain no bonus food', 'all fifteen learned flower trials produce one usable food for each recorded lucky proc'], unlearned, active)
        },
    }],
    ['tame-guardian-instinct', {
        stage: 'nature-guardian', negativeWindowTicks: 1, prepare: input => prepare(input, 'bow', true), trigger: guardian,
        sound: 'minecraft:item.shield.block', soundVolume: 0.7, soundPitch: 1.1, particle: 'dust', particleCount: 4,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.attempts === 20 && unlearned.ownerDamage > 0 && unlearned.petDamage === 0 && unlearned.intercepts === 0,
                'Twenty unlearned natural arrows hurt only the owner')
            context.expect(active.intercepts > 0 && active.latest.ownerDamage === 0 && active.latest.petDamage > 0,
                'Learned natural arrow is intercepted with actual pet damage and no owner damage')
            return result(['unlearned arrows damage the owner and spare the nearby pet', 'learned pet intercept transfers actual projectile damage away from its owner'], unlearned, active)
        },
    }],
    ['tame-wild-empathy', {
        stage: 'nature-empathy', negativeWindowTicks: 1, prepare: input => prepare(input, 'bone'), trigger: empathy,
        sound: 'minecraft:entity.wolf.pant', soundVolume: 0.7, soundPitch: 1.2, particle: 'heart', particleCount: 4,
        soundControl: 'per-action-count',
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.attempts === 20 && unlearned.forced === 0 && unlearned.soundsPerAttempt.every(count => count <= 1),
                'Unlearned natural feeding receives no assisted tame and emits at most one core tame sound per feeding')
            context.expect(active.forcedSounds >= 2, 'Forced tame emits its own exact sound packet in addition to the shared core taming sound')
            context.expect(active.forced > 0 && active.target.tamed && active.target.owned, 'Learned natural feeding produces an assisted tame actually owned by the actor')
            return { ...result(['unlearned feeding has no assisted tame', 'learned natural food interaction produces an actual owned pet through the assisted branch'], unlearned, active),
                soundAssertions: ['each unlearned feeding emits at most one exact wolf pant from core taming',
                    'the observed forced tame emits at least two exact wolf pants, adding adaptation feedback to core taming feedback'] }
        },
    }],
    ['tragoul-blood-pact', {
        stage: 'nature-pact', negativeWindowTicks: 1, prepare: input => prepare(input, 'wooden_sword', true), trigger: pact,
        sound: 'minecraft:block.respawn_anchor.charge', soundVolume: 0.6, soundPitch: 1, particle: 'totem_of_undying', particleCount: 8,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.hits === 20 && unlearned.healthLost >= 80 && !unlearned.buffed && unlearned.sacrificed === 0,
                'Twenty qualifying unlearned damage hits grant no pact buffs')
            context.expect(active.healthLost > 0 && active.buffed && active.sacrificed >= 4,
                'Learned real qualifying damage grants source-selected beneficial effects or movement attributes')
            return result(['unlearned qualifying damage grants no buffs', 'learned qualifying health loss grants actual beneficial effects within twenty hits'], unlearned, active)
        },
    }],
    ['tragoul-bone-harvest', {
        stage: 'nature-bone', negativeWindowTicks: 1, prepare: input => prepare(input, 'wooden_axe'), trigger: bone,
        sound: 'minecraft:entity.experience_orb.pickup', soundVolume: 0.5, soundPitch: 1.2, particle: 'dust', particleCount: 3,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.kills === 20 && !unlearned.collected && !unlearned.buffed, 'Twenty unlearned natural kills spawn no collectible buff globe')
            context.expect(active.collected && active.removed && active.buffed && !active.inventoryContainsGlobe,
                'Learned natural kill spawns a globe that is consumed by walking into it and grants actual buffs')
            return result(['unlearned kills produce no buff globe', 'learned natural kill and pickup consume a real globe and grant beneficial effects'], unlearned, active)
        },
    }],
    ['seaborne-fishers-fantasy', {
        stage: 'nature-fishing', negativeWindowTicks: 1, prepare: input => prepare(input, 'fishing_rod'), trigger: fishing,
        sound: 'minecraft:block.conduit.activate', soundVolume: 0.4, soundPitch: 1.76, particle: 'glow', particleCount: 4,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.attempts === 20 && unlearned.caught.extra.length === 0, 'Twenty unlearned actual fishing catches create only their ordinary loot')
            context.expect(active.caught.extra.length === 1 && active.caught.extra[0].amount === 1,
                'Learned real catch creates one additional actual bonus item at the player')
            return result(['unlearned natural fishing has no bonus item', 'learned natural catch spawns one tangible additional bonus item'], unlearned, active)
        },
    }],
])
