function position(actor, x, y, z) {
    const current = actor.bot.entity.position
    return current.offset(x - current.x, y - current.y, z - current.z)
}

function count(actor, name) {
    return actor.bot.inventory.items().filter(item => item.name === name).reduce((total, item) => total + item.count, 0)
}

async function prepare(input, item) {
    input.actor.bot.clearControlStates()
    input.context.expect((await input.snapshot()).nether.environment === 'NETHER', 'Trial takes place in the Nether dimension')
    await input.context.waitUntil(async () => {
        const state = await input.snapshot()
        return state.feedbackPosition?.world === state.world
    }, { label: 'feedback viewer follows the Nether world transfer', timeoutMs: 4000, intervalMs: 100 })
    if (item) await input.equip(item)
    await input.actor.bot.look(0, 0, true)
    await input.actor.bot.waitForTicks(4)
}

async function stepForward(actor, ticks) {
    actor.bot.setControlState('forward', true)
    try {
        await actor.bot.waitForTicks(ticks)
    } finally {
        actor.bot.setControlState('forward', false)
    }
}

async function exposure(input, ticks) {
    const before = await input.snapshot()
    await stepForward(input.actor, 6)
    await input.actor.bot.waitForTicks(ticks)
    const after = await input.snapshot()
    return { healthBefore: before.health, healthAfter: after.health, location: after.location, effects: after.effects }
}

function verifyReduction({ context, unlearned, active }, complete = false) {
    const unlearnedDamage = unlearned.healthBefore - unlearned.healthAfter
    const activeDamage = active.healthBefore - active.healthAfter
    context.expect(unlearnedDamage > 0, 'Unlearned natural hazard inflicts health damage')
    context.expect(active.healthBefore === unlearned.healthBefore, 'Both hazard trials start at identical health')
    context.expect(complete ? activeDamage === 0 : activeDamage >= 0 && activeDamage < unlearnedDamage,
        complete ? 'Learned hazard immunity prevents all health damage' : 'Learned adaptation reduces actual health damage')
    return { assertions: ['unlearned natural hazard damages health', complete ? 'learned hazard causes zero damage' : 'learned hazard causes less damage'], measurements: { unlearned, active } }
}

async function feast(input) {
    const before = await input.snapshot()
    const itemsBefore = count(input.actor, 'crimson_fungus')
    input.actor.bot.activateItem()
    await input.actor.bot.waitForTicks(5)
    input.actor.bot.deactivateItem()
    const after = await input.snapshot()
    return { foodBefore: before.food, foodAfter: after.food, saturationBefore: before.saturation,
        saturationAfter: after.saturation, consumed: itemsBefore - count(input.actor, 'crimson_fungus'), effects: after.effects }
}

async function explosion(input) {
    const before = await input.snapshot()
    const tnt = input.actor.bot.blockAt(position(input.actor, 0, 100, -3))
    input.context.expect(tnt?.name === 'tnt', 'Blast trial starts with an unprimed TNT block')
    await input.actor.bot.activateBlock(tnt)
    await input.actor.bot.look(0, 0, true)
    input.actor.bot.setControlState('back', true)
    try {
        await input.actor.bot.waitForTicks(12)
    } finally {
        input.actor.bot.setControlState('back', false)
    }
    await input.actor.bot.waitForTicks(90)
    const after = await input.snapshot()
    input.context.expect(input.actor.bot.blockAt(tnt.position)?.name === 'air', 'Naturally ignited TNT is consumed')
    return { healthBefore: before.health, healthAfter: after.health, location: after.location }
}

async function lava(input) {
    const before = await input.snapshot()
    const velocities = []
    const onVelocity = packet => {
        if (packet.entityId === input.actor.bot.entity.id) velocities.push({ ...packet.velocity })
    }
    input.actor.bot._client.on('entity_velocity', onVelocity)
    try { await stepForward(input.actor, 10) }
    finally { input.actor.bot._client.off('entity_velocity', onVelocity) }
    const after = await input.snapshot()
    return { foodBefore: before.food, foodAfter: after.food, effects: after.effects,
        strides: (after.stats['nether.lava-walker.blocks-walked'] ?? 0) - (before.stats['nether.lava-walker.blocks-walked'] ?? 0),
        location: after.location, velocities }
}

async function burningStrike(input) {
    await stepForward(input.actor, 6)
    await input.context.waitUntil(async () => (await input.snapshot()).nether.fireTicks > 0,
        { label: 'player naturally ignites in the fire block', timeoutMs: 3000 })
    const before = await input.snapshot(input.opponent)
    const target = input.actor.bot.entities[input.opponent.bot.entity.id]
    input.context.expect(Boolean(target), 'Burning melee target is visible')
    await input.actor.bot.lookAt(target.position.offset(0, 1, 0), true)
    input.actor.bot.attack(target)
    await input.actor.bot.waitForTicks(5)
    const after = await input.snapshot(input.opponent)
    return { damage: before.health - after.health, fireTicks: after.nether.fireTicks }
}

async function mason(input) {
    const before = await input.snapshot()
    let miningSpeed = before.attributes['minecraft:block_break_speed']
    let broken = 0
    for (let z = -1; z >= -3; z--) {
        for (let x = -2; x <= 2; x++) {
            const target = input.actor.bot.blockAt(position(input.actor, x, 100, z))
            input.context.expect(target?.name === 'blackstone', 'Mason trial starts with natural blackstone')
            let finished = false
            let failure
            const digging = input.actor.bot.dig(target).then(() => { finished = true }, error => { failure = error; finished = true })
            try {
                await input.actor.bot.waitForTicks(4)
                miningSpeed = Math.max(miningSpeed, (await input.snapshot()).attributes['minecraft:block_break_speed'])
                await input.context.waitUntil(() => {
                    if (failure) throw failure
                    return finished
                }, { label: 'natural blackstone mining completes', timeoutMs: 8000, intervalMs: 20 })
                await digging
            } finally {
                if (!finished) input.actor.bot.stopDigging()
            }
            input.context.expect(input.actor.bot.blockAt(target.position)?.name === 'air', 'Natural mining removes the blackstone block')
            broken++
        }
    }
    await input.actor.bot.waitForTicks(3)
    const after = await input.snapshot()
    const outputs = [...after.knowledge.inventory, ...after.knowledge.drops]
        .filter(item => item.type !== 'minecraft:wooden_pickaxe' && item.type !== 'minecraft:air')
    return { beforeSpeed: before.attributes['minecraft:block_break_speed'], miningSpeed, broken,
        items: outputs.reduce((total, item) => total + item.amount, 0) }
}

async function skull(input) {
    const seen = new Map()
    const onSpawn = entity => {
        if (entity.name === 'wither_skull') seen.set(entity.id, { start: entity.position.clone(), distance: 0 })
    }
    const onMove = entity => {
        const sample = seen.get(entity.id)
        if (sample) sample.distance = Math.max(sample.distance, entity.position.distanceTo(sample.start))
    }
    input.actor.bot.on('entitySpawn', onSpawn)
    input.actor.bot.on('entityMoved', onMove)
    input.actor.bot.on('entityGone', onMove)
    const before = count(input.actor, 'wither_skeleton_skull')
    try {
        input.actor.bot.activateItem()
        await input.actor.bot.waitForTicks(20)
        input.actor.bot.deactivateItem()
        const movement = [...seen.values()].map(sample => sample.distance)
        return { consumed: before - count(input.actor, 'wither_skeleton_skull'), spawned: seen.size, movement, serverTravel: (await input.snapshot()).nether.skullTravel }
    } finally {
        input.actor.bot.deactivateItem()
        input.actor.bot.off('entitySpawn', onSpawn)
        input.actor.bot.off('entityMoved', onMove)
        input.actor.bot.off('entityGone', onMove)
    }
}

async function harvest(input) {
    const skeleton = input.actor.bot.nearestEntity(entity => entity.name === 'wither_skeleton')
    input.context.expect(Boolean(skeleton), 'Harvest trial starts with a living Wither Skeleton')
    await input.actor.bot.lookAt(skeleton.position.offset(0, 1, 0), true)
    input.actor.bot.attack(skeleton)
    await input.context.waitUntil(() => !input.actor.bot.entities[skeleton.id], { label: 'natural sword strike kills Wither Skeleton', timeoutMs: 3000 })
    await input.actor.bot.look(0, 0, true)
    await stepForward(input.actor, 9)
    await input.actor.bot.waitForTicks(25)
    return { bone: count(input.actor, 'bone'), coal: count(input.actor, 'coal'), skull: count(input.actor, 'wither_skeleton_skull') }
}

export const netherBehaviorCases = new Map([
    ['nether-ashwalker', {
        stage: 'nether-qa-ash', negativeWindowTicks: 1,
        prepare: input => prepare(input), trigger: input => exposure(input, 20), verify: verifyReduction,
        sound: 'minecraft:block.soul_sand.step', soundVolume: 0.4, soundPitch: 0.8, particle: 'soul_fire_flame',
    }],
    ['nether-crimson-feast', {
        stage: 'nether-qa-feast', negativeWindowTicks: 1,
        prepare: input => prepare(input, 'crimson_fungus'), trigger: feast,
        sound: 'minecraft:entity.generic.eat', soundVolume: 0.6, soundPitch: 1.1, particle: 'crimson_spore',
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.consumed === 0 && unlearned.foodAfter === unlearned.foodBefore,
                'Unlearned right click neither eats fungus nor restores hunger')
            context.expect(active.consumed === 1 && active.foodAfter === active.foodBefore + 6,
                'Learned right click consumes one fungus and restores six food points')
            context.expect(active.saturationAfter > active.saturationBefore && active.effects.some(effect => effect.type === 'minecraft:fire_resistance'),
                'Nether fungus meal restores saturation and grants fire resistance')
            return { assertions: ['unlearned fungus cannot be eaten', 'learned fungus consumption restores food, saturation, and Nether fire resistance'], measurements: { unlearned, active } }
        },
    }],
    ['nether-ghast-ward', {
        stage: 'nether-qa-ward', negativeWindowTicks: 1,
        prepare: input => prepare(input, 'flint_and_steel'), trigger: explosion, verify: verifyReduction,
        sound: 'minecraft:block.beacon.deactivate', soundVolume: 0.4, soundPitch: 1.4, particle: 'sculk_soul',
    }],
    ['nether-lava-walker', {
        stage: 'nether-qa-lava', negativeWindowTicks: 1,
        prepare: input => prepare(input), trigger: lava,
        sound: 'minecraft:block.fire.extinguish', soundVolume: 0.25, soundPitch: 1.4, particle: 'lava',
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.strides === 0 && !unlearned.effects.some(effect => effect.type === 'minecraft:fire_resistance'),
                'Unlearned lava entry does not activate a stride or fire resistance')
            context.expect(active.strides > 0 && active.foodAfter < active.foodBefore,
                'Learned lava entry activates strides and spends real food')
            context.expect(active.effects.some(effect => effect.type === 'minecraft:fire_resistance')
                && active.velocities.some(velocity => velocity.y >= 0.159 && Math.hypot(velocity.x, velocity.z) >= 0.75)
                && !unlearned.velocities.some(velocity => velocity.y >= 0.159 && Math.hypot(velocity.x, velocity.z) >= 0.75),
                'Lava stride grants resistance and sends its actual upward and forward velocity impulse')
            return { assertions: ['unlearned lava entry has no stride', 'learned lava entry spends food, grants resistance, and sends upward forward motion'], measurements: { unlearned, active } }
        },
    }],
    ['nether-magma-skin', {
        stage: 'nether-qa-magma', negativeWindowTicks: 1,
        prepare: input => prepare(input), trigger: burningStrike,
        sound: 'minecraft:entity.blaze.hurt', soundVolume: 0.35, soundPitch: 1.5, particle: 'soul_fire_flame',
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.damage > 0 && unlearned.fireTicks <= 0, 'Unlearned burning punch deals ordinary damage without ignition')
            context.expect(active.damage >= unlearned.damage + 2.9 && active.fireTicks > 0,
                'Learned burning punch adds at least three damage and ignites the target')
            return { assertions: ['unlearned burning punch does not ignite', 'learned burning punch adds damage and ignites its victim'], measurements: { unlearned, active } }
        },
    }],
    ['nether-netherrack-mason', {
        stage: 'nether-qa-mason', negativeWindowTicks: 1,
        prepare: input => prepare(input, 'wooden_pickaxe'), trigger: mason,
        sound: 'minecraft:block.netherrack.break', soundVolume: 0.6, soundPitch: 1.3, particle: 'lava', particleCount: 3,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.items === unlearned.broken && unlearned.miningSpeed === unlearned.beforeSpeed, 'Unlearned blackstone mining preserves normal break speed')
            context.expect(active.items > active.broken && active.beforeSpeed === unlearned.beforeSpeed && active.miningSpeed > active.beforeSpeed,
                'Learned blackstone mining applies a real break speed bonus during the dig')
            return { assertions: ['unlearned blackstone mining has ordinary break speed', 'learned natural mining increases break speed and removes the block'], measurements: { unlearned, active } }
        },
    }],
    ['nether-skull-toss', {
        stage: 'nether-qa-skull', negativeWindowTicks: 1,
        prepare: input => prepare(input, 'wither_skeleton_skull'), trigger: skull,
        sound: 'minecraft:entity.wither.shoot', soundVolume: 1, soundPitch: 1, particle: 'soul',
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.consumed === 0 && unlearned.spawned === 0, 'Unlearned skull right click consumes no item and launches no projectile')
            context.expect(active.consumed === 1 && active.spawned === 1 && active.serverTravel > 0.2,
                'Learned skull right click consumes one skull and launches one moving Wither Skull')
            return { assertions: ['unlearned skull use does not launch', 'learned skull use consumes one item and launches a moving projectile'], measurements: { unlearned, active } }
        },
    }],
    ['nether-soul-strider', {
        stage: 'nether-qa-soul', attribute: 'minecraft:movement_speed', negativeWindowTicks: 10,
        prepare: input => prepare(input), trigger: input => stepForward(input.actor, 8),
        sound: 'minecraft:particle.soul_escape', soundVolume: 0.35, soundPitch: 1.3, particle: 'soul_fire_flame',
    }],
    ['nether-wither-resist', {
        stage: 'nether-qa-wither', negativeWindowTicks: 1,
        prepare: input => prepare(input), trigger: input => exposure(input, 65),
        verify: input => verifyReduction(input, true),
        sound: 'minecraft:particle.soul_escape', soundVolume: 0.35, soundPitch: 0.8, particle: 'sculk_soul',
    }],
    ['nether-wither-harvest', {
        stage: 'nether-qa-harvest', negativeWindowTicks: 1,
        prepare: input => prepare(input, 'wooden_sword'), trigger: harvest, particle: 'soul',
        sound: 'minecraft:entity.wither_skeleton.hurt', soundVolume: 0.4, soundPitch: 1.1,
        soundAlternatives: [
            { sound: 'minecraft:entity.wither_skeleton.hurt', soundVolume: 0.4, soundPitch: 1.1 },
            { sound: 'minecraft:ui.toast.challenge_complete', soundVolume: 0.5, soundPitch: 1.2 },
        ],
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.bone <= 2 && unlearned.coal <= 1, 'Unlearned skeleton loot remains within ordinary drop bounds')
            context.expect(active.bone >= 3 && active.coal >= 3, 'Learned skeleton kill yields the guaranteed three bonus bones and coal')
            return { assertions: ['unlearned drops stay within vanilla bounds', 'learned natural kill yields at least three bones and three coal'], measurements: { unlearned, active } }
        },
    }],
])
