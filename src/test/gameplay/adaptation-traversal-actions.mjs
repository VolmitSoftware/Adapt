function position(actor, x, y, z) {
    const at = actor.bot.entity.position
    return at.offset(x - at.x, y - at.y, z - at.z)
}

function count(actor, name) {
    return actor.bot.inventory.items().filter(item => item.name === name).reduce((total, item) => total + item.count, 0)
}

async function prepare(input, item) {
    input.actor.bot.clearControlStates()
    input.opponent.bot.clearControlStates()
    if (item) await input.equip(item)
    await input.actor.bot.look(0, 0, true)
    await input.actor.bot.waitForTicks(6)
}

async function sprint(input, ticks = 60) {
    const before = await input.snapshot()
    const start = input.actor.bot.entity.position.clone()
    const impulses = []
    const onVelocity = packet => {
        if (packet.entityId === input.actor.bot.entity.id) impulses.push(packet.velocity)
    }
    input.actor.bot._client.on('entity_velocity', onVelocity)
    input.actor.bot.setControlState('forward', true)
    input.actor.bot.setControlState('sprint', true)
    try {
        await input.actor.bot.waitForTicks(ticks)
        const after = await input.snapshot()
        return { exhaustion: after.traversal.exhaustion - before.traversal.exhaustion,
            distance: input.actor.bot.entity.position.distanceTo(start), friction: after.attributes['minecraft:friction_modifier'],
            exhaustionEvents: after.traversal.exhaustionEvents, impulses }
    } finally {
        input.actor.bot.clearControlStates()
        input.actor.bot._client.off('entity_velocity', onVelocity)
    }
}

async function pressurePlate(input) {
    const at = position(input.actor, 0, 100, -3)
    let powered = false
    const observe = (_old, block) => {
        if (block?.position.equals(at) && [true, 'true'].includes(block.getProperties().powered)) powered = true
    }
    input.actor.bot.on('blockUpdate', observe)
    try {
        const movement = await sprint(input, 16)
        const plate = input.actor.bot.blockAt(at)
        powered ||= [true, 'true'].includes(plate.getProperties().powered)
        return { powered, distance: movement.distance, z: input.actor.bot.entity.position.z }
    } finally {
        input.actor.bot.off('blockUpdate', observe)
    }
}

async function climb(input) {
    const before = input.actor.bot.entity.position.y
    await input.actor.bot.look(0, 1.1, true)
    input.actor.bot.setControlState('forward', true)
    try {
        await input.actor.bot.waitForTicks(22)
        const state = await input.snapshot()
        return { risen: state.location.y - before, climbing: state.traversal.climbing }
    } finally {
        input.actor.bot.clearControlStates()
    }
}

async function fall(input, roll = false) {
    const before = await input.snapshot()
    input.actor.bot.setControlState('forward', true)
    try {
        await input.context.waitUntil(() => input.actor.bot.entity.position.y < 107.7,
            { label: 'player naturally walks off the eight-block platform', timeoutMs: 4000, intervalMs: 20 })
        input.actor.bot.setControlState('forward', false)
        if (roll) input.actor.bot.setControlState('sneak', true)
        await input.context.waitUntil(() => input.actor.bot.entity.onGround && input.actor.bot.entity.position.y < 101,
            { label: 'natural fall reaches the landing surface', timeoutMs: 5000, intervalMs: 20 })
        await input.actor.bot.waitForTicks(3)
        const after = await input.snapshot()
        return { damage: before.health - after.health, foodCost: before.food - after.food, landingY: after.location.y }
    } finally {
        input.actor.bot.clearControlStates()
    }
}

function verifyFall({ context, unlearned, active }) {
    context.expect(unlearned.damage > 0 && active.damage < unlearned.damage, 'Learned landing loses less health than the same unlearned fall')
    context.expect(Math.abs(active.landingY - unlearned.landingY) < 0.1, 'Both fall trials reach the same landing elevation')
    return { assertions: ['unlearned natural fall damages health', 'learned fall onto the same surface loses less health'], measurements: { unlearned, active } }
}

async function slip(input) {
    input.actor.bot.setControlState('forward', true)
    input.actor.bot.setControlState('sprint', true)
    try {
        await input.actor.bot.waitForTicks(10)
        const before = await input.snapshot()
        const start = input.actor.bot.entity.position.clone()
        input.actor.bot.setControlState('sneak', true)
        await input.actor.bot.waitForTicks(5)
        const after = await input.snapshot()
        return { pose: after.traversal.pose, foodCost: before.food - after.food,
            saturationCost: before.saturation - after.saturation,
            distance: input.actor.bot.entity.position.distanceTo(start) }
    } finally {
        input.actor.bot.clearControlStates()
    }
}

async function vault(input) {
    let peak = input.actor.bot.entity.position.y
    const startY = peak
    const onMove = () => { peak = Math.max(peak, input.actor.bot.entity.position.y) }
    input.actor.bot.on('move', onMove)
    input.actor.bot.setControlState('forward', true)
    try {
        await input.actor.bot.waitForTicks(5)
        input.actor.bot.setControlState('jump', true)
        await input.actor.bot.waitForTicks(2)
        input.actor.bot.setControlState('jump', false)
        await input.actor.bot.waitForTicks(24)
        return { height: peak - startY, z: input.actor.bot.entity.position.z }
    } finally {
        input.actor.bot.clearControlStates()
        input.actor.bot.off('move', onMove)
    }
}

async function rubber(input) {
    await input.actor.bot.waitForTicks(25)
    const before = await input.snapshot()
    input.context.expect(Number.isFinite(before.attributes['minecraft:bounciness']), 'Rubber Soul requires the server bounciness attribute')
    let peak = before.attributes['minecraft:bounciness']
    input.actor.bot.setControlState('jump', true)
    await input.actor.bot.waitForTicks(2)
    input.actor.bot.setControlState('jump', false)
    for (let tick = 0; tick < 12; tick++) {
        await input.actor.bot.waitForTicks(2)
        peak = Math.max(peak, (await input.snapshot()).attributes['minecraft:bounciness'])
    }
    return { before: before.attributes['minecraft:bounciness'], peak }
}

async function coral(input) {
    const support = input.actor.bot.blockAt(position(input.actor, 2, 99, 2))
    const at = position(input.actor, 2, 100, 2)
    const before = count(input.actor, 'tube_coral_block')
    await input.actor.bot.placeBlock(support, position(input.actor, 0, 1, 0))
    await input.context.waitUntil(() => input.actor.bot.blockAt(at)?.name === 'tube_coral_block',
        { label: 'natural placement creates live dry coral', timeoutMs: 3000 })
    await input.actor.bot.waitForTicks(160)
    return { consumed: before - count(input.actor, 'tube_coral_block'), block: input.actor.bot.blockAt(at)?.name }
}

function chestContents(window) {
    const entries = new Map()
    for (const item of window.slots.slice(0, 27)) if (item) entries.set(item.name, (entries.get(item.name) ?? 0) + item.count)
    return [...entries].sort(([a], [b]) => a.localeCompare(b))
}

async function salvage(input) {
    const chest = input.actor.bot.blockAt(position(input.actor, 2, 100, 2))
    input.context.expect(chest?.name === 'chest' && (await input.snapshot()).inWater, 'Salvage opens a real chest while submerged')
    const first = await input.actor.bot.openChest(chest)
    await input.actor.bot.waitForTicks(3)
    const contents = chestContents(first)
    first.close()
    await input.actor.bot.waitForTicks(3)
    const second = await input.actor.bot.openChest(chest)
    await input.actor.bot.waitForTicks(3)
    const reopened = chestContents(second)
    second.close()
    return { contents, reopened, total: contents.reduce((total, [, amount]) => total + amount, 0) }
}

async function fish(input) {
    const before = await input.snapshot()
    input.context.expect(before.inWater, 'Fish charm trial starts underwater')
    await input.actor.bot.waitForTicks(50)
    const after = await input.snapshot()
    return { distanceBefore: before.traversal.target.distance, distanceAfter: after.traversal.target.distance,
        luck: after.attributes['minecraft:luck'] }
}

async function ink(input) {
    const before = await input.snapshot()
    input.context.expect(before.inWater, 'Ink trial starts underwater')
    const target = input.opponent.bot.entities[input.actor.bot.entity.id]
    input.context.expect(Boolean(target), 'Ink defender is visible to its attacker')
    await input.opponent.bot.lookAt(target.position.offset(0, 1, 0), true)
    input.opponent.bot.attack(target)
    await input.actor.bot.waitForTicks(10)
    const after = await input.snapshot()
    return { damage: before.health - after.health, effects: after.traversal.target.effects }
}

async function tide(input) {
    const before = await input.snapshot()
    const start = input.actor.bot.entity.position.clone()
    input.context.expect(before.inWater, 'Tidecaller trial starts underwater')
    input.actor.bot.setControlState('sneak', true)
    await input.actor.bot.waitForTicks(10)
    input.actor.bot.setControlState('sneak', false)
    const after = await input.snapshot()
    return { distance: input.actor.bot.entity.position.distanceTo(start),
        dashes: (after.stats['seaborne.tidecaller.dashes'] ?? 0) - (before.stats['seaborne.tidecaller.dashes'] ?? 0) }
}

async function trident(input) {
    const before = await input.snapshot(input.opponent)
    const target = input.actor.bot.entities[input.opponent.bot.entity.id]
    input.context.expect(Boolean(target), 'Trident trial has a visible melee target')
    await input.actor.bot.lookAt(target.position.offset(0, 1, 0), true)
    input.actor.bot.attack(target)
    await input.actor.bot.waitForTicks(8)
    return { damage: before.health - (await input.snapshot(input.opponent)).health }
}

async function assassinate(input) {
    input.actor.bot.setControlState('sneak', true)
    await input.actor.bot.waitForTicks(12)
    const before = await input.snapshot()
    const target = input.actor.bot.entities[before.traversal.target.entityId]
    input.context.expect(Boolean(target), 'Execution trial starts with a living passive mob')
    await input.actor.bot.lookAt(target.position.offset(0, 0.7, 0), true)
    input.actor.bot.attack(target)
    await input.actor.bot.waitForTicks(8)
    const after = await input.snapshot()
    return { healthBefore: before.traversal.target.health, healthAfter: after.traversal.target.health, dead: after.traversal.target.dead }
}

export const traversalBehaviorCases = new Map([
    ['agility-featherfoot', {
        stage: 'traverse-qa-plate', negativeWindowTicks: 1, prepare: input => prepare(input), trigger: pressurePlate,
        sound: 'minecraft:item.armor.equip_leather', soundVolume: 0.3, soundPitch: 1.6, particle: 'cloud',
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.z < -3 && active.z < -3, 'Both sprint trials cross the pressure plate')
            context.expect(unlearned.powered && !active.powered, 'Only the unlearned sprint powers the actual pressure plate')
            return { assertions: ['both sprint trials cross the plate', 'learned sprint suppresses the plate power transition'], measurements: { unlearned, active } }
        },
    }],
    ['agility-ladder-slide', {
        stage: 'traverse-qa-ladder', negativeWindowTicks: 1, prepare: input => prepare(input), trigger: climb,
        sound: 'minecraft:entity.phantom.flap', soundVolume: 0.22, soundPitch: 1.2, particle: 'crit',
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.risen > 0 && active.climbing && active.risen > unlearned.risen + 1,
                'Learned upward gaze climbs a real ladder faster than the same unlearned input')
            return { assertions: ['unlearned input climbs a real ladder', 'learned upward gaze gains over one extra block in the same interval'], measurements: { unlearned, active } }
        },
    }],
    ['agility-marathoner', {
        stage: 'traverse-qa-runway', negativeWindowTicks: 1, prepare: input => prepare(input), trigger: input => sprint(input),
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.distance > 10 && active.distance > 10 && Math.abs(active.distance - unlearned.distance) < 1,
                'Both marathon trials sprint the same distance')
            context.expect(unlearned.exhaustion > 0 && active.exhaustion > 0 && active.exhaustion < unlearned.exhaustion * 0.6,
                'Learned sprinting produces materially less actual hunger exhaustion')
            return { assertions: ['both trials sprint comparable distance', 'learned sprint accumulates less than 60% of ordinary exhaustion'], measurements: { unlearned, active } }
        },
    }],
    ['agility-roll-landing', {
        stage: 'traverse-qa-roll', negativeWindowTicks: 1, prepare: input => prepare(input), trigger: input => fall(input, true),
        sound: 'minecraft:item.armor.equip_leather', soundVolume: 1, soundPitch: 0.89, particle: 'cloud',
        verify: input => {
            const evidence = verifyFall(input)
            input.context.expect(input.unlearned.foodCost === 0 && input.active.foodCost > 0, 'Only learned landing pays the roll hunger cost')
            evidence.assertions.push('roll absorption spends actual food')
            return evidence
        },
    }],
    ['agility-slipstream-slide', {
        stage: 'traverse-qa-runway', negativeWindowTicks: 1, prepare: input => prepare(input), trigger: slip,
        sound: 'minecraft:block.ladder.step', soundVolume: 0.6, soundPitch: 0.6, particle: 'cloud',
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.pose !== 'SWIMMING' && unlearned.foodCost === 0, 'Unlearned sprint-sneak does not force a prone slide')
            context.expect(active.pose === 'SWIMMING' && active.foodCost + active.saturationCost > 0 && active.distance > unlearned.distance,
                'Learned sprint-sneak adopts prone pose, pays saturation or food, and slides farther')
            return { assertions: ['unlearned sprint-sneak has no prone slide', 'learned slide changes pose, travels farther, and pays its actual stamina cost'], measurements: { unlearned, active } }
        },
    }],
    ['agility-vault', {
        stage: 'traverse-qa-vault', negativeWindowTicks: 1, prepare: input => prepare(input), trigger: vault,
        sound: 'minecraft:item.armor.equip_leather', soundVolume: 0.7, soundPitch: 1.2, particle: 'block',
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.z > -2 && active.z < -2 && active.height > 1.5,
                'Unlearned jump is stopped by the fence while learned vault clears it')
            return { assertions: ['ordinary jump cannot cross the fence', 'learned jump rises above and crosses the fence'], measurements: { unlearned, active } }
        },
    }],
    ['kinetics-rubber-soul', {
        stage: 'traverse-qa-rubber', negativeWindowTicks: 1, prepare: input => prepare(input), trigger: rubber,
        sound: 'minecraft:block.slime_block.fall', soundVolume: 0.5, soundPitch: 1.4, particle: 'item_slime',
        verify: ({ context, unlearned, active }) => {
            context.expect(Math.abs(unlearned.before) < 0.000001 && Math.abs(unlearned.peak) < 0.000001,
                'Unlearned honey-block landing leaves native bounciness at zero')
            context.expect(Math.abs(active.before - 0.5) < 0.000001 && Math.abs(active.peak - 1) < 0.000001,
                'Learned landing raises passive bounciness from 0.5 to the native cap of 1.0')
            return { assertions: ['unlearned landing leaves native bounciness at zero',
                'learned natural landing raises passive bounciness from 0.5 to the native cap of 1.0'], measurements: { unlearned, active } }
        },
    }],
    ['kinetics-soft-catch', {
        stage: 'traverse-qa-soft', negativeWindowTicks: 1, prepare: input => prepare(input), trigger: input => fall(input), verify: verifyFall,
        sound: 'minecraft:block.wool.fall', soundVolume: 0.6, soundPitch: 1.1, particle: 'cloud',
    }],
    ['kinetics-surface-skate', {
        stage: 'traverse-qa-runway', negativeWindowTicks: 1, prepare: input => prepare(input), trigger: input => sprint(input, 20),
        verify: ({ context, unlearned, active }) => {
            const nativeFriction = Number.isFinite(unlearned.friction)
            context.expect(nativeFriction ? active.friction < unlearned.friction : active.impulses.length > unlearned.impulses.length,
                'Learned sprint applies the supported native friction modifier or emits actual fallback momentum impulses')
            context.expect(active.distance > unlearned.distance, 'Learned friction reduction travels farther under the same sprint input')
            return { assertions: ['learned sprint applies native friction or real fallback impulses', 'learned friction reduction increases travel distance'], measurements: { unlearned, active } }
        },
    }],
    ['seaborne-coral-gardener', {
        stage: 'traverse-qa-coral', negativeWindowTicks: 1, prepare: input => prepare(input, 'tube_coral_block'), trigger: coral,
        sound: 'minecraft:block.coral_block.place', soundVolume: 0.5, soundPitch: 1.3, particle: 'happy_villager',
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.consumed === 1 && active.consumed === 1, 'Both coral trials consume a real placement item')
            context.expect(unlearned.block === 'dead_tube_coral_block' && active.block === 'tube_coral_block',
                'Unlearned dry coral dies while learned placement remains alive over the same interval')
            return { assertions: ['both trials naturally place dry coral', 'learned coral survives after unlearned coral has died'], measurements: { unlearned, active } }
        },
    }],
    ['seaborne-deep-salvager', {
        stage: 'traverse-qa-salvage', negativeWindowTicks: 1, prepare: input => prepare(input), trigger: salvage,
        sound: 'minecraft:block.conduit.activate', soundVolume: 0.5, soundPitch: 1.3, particle: 'end_rod', particleCount: 6,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.total === 0, 'Unlearned submerged chest stays empty')
            context.expect(active.total >= 4 && JSON.stringify(active.contents) === JSON.stringify(active.reopened),
                'Learned submerged chest gains real treasure once and reopening adds nothing')
            return { assertions: ['unlearned opening yields no treasure', 'learned opening adds at least four real items exactly once'], measurements: { unlearned, active } }
        },
    }],
    ['seaborne-fish-whisperer', {
        stage: 'traverse-qa-fish', negativeWindowTicks: 1, prepare: input => prepare(input), trigger: fish,
        particle: 'bubble', particleCount: 2, particleOffset: 0.1,
        verify: ({ context, unlearned, active }) => {
            context.expect(Math.abs(unlearned.distanceAfter - unlearned.distanceBefore) < 0.3, 'Unlearned idle fish is not pulled toward the swimmer')
            context.expect(active.distanceAfter < active.distanceBefore - 0.5 && active.luck > unlearned.luck,
                'Learned swimmer pulls a real fish closer and receives a luck bonus')
            return { assertions: ['unlearned underwater presence does not pull fish', 'learned presence moves fish toward the player and increases luck'], measurements: { unlearned, active } }
        },
    }],
    ['seaborne-ink-veil', {
        stage: 'traverse-qa-ink', negativeWindowTicks: 1, prepare: input => prepare(input), trigger: ink,
        sound: 'minecraft:entity.dolphin.splash', soundVolume: 0.6, soundPitch: 0.6, particle: 'squid_ink',
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.damage > 0 && active.damage > 0, 'Both underwater trials take a real opponent hit')
            context.expect(unlearned.effects.length === 0 && active.effects.some(effect => effect.type === 'minecraft:blindness'),
                'Only the learned underwater hit blinds the nearby hostile mob')
            return { assertions: ['unlearned underwater damage applies no blindness', 'learned underwater damage blinds a nearby hostile mob'], measurements: { unlearned, active } }
        },
    }],
    ['seaborne-tidecaller', {
        stage: 'traverse-qa-tide', negativeWindowTicks: 1, prepare: input => prepare(input), trigger: tide,
        sound: 'minecraft:item.trident.riptide_2', soundVolume: 0.75, soundPitch: 1.2, particle: 'splash',
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.dashes === 0 && active.dashes === 1 && active.distance > unlearned.distance + 2,
                'Learned underwater sneak activates one dash and moves the player over two extra blocks')
            return { assertions: ['unlearned underwater sneak does not dash', 'learned underwater sneak launches one real movement burst'], measurements: { unlearned, active } }
        },
    }],
    ['seaborne-trident-mastery', {
        stage: 'traverse-qa-trident', negativeWindowTicks: 1, prepare: input => prepare(input, 'trident'), trigger: trident,
        sound: 'minecraft:item.trident.hit', soundVolume: 0.6, soundPitch: 1.1, particle: 'bubble',
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.damage > 0 && active.damage > unlearned.damage, 'Learned trident melee deals more actual health damage')
            return { assertions: ['unlearned trident hit deals ordinary damage', 'learned trident hit deals additional damage'], measurements: { unlearned, active } }
        },
    }],
    ['stealth-assassinate', {
        stage: 'traverse-qa-assassinate', prerequisites: [{ name: 'stealth-silent-step', level: 1 }], negativeWindowTicks: 1,
        prepare: input => prepare(input), trigger: assassinate,
        sound: 'minecraft:entity.player.attack.crit', soundVolume: 1, soundPitch: 0.6, particle: 'soul',
        verify: ({ context, unlearned, active }) => {
            context.expect(!unlearned.dead && unlearned.healthAfter > 0 && unlearned.healthAfter < unlearned.healthBefore,
                'Undetected control punch with Silent Step damages but does not execute the mob')
            context.expect(active.healthBefore === unlearned.healthBefore && active.dead,
                'Learned Assassinate executes the same healthy mob in one undetected punch')
            return { assertions: ['Silent Step alone does not execute the target', 'Assassinate executes the same target with one natural punch'], measurements: { unlearned, active } }
        },
    }],
    ['stealth-shadowmeld', {
        stage: 'traverse-qa-shadowmeld', prerequisites: [{ name: 'stealth-silent-step', level: 1 }], negativeWindowTicks: 30,
        prepare: input => prepare(input), effect: 'minecraft:invisibility',
        sound: 'minecraft:block.sculk_sensor.clicking_stop', soundVolume: 0.5, soundPitch: 0.6, particle: 'squid_ink',
        trigger: async ({ actor }) => {
            actor.bot.setControlState('sneak', true)
            await actor.bot.waitForTicks(20)
        },
    }],
])
