async function sneak({ actor }) {
    actor.bot.setControlState('sneak', false)
    await actor.bot.waitForTicks(3)
    actor.bot.setControlState('sneak', true)
    await actor.bot.waitForTicks(4)
}

async function equipAndSneak(input, item) {
    await input.equip(item)
    await sneak(input)
}

async function windUp({ actor, snapshot }) {
    await actor.bot.look(0, 0, true)
    actor.bot.setControlState('forward', true)
    actor.bot.setControlState('sprint', true)
    try {
        const start = actor.bot.entity.position.clone()
        await actor.bot.waitForTicks(80)
        const state = await snapshot()
        return { speed: state.attributes['minecraft:movement_speed'], distance: actor.bot.entity.position.distanceTo(start) }
    } finally {
        actor.bot.clearControlStates()
    }
}

async function snatch(input) {
    const count = () => input.actor.bot.inventory.items().filter(item => item.name === 'diamond').reduce((total, item) => total + item.count, 0)
    const before = count()
    await sneak(input)
    await input.actor.bot.waitForTicks(12)
    return { before, after: count(), location: (await input.snapshot()).location }
}

async function recoverMeal(input) {
    await input.equip('wooden_sword')
    await sneak(input)
    const cow = input.actor.bot.nearestEntity(entity => entity.name === 'cow')
    input.context.expect(Boolean(cow), 'Sneaking recovery has a living kill target')
    const before = await input.snapshot()
    await input.actor.bot.lookAt(cow.position.offset(0, 0.7, 0), true)
    input.actor.bot.attack(cow)
    await input.context.waitUntil(() => !input.actor.bot.entities[cow.id], { label: 'sneaking sword hit kills cow', timeoutMs: 3000 })
    await input.actor.bot.waitForTicks(4)
    const after = await input.snapshot()
    return { foodBefore: before.food, foodAfter: after.food, saturationBefore: before.saturation, saturationAfter: after.saturation }
}

async function smoke(input) {
    await input.equip('gunpowder')
    await input.actor.bot.look(0, -Math.PI / 2, true)
    await sneak(input)
}

async function kipUp({ context, actor, opponent }) {
    await actor.bot.look(0, 0, true)
    actor.bot.setControlState('forward', true)
    await actor.bot.waitForTicks(3)
    actor.bot.setControlState('forward', false)
    await actor.bot.waitForTicks(3)
    const target = opponent.bot.entities[actor.bot.entity.id]
    context.expect(Boolean(target), 'Kip Up actor is visible to the opponent')
    await opponent.bot.lookAt(target.position.offset(0, 1, 0), true)
    const health = actor.bot.health
    opponent.bot.attack(target)
    actor.bot.setControlState('jump', true)
    await actor.bot.waitForTicks(5)
    actor.bot.setControlState('jump', false)
    context.expect(actor.bot.health < health, 'Kip Up trial receives a real opponent hit')
}

async function fallToggle(input, mace) {
    const { context, actor, snapshot } = input
    if (mace) await input.equip('mace')
    await actor.bot.look(0, 0, true)
    actor.bot.setControlState('forward', true)
    try {
        await context.waitUntil(() => !actor.bot.entity.onGround && actor.bot.entity.position.y < 119.7, {
            label: 'player walks off elevated platform', timeoutMs: 5000, intervalMs: 20,
        })
        await actor.bot.waitForTicks(7)
        const before = await snapshot()
        actor.bot.setControlState('sneak', true)
        await actor.bot.waitForTicks(2)
        const after = await snapshot()
        return {
            gravityBefore: before.attributes['minecraft:gravity'], gravityAfter: after.attributes['minecraft:gravity'],
            beforeY: before.location.y, afterY: after.location.y,
        }
    } finally {
        actor.bot.clearControlStates()
    }
}

async function airDash(input) {
    const { context, actor, snapshot } = input
    await actor.bot.look(0, 0, true)
    actor.bot.setControlState('forward', true)
    actor.bot.setControlState('sprint', true)
    try {
        await actor.bot.waitForTicks(8)
        actor.bot.setControlState('jump', true)
        await context.waitUntil(() => !actor.bot.entity.onGround, { label: 'natural sprint jump takes off', timeoutMs: 3000, intervalMs: 10 })
        actor.bot.setControlState('jump', false)
        const before = await snapshot()
        const start = actor.bot.entity.position.clone()
        actor.bot.swingArm('right')
        await actor.bot.waitForTicks(5)
        const after = await snapshot()
        return {
            distance: actor.bot.entity.position.distanceTo(start),
            dashes: (after.stats['agility.air-dash.dashes'] ?? 0) - (before.stats['agility.air-dash.dashes'] ?? 0),
            saturationBefore: before.saturation, saturationAfter: after.saturation,
        }
    } finally {
        actor.bot.clearControlStates()
    }
}

async function hydroJet(input) {
    const { actor, snapshot } = input
    await actor.bot.look(0, 0, true)
    actor.bot.setControlState('forward', true)
    actor.bot.setControlState('sprint', true)
    try {
        await actor.bot.waitForTicks(12)
        const before = await snapshot()
        const start = actor.bot.entity.position.clone()
        actor.bot.setControlState('sneak', true)
        await actor.bot.waitForTicks(5)
        const after = await snapshot()
        return {
            distance: actor.bot.entity.position.distanceTo(start),
            jets: (after.stats['seaborne.hydro-jet.jets'] ?? 0) - (before.stats['seaborne.hydro-jet.jets'] ?? 0),
        }
    } finally {
        actor.bot.clearControlStates()
    }
}

function verifyGravity({ context, unlearned, active }) {
    context.expect(unlearned.gravityAfter === unlearned.gravityBefore, 'Unlearned midair sneak preserves ordinary gravity')
    context.expect(active.gravityBefore === unlearned.gravityBefore, 'Both falls begin with ordinary gravity')
    context.expect(active.gravityAfter > active.gravityBefore, 'Learned midair sneak increases gravity during a real fall')
    context.expect(active.afterY < active.beforeY, 'Player descends after activating the dive')
    return { assertions: ['unlearned midair sneak preserves gravity', 'learned midair sneak increases gravity while the player falls'], measurements: { unlearned, active } }
}

export const movementBehaviorCases = new Map([
    ['agility-wind-up', {
        stage: 'move-runway', negativeWindowTicks: 1,
        sound: 'minecraft:entity.blaze.shoot', soundVolume: 0.3, soundPitch: 0.7, particle: 'flame',
        trigger: windUp,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.distance > 10 && active.distance > 10, 'Both players naturally sprint along the runway')
            context.expect(active.speed > unlearned.speed + 0.01, 'Learned sprinting increases movement speed beyond ordinary sprinting')
            context.expect(active.distance > unlearned.distance + 1, 'The speed bonus produces more distance in the same sprint interval')
            return { assertions: ['both trials sprint for 80 ticks', 'learned sprint gains speed and covers more ground'], measurements: { unlearned, active } }
        },
    }],
    ['agility-air-dash', {
        stage: 'move-runway', negativeWindowTicks: 1,
        sound: 'minecraft:entity.phantom.flap', soundVolume: 0.6, soundPitch: 1.4, particle: 'cloud',
        trigger: airDash,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.dashes === 0, 'Unlearned sprint-jump swing does not activate a dash')
            context.expect(active.dashes === 1 && active.distance > unlearned.distance + 0.5,
                'Learned sprint-jump swing activates one dash and travels farther')
            context.expect(active.saturationAfter < active.saturationBefore, 'Air dash pays its hunger cost')
            return { assertions: ['unlearned sprint-jump swing does not dash', 'learned sprint-jump swing travels farther and pays hunger cost'], measurements: { unlearned, active } }
        },
    }],
    ['agility-kip-up', {
        stage: 'agility', attribute: 'minecraft:movement_speed', negativeWindowTicks: 12,
        sound: 'minecraft:entity.player.attack.sweep', soundVolume: 0.6, soundPitch: 1.3, particle: 'crit',
        trigger: kipUp,
    }],
    ['kinetics-heavy-frame', {
        stage: 'move-mace', attribute: 'minecraft:knockback_resistance', negativeWindowTicks: 20,
        sound: 'minecraft:block.chain.place', soundVolume: 0.35, soundPitch: 0.7, particle: 'cloud',
        trigger: input => equipAndSneak(input, 'mace'),
    }],
    ['kinetics-phalanx-reach', {
        stage: 'move-spear', attribute: 'minecraft:entity_interaction_range', negativeWindowTicks: 50,
        trigger: async input => {
            await input.equip('iron_spear')
            await input.actor.bot.waitForTicks(5)
        },
    }],
    ['kinetics-terminal-toggle', {
        stage: 'move-fall', negativeWindowTicks: 1,
        trigger: input => fallToggle(input, false), verify: verifyGravity,
    }],
    ['kinetics-meteor-cadence', {
        stage: 'move-meteor', negativeWindowTicks: 1,
        trigger: input => fallToggle(input, true), verify: verifyGravity,
    }],
    ['seaborne-pressure-diver', {
        stage: 'move-pressure', attribute: 'minecraft:max_absorption', negativeWindowTicks: 40,
        sound: 'minecraft:block.conduit.activate', soundVolume: 0.5, soundPitch: 1.4, particle: 'bubble',
        trigger: async ({ actor }) => {
            await actor.bot.look(0, 0, true)
            actor.bot.setControlState('forward', true)
            await actor.bot.waitForTicks(5)
            actor.bot.setControlState('forward', false)
        },
    }],
    ['seaborne-hydro-jet', {
        stage: 'move-hydro', negativeWindowTicks: 1,
        sound: 'minecraft:entity.dolphin.splash', soundVolume: 0.6, soundPitch: 1.3, particle: 'splash',
        trigger: hydroJet,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.jets === 0, 'Unlearned underwater sneak does not activate a jet')
            context.expect(active.jets === 1 && active.distance > unlearned.distance + 0.5,
                'Learned swimming sneak activates a jet and propels the swimmer farther')
            return { assertions: ['unlearned swimming sneak does not jet', 'learned swimming sneak activates one jet and covers more distance'], measurements: { unlearned, active } }
        },
    }],
    ['stealth-smoke-pellet', {
        stage: 'move-smoke', effect: 'minecraft:invisibility', negativeWindowTicks: 20,
        sound: 'minecraft:entity.witch.throw', soundVolume: 0.4, soundPitch: 1.4, particle: 'smoke',
        trigger: smoke,
    }],
    ['stealth-snatch', {
        stage: 'move-snatch', negativeWindowTicks: 1,
        sound: 'minecraft:block.amethyst_block.resonate', soundVolume: 0.4, soundPitch: 1.5, particle: 'enchant',
        trigger: snatch,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.before === 0 && unlearned.after === 0, 'Unlearned stationary sneak cannot pick up the distant diamond')
            context.expect(active.before === 0 && active.after === 1, 'Learned stationary sneak retrieves the distant diamond')
            context.expect(Math.abs(active.location.x - 0.5) < 0.1 && Math.abs(active.location.z - 0.5) < 0.1,
                'Snatch retrieves the item without walking into pickup range')
            return { assertions: ['unlearned sneak leaves the distant item', 'learned sneak transfers exactly one diamond without moving'], measurements: { unlearned, active } }
        },
    }],
    ['stealth-umbral-recovery', {
        stage: 'move-umbral', negativeWindowTicks: 1,
        sound: 'minecraft:entity.generic.eat', soundVolume: 0.35, soundPitch: 1.4, particle: 'happy_villager',
        trigger: recoverMeal,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.foodAfter === unlearned.foodBefore, 'Unlearned sneaking kill does not restore hunger')
            context.expect(active.foodBefore === unlearned.foodBefore && active.foodAfter > active.foodBefore,
                'Learned sneaking kill restores hunger from the same starting level')
            context.expect(active.saturationAfter > active.saturationBefore, 'Learned sneaking kill also restores saturation')
            return { assertions: ['unlearned sneaking kill leaves hunger unchanged', 'learned sneaking kill restores food and saturation'], measurements: { unlearned, active } }
        },
    }],
])
