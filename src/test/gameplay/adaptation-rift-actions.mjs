function point(actor, x, y, z) {
    return actor.bot.entity.position.clone().set(x, y, z)
}

function count(actor, name) {
    return actor.bot.inventory.slots.reduce((sum, item) => sum + (item?.name === name ? item.count : 0), 0)
}

const effect = (state, name) => state.effects.some(entry => entry.type === `minecraft:${name}`)

function prepare(item) {
    return async ({ actor, equip }) => {
        actor.bot.clearControlStates()
        if (item) await equip(item)
        await actor.bot.waitForTicks(5)
    }
}

async function block(input, x, y, z) {
    await input.context.waitUntil(() => Boolean(input.actor.bot.blockAt(point(input.actor, x, y, z))), {
        label: 'Rift interaction block loaded', timeoutMs: 5000,
    })
    return input.actor.bot.blockAt(point(input.actor, x, y, z))
}

async function walkTo(input, destination) {
    await input.actor.bot.lookAt(destination.offset(0, 1.6, 0), true)
    input.actor.bot.setControlState('forward', true)
    try {
        await input.context.waitUntil(() => input.actor.bot.entity.position.distanceTo(destination) < 0.6,
            { label: 'walk away from Rift binding location', timeoutMs: 6500, intervalMs: 20 })
    } finally {
        input.actor.bot.setControlState('forward', false)
    }
    await input.actor.bot.waitForTicks(3)
}

async function rightAir(actor) {
    await actor.bot.lookAt(actor.bot.entity.position.offset(0, 3, -10), true)
    actor.bot.activateItem()
    actor.bot.deactivateItem()
}

async function descent(input) {
    const { actor, context, snapshot } = input
    await actor.bot.consume()
    await actor.bot.waitForTicks(3)
    const before = await snapshot()
    context.expect(effect(before, 'levitation'), 'Naturally consumed potion actually gives levitation')
    actor.bot.setControlState('sneak', true)
    try {
        await actor.bot.waitForTicks(5)
        return { before, after: await snapshot(), bottles: count(actor, 'glass_bottle') }
    } finally {
        actor.bot.setControlState('sneak', false)
    }
}

async function blink(input) {
    const { actor, context, snapshot } = input
    await actor.bot.lookAt(actor.bot.entity.position.offset(15, 1.62, 0), true)
    const before = await snapshot()
    actor.bot.setControlState('jump', true)
    try {
        await actor.bot.waitForTicks(4)
        context.expect(!actor.bot.entity.onGround, 'First jump leaves the ground')
        actor.bot.setControlState('jump', false)
        await actor.bot.waitForTicks(2)
        actor.bot.setControlState('jump', true)
        await actor.bot.waitForTicks(3)
        actor.bot.setControlState('jump', false)
        await actor.bot.waitForTicks(15)
        return { before, after: await snapshot() }
    } finally {
        actor.bot.setControlState('jump', false)
    }
}

async function magnet(input) {
    const before = await input.snapshot()
    input.actor.bot.setControlState('sneak', true)
    try {
        await input.actor.bot.waitForTicks(50)
        return { before, after: await input.snapshot() }
    } finally {
        input.actor.bot.setControlState('sneak', false)
    }
}

async function pocket(input) {
    const { actor, equip, snapshot } = input
    actor.bot.setQuickBarSlot(8)
    await actor.bot.waitForTicks(3)
    const before = await snapshot()
    const target = await block(input, 3, 100, 0)
    await actor.bot.activateBlock(target)
    await actor.bot.waitForTicks(10)
    const pulled = count(actor, 'stone')
    const afterPull = await snapshot()
    await equip('diamond')
    actor.bot.setControlState('sneak', true)
    try {
        await actor.bot.waitForTicks(3)
        await actor.bot.tossStack(actor.bot.heldItem)
        await actor.bot.waitForTicks(10)
        return { before, pulled, afterPull, after: await snapshot() }
    } finally {
        actor.bot.setControlState('sneak', false)
    }
}

async function throwPearl(input) {
    const { actor, context, snapshot } = input
    const before = await snapshot()
    const pearlIds = new Set()
    const pearlSpawns = []
    const pearlVelocities = []
    const onVelocity = packet => {
        if (pearlIds.has(packet.entityId)) pearlVelocities.push({ entityId: packet.entityId, velocity: packet.velocity })
    }
    const onSpawn = entity => {
        if (entity.name !== 'ender_pearl') return
        pearlIds.add(entity.id)
        pearlSpawns.push({ id: entity.id, position: entity.position.clone(), velocity: entity.velocity?.clone(), yaw: actor.bot.entity.yaw, pitch: actor.bot.entity.pitch })
    }
    actor.bot.on('entitySpawn', onSpawn)
    actor.bot._client.on('entity_velocity', onVelocity)
    try {
        await actor.bot.lookAt(point(actor, 8, 102.8, 0.5), true)
        actor.bot.activateItem()
        actor.bot.deactivateItem()
        await actor.bot.waitForTicks(1)
        await actor.bot.lookAt(point(actor, -8, 103.5, 0.5), true)
        actor.bot._client.write('look', {
            yaw: 180 - actor.bot.entity.yaw * 180 / Math.PI,
            pitch: -actor.bot.entity.pitch * 180 / Math.PI,
            flags: { onGround: actor.bot.entity.onGround, hasHorizontalCollision: false },
        })
        let steering
        await context.waitUntil(async () => {
            steering = await snapshot()
            return Math.abs(steering.location.yaw - 90) < 1
        }, { label: 'server receives the backward aim before pearl impact', timeoutMs: 2000, intervalMs: 25 })
        await context.waitUntil(() => Math.abs(actor.bot.entity.position.x - before.location.x) > 3,
            { label: 'natural thrown pearl resolves to a teleport', timeoutMs: 8000, intervalMs: 50 })
        await actor.bot.waitForTicks(5)
        return { before, after: await snapshot(), pearlEntities: pearlIds.size, pearlSpawns, pearlVelocities, steering: { location: steering.location, input: steering.input }, remainingPearls: count(actor, 'ender_pearl') }
    } finally {
        actor.bot.off('entitySpawn', onSpawn)
        actor.bot._client.off('entity_velocity', onVelocity)
    }
}

async function visage(input) {
    const { actor, context, snapshot } = input
    const before = await snapshot()
    context.expect(Boolean(before.rift.target), 'Natural Enderman target exists')
    await context.waitUntil(() => Boolean(actor.bot.entities[before.rift.target.entityId]), {
        label: 'Enderman visible to ordinary player', timeoutMs: 5000,
    })
    const target = actor.bot.entities[before.rift.target.entityId]
    await actor.bot.lookAt(target.position.offset(0, 2.55, 0), true)
    let after
    await context.waitUntil(async () => {
        after = await snapshot()
        return Boolean(after.rift.targeting)
    }, { label: 'looking at Enderman triggers natural target selection', timeoutMs: 6000, intervalMs: 100 })
    await actor.bot.waitForTicks(3)
    return { before, after: await snapshot() }
}

async function taglock(input) {
    const { actor, context, snapshot } = input
    const before = await snapshot()
    await context.waitUntil(() => Boolean(actor.bot.entities[before.rift.target.entityId]), {
        label: 'taglock cow visible', timeoutMs: 5000,
    })
    const victim = actor.bot.entities[before.rift.target.entityId]
    actor.bot.setControlState('sneak', true)
    try {
        await actor.bot.waitForTicks(3)
        await actor.bot.lookAt(victim.position.offset(0, 0.7, 0), true)
        actor.bot.attack(victim)
        await actor.bot.waitForTicks(8)
    } finally {
        actor.bot.setControlState('sneak', false)
    }
    const afterTag = await snapshot()
    await actor.bot.lookAt(point(actor, 9.5, 103.5, 0.5), true)
    actor.bot.activateItem()
    actor.bot.deactivateItem()
    await actor.bot.waitForTicks(65)
    return { before, afterTag, after: await snapshot(), remainingPearls: count(actor, 'ender_pearl') }
}

async function voidSkin(input) {
    const { actor, opponent, context, snapshot } = input
    const sword = opponent.bot.inventory.items().find(item => item.name === 'wooden_sword')
    context.expect(Boolean(sword), 'Void-skin opponent holds a real sword')
    await opponent.bot.equip(sword, 'hand')
    await opponent.bot.waitForTicks(25)
    let deaths = 0
    const onDeath = () => { deaths++ }
    actor.bot.on('death', onDeath)
    try {
        const before = await snapshot()
        context.expect(before.health === 2, 'Void-skin lethal control begins at two health')
        const victim = opponent.bot.entities[actor.bot.entity.id]
        context.expect(Boolean(victim), 'Defender is visible to opponent')
        await opponent.bot.lookAt(victim.position.offset(0, 1, 0), true)
        opponent.bot.attack(victim)
        await actor.bot.waitForTicks(15)
        if (deaths > 0) {
            await context.waitUntil(() => actor.bot.health > 0, { label: 'lethal control player respawns', timeoutMs: 5000 })
        }
        return { before, after: await snapshot(), deaths, pearls: count(actor, 'ender_pearl') }
    } finally {
        actor.bot.off('death', onDeath)
    }
}

async function gate(input) {
    const { actor, snapshot } = input
    const before = await snapshot()
    actor.bot.setControlState('sneak', true)
    try {
        await actor.bot.waitForTicks(3)
        await actor.bot.lookAt(actor.bot.entity.position.offset(0, 4, -8), true)
        actor.bot.swingArm('right')
        await actor.bot.waitForTicks(10)
    } finally {
        actor.bot.setControlState('sneak', false)
    }
    const bound = await snapshot()
    await walkTo(input, point(actor, 0.5, 100, 8.5))
    const departed = await snapshot()
    await rightAir(actor)
    await actor.bot.waitForTicks(105)
    return { before, bound, departed, after: await snapshot(), eyes: count(actor, 'ender_eye') }
}

async function access(input) {
    const { actor, snapshot, context } = input
    const source = await block(input, 3, 100, 0)
    actor.bot.setControlState('sneak', true)
    try {
        await actor.bot.waitForTicks(3)
        await actor.bot.lookAt(source.position.offset(0, 0.5, 0.5), true)
        actor.bot._client.write('block_dig', { status: 0, location: source.position, face: 4, sequence: 0 })
        await actor.bot.waitForTicks(2)
        actor.bot._client.write('block_dig', { status: 1, location: source.position, face: 4, sequence: 1 })
    } finally {
        actor.bot.setControlState('sneak', false)
    }
    await actor.bot.waitForTicks(10)
    const bound = await snapshot()
    await walkTo(input, point(actor, 0.5, 100, 8.5))
    await rightAir(actor)
    await actor.bot.waitForTicks(20)
    const opened = Boolean(actor.bot.currentWindow)
    let observedDiamonds = 0
    if (opened) {
        const window = actor.bot.currentWindow
        for (let slot = 0; slot < window.inventoryStart; slot++) {
            if (window.slots[slot]?.name === 'diamond') {
                observedDiamonds += window.slots[slot].count
                await actor.bot.clickWindow(slot, 0, 1)
            }
        }
        actor.bot.closeWindow(window)
        await actor.bot.waitForTicks(8)
    }
    context.expect(actor.bot.entity.position.distanceTo(point(actor, 3.5, 100, 0.5)) > 7, 'Remote storage user stays beyond normal chest reach')
    return { bound, opened, observedDiamonds, diamonds: count(actor, 'diamond'), after: await snapshot() }
}

async function conduit(input) {
    const { actor, context, snapshot } = input
    const source = await block(input, 3, 100, -2)
    const destination = await block(input, 3, 100, 2)
    const before = await snapshot()
    actor.bot.setControlState('sneak', true)
    try {
        await actor.bot.waitForTicks(3)
        await actor.bot.activateBlock(source)
        await actor.bot.waitForTicks(15)
        if (actor.bot.currentWindow) actor.bot.closeWindow(actor.bot.currentWindow)
        await actor.bot.activateBlock(destination)
        await actor.bot.waitForTicks(15)
        if (actor.bot.currentWindow) actor.bot.closeWindow(actor.bot.currentWindow)
    } finally {
        actor.bot.setControlState('sneak', false)
    }
    await actor.bot.waitForTicks(10)
    await actor.bot.activateBlock(source)
    await context.waitUntil(() => Boolean(actor.bot.currentWindow), { label: 'naturally open conduit source chest', timeoutMs: 5000 })
    actor.bot.closeWindow(actor.bot.currentWindow)
    await actor.bot.waitForTicks(20)
    return { before, after: await snapshot() }
}

function report(assertions, unlearned, active) {
    return { assertions, measurements: { unlearned, active } }
}

export const riftBehaviorCases = new Map([
    ['rift-descent', {
        stage: 'rift-descent', prepare: prepare('potion'), trigger: descent, negativeWindowTicks: 10,
        description: 'sneaking cancels naturally consumed levitation and grants fall protection',
        sound: 'minecraft:block.amethyst_block.break', soundVolume: 0.5, soundPitch: 1.7, particle: 'dragon_breath',
        async verify({ context, unlearned, active }) {
            context.expect(effect(unlearned.after, 'levitation'), 'Unlearned sneak preserves the consumed levitation')
            context.expect(!effect(active.after, 'levitation'), 'Learned sneak removes actual levitation')
            context.expect(active.after.attributes['minecraft:fall_damage_multiplier'] === 0, 'Descent grants actual zero fall-damage multiplier')
            context.expect(unlearned.bottles === 1 && active.bottles === 1, 'Both levitation effects came from consumed bottled potions')
            return report(['unlearned sneak retains consumed levitation', 'learned sneak clears levitation and sets zero fall damage'], unlearned, active)
        },
    }],
    ['rift-blink', {
        stage: 'rift-blink', trigger: blink, negativeWindowTicks: 10,
        description: 'a natural double jump teleports the player along their aim',
        sound: 'minecraft:entity.enderman.teleport', soundVolume: 0.5, soundPitch: 1, particle: 'reverse_portal',
        async verify({ context, unlearned, active }) {
            context.expect(Math.abs(unlearned.after.location.x - unlearned.before.location.x) < 0.2, 'Unlearned stationary double jump has no horizontal travel')
            context.expect(active.after.location.x - active.before.location.x > 4 && active.after.rift.teleport?.cause === 'PLUGIN', 'Learned double jump produces actual aimed teleport')
            return report(['unlearned stationary jump gesture does not travel horizontally', 'learned double jump teleports along the aim'], unlearned, active)
        },
    }],
    ['rift-void-magnet', {
        stage: 'rift-magnet', trigger: magnet, negativeWindowTicks: 10,
        description: 'sneaking transfers distant item drops into the ender chest',
        sound: 'minecraft:block.beacon.power_select', soundVolume: 0.4, soundPitch: 1.5, particle: 'end_rod',
        async verify({ context, unlearned, active }) {
            context.expect((unlearned.after.rift.enderChest.DIAMOND ?? 0) === 0 && unlearned.after.rift.drops.DIAMOND === 3, 'Unlearned sneak leaves all distant diamonds dropped')
            context.expect(active.after.rift.enderChest.DIAMOND === 3 && (active.after.rift.drops.DIAMOND ?? 0) === 0, 'Learned sneak conserves and stores all three distant diamonds')
            context.expect(Math.abs(active.after.location.x - active.before.location.x) < 0.2, 'Owner stays outside physical pickup range')
            return report(['unlearned sneak leaves three distant diamonds untouched', 'learned stationary sneak moves exactly three diamonds into ender storage'], unlearned, active)
        },
    }],
    ['rift-inflated-pocket-dimension', {
        stage: 'rift-pocket', trigger: pocket, negativeWindowTicks: 10,
        description: 'empty-hand block use pulls matching ender items and sneak-dropping stores items',
        sound: 'minecraft:block.ender_chest.open', soundVolume: 0.4, soundPitch: 1.7, particle: 'portal',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.pulled === 0 && unlearned.afterPull.rift.enderChest.STONE === 16, 'Unlearned empty-hand use leaves ender stone untouched')
            context.expect(active.pulled === 16 && (active.afterPull.rift.enderChest.STONE ?? 0) === 0, 'Learned empty-hand use transfers exactly sixteen stone')
            context.expect(unlearned.after.rift.drops.DIAMOND === 3 && (unlearned.after.rift.enderChest.DIAMOND ?? 0) === 0, 'Unlearned sneak-drop produces ordinary world drops')
            context.expect(active.after.rift.enderChest.DIAMOND === 3 && (active.after.rift.drops.DIAMOND ?? 0) === 0, 'Learned sneak-drop stores all three diamonds without a world drop')
            return report(['unlearned gestures neither pull nor store ender items', 'learned pull and deposit conserve sixteen stone and three diamonds'], unlearned, active)
        },
    }],
    ['rift-pearl-rebound', {
        stage: 'rift-rebound', prepare: prepare('ender_pearl'), trigger: throwPearl, negativeWindowTicks: 10,
        description: 'a naturally thrown pearl rebounds off a wall before teleporting its owner',
        sound: 'minecraft:block.slime_block.hit', soundVolume: 0.7, soundPitch: 1.4, particle: 'reverse_portal',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.after.location.x > 6, 'Ordinary control pearl teleports to the forward wall')
            context.expect(active.after.location.x < -3 && active.pearlEntities >= 2, 'Learned pearl spawns its rebound and teleports behind the shooter')
            context.expect(active.before.health - active.after.health < unlearned.before.health - unlearned.after.health, 'Learned pearl teleport costs less actual health')
            context.expect(unlearned.remainingPearls === 0 && active.remainingPearls === 0, 'Each trial consumes one real pearl')
            return report(['unlearned pearl resolves at its first forward impact', 'learned pearl rebounds backward and reduces teleport health loss'], unlearned, active)
        },
    }],
    ['rift-visage', {
        stage: 'rift-visage', trigger: visage, negativeWindowTicks: 10,
        description: 'carrying a pearl prevents natural Enderman stare aggression',
        sound: 'minecraft:entity.enderman.ambient', soundVolume: 0.3, soundPitch: 0.7, particle: 'reverse_portal',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.after.rift.targeting.cancelled === false, 'Unlearned staring permits natural Enderman targeting')
            context.expect(active.after.rift.targeting.cancelled === true && !active.after.rift.target.targetingPlayer, 'Learned pearl bearer cancels natural Enderman aggression')
            return report(['unlearned gaze naturally provokes Enderman targeting', 'learned pearl-bearing gaze cancels targeting and leaves no player target'], unlearned, active)
        },
    }],
    ['rift-ender-taglock', {
        stage: 'rift-taglock', prepare: prepare('ender_pearl'), trigger: taglock, negativeWindowTicks: 10,
        description: 'a sneak pearl strike tags a cow and throwing the taglock teleports that cow',
        sound: 'minecraft:entity.ender_eye.launch', soundVolume: 0.65, soundPitch: 1.25, particle: 'reverse_portal',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.afterTag.rift.target.health < unlearned.before.rift.target.health, 'Unlearned pearl strike is ordinary melee damage')
            context.expect(active.afterTag.rift.target.health === active.before.rift.target.health, 'Learned tagging cancels ordinary strike damage')
            context.expect(active.after.rift.target.x > 6 && active.after.location.x < 4 && !active.after.rift.teleport,
                'Tagged pearl relocates the actual cow while its thrower remains behind without a player teleport')
            context.expect(unlearned.after.rift.target.x < 4 && unlearned.after.location.x > 6
                && unlearned.after.rift.teleport?.cause === 'ENDER_PEARL', 'Control pearl teleports its thrower and leaves cow behind')
            return report(['unlearned pearl strike hurts cow and throw moves the player', 'learned tag prevents strike damage and throw moves the cow instead'], unlearned, active)
        },
    }],
    ['rift-void-skin', {
        stage: 'rift-void-skin', trigger: voidSkin, negativeWindowTicks: 10,
        description: 'a lethal real sword hit consumes a pearl and teleports the defender to safety',
        sound: 'minecraft:entity.enderman.teleport', soundVolume: 0.6, soundPitch: 1.3, particle: 'reverse_portal',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.deaths === 1, 'Unlearned lethal hit kills its defender')
            context.expect(active.deaths === 0 && active.after.health > 0 && active.pearls === 0, 'Learned lethal hit preserves life and consumes exactly one pearl')
            context.expect(Math.hypot(active.after.location.x - active.before.location.x, active.after.location.z - active.before.location.z) > 2, 'Surviving defender actually relocates to safety')
            context.expect(effect(active.after, 'resistance'), 'Successful escape grants actual resistance')
            return report(['unlearned lethal sword hit causes death', 'learned lethal hit spends pearl, preserves life, relocates defender and grants resistance'], unlearned, active)
        },
    }],
    ['rift-gate', {
        stage: 'rift-gate', prepare: prepare('ender_eye'), trigger: gate, negativeWindowTicks: 10,
        description: 'naturally binding an unbound gate eye returns its user after walking away',
        sound: 'minecraft:block.lodestone.place', soundVolume: 1, soundPitch: 0.8, particle: 'reverse_portal',
        async verify({ context, unlearned, active }) {
            context.expect(!unlearned.bound.rift.gateBound && unlearned.after.location.z > 7, 'Unlearned gestures neither bind nor recall the gate eye')
            context.expect(active.bound.rift.gateBound && active.departed.location.z > 7, 'Learned user naturally binds then walks away')
            context.expect(Math.hypot(active.after.location.x - active.before.location.x, active.after.location.z - active.before.location.z) < 1, 'Gate channel returns user to the actual bound location')
            context.expect(active.eyes === 0, 'Successful gate use consumes its eye')
            return report(['unlearned unbound eye neither binds nor recalls', 'learned binding and channel return the distant user and consume the eye'], unlearned, active)
        },
    }],
    ['rift-access', {
        stage: 'rift-access', prepare: prepare('ender_pearl'), trigger: access, negativeWindowTicks: 10,
        description: 'a naturally bound portkey opens and withdraws from a distant chest',
        sound: 'minecraft:particle.soul_escape', soundVolume: 1, soundPitch: 0.8, particle: 'portal',
        async verify({ context, unlearned, active }) {
            context.expect(!unlearned.bound.rift.accessBound && !unlearned.opened && unlearned.diamonds === 0, 'Unlearned portkey neither binds nor opens storage')
            context.expect(active.bound.rift.accessBound && active.opened && active.observedDiamonds === 4, 'Learned remote window exposes four actual source diamonds')
            context.expect(active.diamonds === 4 && (active.after.rift.chests['0'].DIAMOND ?? 0) === 0, 'Remote withdrawal moves four real diamonds out of the physical source chest')
            return report(['unlearned portkey cannot bind or remotely open chest', 'learned remote window transfers all four physical chest diamonds'], unlearned, active)
        },
    }],
    ['rift-conduit', {
        stage: 'rift-conduit', prepare: prepare('ender_pearl'), trigger: conduit, negativeWindowTicks: 10,
        description: 'natural pearl capture and binding links two chests that transfer items on close',
        sound: 'minecraft:block.conduit.activate', soundVolume: 0.6, soundPitch: 1.1, particle: 'reverse_portal',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.after.rift.chests['-2'].DIAMOND === 7 && (unlearned.after.rift.chests['2'].DIAMOND ?? 0) === 0, 'Unlearned gestures leave both physical chest inventories unchanged')
            context.expect((active.after.rift.chests['-2'].DIAMOND ?? 0) === 0 && active.after.rift.chests['2'].DIAMOND === 7, 'Closing naturally linked source transfers all seven diamonds to the physical partner chest')
            return report(['unlearned chest gestures do not transfer contents', 'natural learned linking and source close transfer exactly seven diamonds'], unlearned, active)
        },
    }],
])
