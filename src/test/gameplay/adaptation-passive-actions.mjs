async function sneak({ actor }) {
    actor.bot.setControlState('sneak', false)
    await actor.bot.waitForTicks(3)
    actor.bot.setControlState('sneak', true)
    await actor.bot.waitForTicks(5)
}

async function walk({ actor }) {
    await actor.bot.look(-Math.PI / 2, 0, true)
    actor.bot.setControlState('forward', true)
    try {
        await actor.bot.waitForTicks(10)
    } finally {
        actor.bot.setControlState('forward', false)
    }
}

async function sprint({ actor }) {
    await actor.bot.look(0, 0, true)
    actor.bot.setControlState('sprint', true)
    actor.bot.setControlState('forward', true)
    try {
        await actor.bot.waitForTicks(25)
    } finally {
        actor.bot.setControlState('forward', false)
        actor.bot.setControlState('sprint', false)
    }
}

async function emptyHands({ actor }) {
    actor.bot.setQuickBarSlot(8)
    await actor.bot.waitForTicks(3)
    actor.bot.setQuickBarSlot(7)
    await actor.bot.waitForTicks(3)
}

async function meditate(input) {
    await emptyHands(input)
    await input.actor.bot.waitForTicks(165)
    await sneak(input)
}

async function digClay({ context, actor, equip }) {
    await equip('iron_shovel')
    const position = actor.bot.entity.position.clone().set(3, 100, 2)
    await context.waitUntil(() => actor.bot.blockAt(position)?.name === 'clay', {
        label: 'natural clay fixture loaded', timeoutMs: 5000,
    })
    await actor.bot.dig(actor.bot.blockAt(position))
}

async function raiseShield(input) {
    await input.actor.bot.look(Math.PI / 2, 0, true)
    await sneak(input)
    input.actor.bot.activateItem(true)
    await input.actor.bot.waitForTicks(10)
    const before = await input.snapshot()
    await input.equip('bow', input.opponent)
    for (let shot = 0; shot < 8; shot++) {
        const victim = input.opponent.bot.entities[input.actor.bot.entity.id]
        input.context.expect(Boolean(victim), 'Braced shield holder is visible to the real archer')
        await input.opponent.bot.lookAt(victim.position.offset(0, 1.4, 0), true)
        await input.opponent.bot.waitForTicks(3)
        input.opponent.bot.activateItem()
        await input.opponent.bot.waitForTicks(6)
        input.opponent.bot.deactivateItem()
        await input.actor.bot.waitForTicks(20)
        input.context.expect(input.actor.bot.health > 0, 'Low-charge projectile trials preserve the shield holder')
    }
    const after = await input.snapshot()
    input.context.expect(after.health < before.health, 'Rear arrows actually hit the braced shield holder')
    return { before, after }
}

async function titanForm(input) {
    await input.actor.bot.look(0, Math.PI / 3, true)
    await sneak(input)
    input.actor.bot._client.write('block_dig', {
        status: 6, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0,
    })
    await input.actor.bot.waitForTicks(5)
}

async function remainStill({ actor }) {
    await actor.bot.waitForTicks(5)
}

async function ghostArmor(input) {
    await walk(input)
    await input.actor.bot.waitForTicks(45)
    const charged = await input.snapshot()
    const target = input.opponent.bot.entities[input.actor.bot.entity.id]
    input.context.expect(Boolean(target), 'Ghost Armor has a real visible attacker')
    input.opponent.bot.attack(target)
    await input.actor.bot.waitForTicks(2)
    return { charged, struck: await input.snapshot() }
}

async function silentBackstab(input) {
    await sneak(input)
    await input.actor.bot.waitForTicks(15)
    const before = await input.snapshot()
    const target = input.actor.bot.entities[before.traversal.target.entityId]
    input.context.expect(Boolean(target), 'Silent Step has a real undetected mob target')
    await input.actor.bot.lookAt(target.position.offset(0, 0.7, 0), true)
    await input.actor.bot.waitForTicks(3)
    input.actor.bot.attack(target)
    await input.actor.bot.waitForTicks(4)
    return { before, after: await input.snapshot() }
}

async function brineHit(input) {
    await input.actor.bot.waitForTicks(60)
    const before = await input.snapshot()
    const target = input.opponent.bot.entities[input.actor.bot.entity.id]
    input.context.expect(Boolean(target), 'Brine Skin has a real visible attacker')
    input.opponent.bot.attack(target)
    await input.actor.bot.waitForTicks(2)
    const after = await input.snapshot()
    return { before, after, damage: before.health - after.health }
}

export const passiveBehaviorCases = new Map([
    ['agility-armor-up', {
        stage: 'agility', attribute: 'minecraft:armor', negativeWindowTicks: 45,
        description: 'sprinting builds armor plating',
        sound: 'minecraft:item.armor.equip_iron', soundVolume: 0.4, soundPitch: 0.8,
        particle: 'end_rod', trigger: sprint,
    }],
    ['stealth-ghost-armor', {
        stage: 'agility', attribute: 'minecraft:armor', negativeWindowTicks: 130,
        description: 'moving starts passive ghost armor regeneration',
        sound: 'minecraft:item.shield.break', soundVolume: 0.7, soundPitch: 1.2,
        particle: 'crit', particleCount: 4, particleOffset: 0.25, trigger: ghostArmor,
        verify({ context, unlearned, active }) {
            const consumed = value => (value.struck.stats['stealth.ghost-armor.armor-consumed'] ?? 0) - (value.charged.stats['stealth.ghost-armor.armor-consumed'] ?? 0)
            context.expect(unlearned.charged.attributes['minecraft:armor'] === 0 && consumed(unlearned) === 0, 'Unlearned movement and damage create no ghost armor charge')
            context.expect(active.charged.attributes['minecraft:armor'] > 0 && consumed(active) === 1, 'Learned movement charges armor and the actual incoming hit consumes it')
            return { assertions: ['natural movement charges ghost armor only when learned', 'real incoming damage consumes exactly one stored charge'], measurements: { unlearned, active } }
        },
    }],
    ['stealth-silent-step', {
        stage: 'traverse-qa-assassinate', attribute: 'minecraft:safe_fall_distance', negativeWindowTicks: 10,
        description: 'sneaking grants silent fall protection and an undetected backstab increases actual damage',
        sound: 'minecraft:entity.player.attack.crit', soundVolume: 0.7, soundPitch: 0.9,
        particle: 'damage_indicator', particleCount: 4, particleOffset: 0.25, trigger: silentBackstab,
        verify({ context, unlearned, active }) {
            const damage = value => value.before.traversal.target.health - value.after.traversal.target.health
            context.expect(active.before.attributes['minecraft:safe_fall_distance'] > unlearned.before.attributes['minecraft:safe_fall_distance'], 'Learned sneaking grants actual fall protection')
            context.expect(damage(unlearned) > 0 && damage(active) > damage(unlearned), 'Learned undetected fist strike deals more real mob damage')
            context.expect((active.after.stats['stealth.silent-step.backstabs'] ?? 0) > (active.before.stats['stealth.silent-step.backstabs'] ?? 0), 'Actual backstab is recorded')
            return { assertions: ['learned sneaking grants fall protection', 'natural undetected hit increases actual damage and records a backstab'], measurements: { unlearned, active } }
        },
    }],
    ['unarmed-meditation', {
        stage: 'agility', attribute: 'minecraft:max_absorption', negativeWindowTicks: 50,
        description: 'stationary sneaking with empty hands builds absorption capacity',
        sound: 'minecraft:block.amethyst_block.resonate', soundVolume: 0.4, soundPitch: 1.2,
        particle: 'end_rod', trigger: meditate,
    }],
    ['excavation-haste', {
        stage: 'excavation', attribute: 'minecraft:block_break_speed', negativeWindowTicks: 100,
        description: 'digging clay grants excavation haste',
        sound: 'minecraft:block.amethyst_block.chime', soundVolume: 0.4, soundPitch: 1.6,
        particle: 'enchant', trigger: digClay,
    }],
    ['seaborne-brine-skin', {
        stage: 'seaborne', effect: 'minecraft:regeneration', negativeWindowTicks: 100,
        description: 'remaining in water grants regeneration',
        sound: 'minecraft:item.shield.block', soundVolume: 0.35, soundPitch: 1.4,
        particle: 'glow', particleCount: 2, particleOffset: 0.2, trigger: brineHit,
        verify({ context, unlearned, active }) {
            context.expect(!unlearned.before.effects.some(effect => effect.type === 'minecraft:regeneration') && active.before.effects.some(effect => effect.type === 'minecraft:regeneration'), 'Only learned wet skin supplies regeneration')
            context.expect(unlearned.damage > 0 && active.damage < unlearned.damage, 'Learned wet skin reduces real incoming damage')
            return { assertions: ['remaining in water grants learned regeneration', 'actual incoming hit produces less health damage with wet protection'], measurements: { unlearned, active } }
        },
    }],
    ['discovery-world-armor', {
        stage: 'agility', attribute: 'minecraft:armor', negativeWindowTicks: 50,
        description: 'nearby stone supplies world armor',
        sound: 'minecraft:block.stone.place', soundVolume: 0.5, soundPitch: 0.7,
        particle: 'dust', trigger: remainStill,
    }],
    ['blocking-bastion-stance', {
        stage: 'blocking', attribute: 'minecraft:knockback_resistance', negativeWindowTicks: 50,
        description: 'sneaking with a raised shield grants knockback resistance',
        sound: 'minecraft:item.shield.block', soundVolume: 0.75, soundPitch: 0.75,
        particle: 'cloud', particleCount: 5, particleOffset: 0.1,
        prepare: async ({ context, actor, opponent }) => {
            await context.command(`/give ${opponent.bot.username} minecraft:bow 1`, /Gave /, 5000)
            await context.command(`/give ${opponent.bot.username} minecraft:arrow 8`, /Gave /, 5000)
            await context.command(`/execute at ${actor.bot.username} run fill -2 100 -1 -2 103 2 minecraft:stone`, /filled|blocks|changed|modified/i, 5000)
        },
        trigger: raiseShield,
    }],
    ['kinetics-mass-shift', {
        stage: 'agility', attribute: 'minecraft:scale', negativeWindowTicks: 45,
        description: 'sneaking, looking up, and swapping hands activates titan form',
        sound: 'minecraft:entity.iron_golem.repair', soundVolume: 0.7, soundPitch: 0.55,
        particle: 'cloud', trigger: titanForm,
    }],
    ['seaborne-speed', {
        stage: 'seaborne', attribute: 'minecraft:water_movement_efficiency', negativeWindowTicks: 100,
        description: 'moving underwater grants water movement efficiency',
        sound: 'minecraft:entity.dolphin.splash', soundVolume: 0.45, soundPitch: 1.2,
        particle: 'dust', trigger: walk,
    }],
])
