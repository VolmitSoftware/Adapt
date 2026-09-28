function effect(state, type) {
    return state.effects.find(value => value.type === `minecraft:${type}`)
}

function result(assertions, unlearned, active) {
    return { assertions, measurements: { unlearned, active } }
}

async function prepare(input, weapon) {
    input.actor.bot.clearControlStates()
    input.opponent.bot.clearControlStates()
    input.actor.bot.deactivateItem()
    input.opponent.bot.deactivateItem()
    if (weapon) await input.equip(weapon)
    await input.actor.bot.waitForTicks(24)
}

async function attack(input, attacker = input.actor, victim = input.opponent) {
    const { context, snapshot } = input
    const target = attacker.bot.entities[victim.bot.entity.id]
    context.expect(Boolean(target), 'Natural attack target is visible')
    context.expect(attacker.bot.entity.position.distanceTo(target.position) < 3, 'Natural melee target is within reach')
    await attacker.bot.lookAt(target.position.offset(0, 1, 0), true)
    const before = await snapshot(victim)
    attacker.bot.attack(target)
    let after
    await context.waitUntil(async () => {
        after = await snapshot(victim)
        return after.defense.lastHit?.sequence > (before.defense.lastHit?.sequence ?? 0)
    }, { label: 'server observes natural melee hit', timeoutMs: 2500, intervalMs: 25 })
    await attacker.bot.waitForTicks(2)
    after = await snapshot(victim)
    return { before, after, damage: before.health - after.health, hit: after.defense.lastHit }
}

async function damageIncrease({ context, unlearned, active }) {
    context.expect(unlearned.damage > 0, 'Unlearned attack deals actual damage')
    context.expect(active.damage > unlearned.damage + 0.1, 'Learned attack deals more health damage')
    return result(['natural control hit deals damage', 'learned hit increases actual health damage'], unlearned, active)
}

async function sprintHit(input) {
    const { actor, context, snapshot } = input
    const target = actor.bot.entities[input.opponent.bot.entity.id]
    await actor.bot.lookAt(target.position.offset(0, 1, 0), true)
    actor.bot.setControlState('forward', true)
    actor.bot.setControlState('sprint', true)
    try {
        await context.waitUntil(async () => (await snapshot()).defense.sprinting, {
            label: 'server recognizes natural sprint before strike', timeoutMs: 1500, intervalMs: 25,
        })
        return await attack(input)
    } finally {
        actor.bot.setControlState('forward', false)
        actor.bot.setControlState('sprint', false)
    }
}

async function pressure(input) {
    const first = await attack(input)
    await input.actor.bot.waitForTicks(12)
    const target = input.actor.bot.entities[input.opponent.bot.entity.id]
    if (input.actor.bot.entity.position.distanceTo(target.position) >= 2.8) {
        await input.actor.bot.lookAt(target.position.offset(0, 1, 0), true)
        input.actor.bot.setControlState('forward', true)
        try {
            await input.context.waitUntil(() => input.actor.bot.entity.position.distanceTo(target.position) < 2.5, {
                label: 'walk back into punch range', timeoutMs: 2000, intervalMs: 25,
            })
        } finally { input.actor.bot.setControlState('forward', false) }
    }
    const second = await attack(input)
    return { first, second }
}

async function poison(input) {
    return attack(input)
}

async function bleed(input) {
    const hit = await attack(input)
    await input.actor.bot.waitForTicks(26)
    return { ...hit, later: await input.snapshot(input.opponent) }
}

async function kill(input) {
    const before = await input.snapshot()
    const target = input.actor.bot.nearestEntity(entity => entity.name === 'cow')
    input.context.expect(Boolean(target), 'Second Wind has a living kill target')
    await input.actor.bot.lookAt(target.position.offset(0, 0.7, 0), true)
    input.actor.bot.attack(target)
    await input.context.waitUntil(() => !input.actor.bot.entities[target.id], { label: 'bare-hand hit kills cow', timeoutMs: 3000 })
    await input.actor.bot.waitForTicks(5)
    return { before, after: await input.snapshot() }
}

async function grapple(input) {
    input.actor.bot.setControlState('sneak', true)
    await input.actor.bot.waitForTicks(3)
    const hit = await attack(input)
    const origin = input.opponent.bot.entity.position.clone()
    input.actor.bot.setControlState('sneak', false)
    let farthest = 0
    const measure = () => { farthest = Math.max(farthest, input.opponent.bot.entity.position.distanceTo(origin)) }
    input.opponent.bot.on('physicsTick', measure)
    try { await input.actor.bot.waitForTicks(20) }
    finally { input.opponent.bot.off('physicsTick', measure) }
    return { ...hit, throwDistance: farthest }
}

async function hamstring(input) {
    const { opponent, actor, context, snapshot } = input
    await opponent.bot.lookAt(opponent.bot.entity.position.offset(10, 0, 0), true)
    opponent.bot.setControlState('forward', true)
    opponent.bot.setControlState('sprint', true)
    try {
        await context.waitUntil(async () => (await snapshot(opponent)).defense.sprinting, {
            label: 'target starts naturally fleeing', timeoutMs: 1500, intervalMs: 25,
        })
        return await attack(input)
    } finally { opponent.bot.clearControlStates(); actor.bot.clearControlStates() }
}

async function flow(input) {
    const before = await input.snapshot()
    const hit = await attack(input)
    return { before, hit, after: await input.snapshot() }
}

async function lunge(input) {
    const before = await input.snapshot()
    const hit = await sprintHit(input)
    const after = await input.snapshot()
    return { before, hit, after }
}

async function raiseShield(input, defender, attacker) {
    const target = defender.bot.entities[attacker.bot.entity.id]
    input.context.expect(Boolean(target), 'Shield defender sees attacker')
    await defender.bot.lookAt(target.position.offset(0, 1, 0), true)
    defender.bot.activateItem(true)
    await input.context.waitUntil(async () => (await input.snapshot(defender)).defense.blocking, {
        label: 'real shield enters blocking state', timeoutMs: 2500, intervalMs: 25,
    })
    await defender.bot.waitForTicks(6)
}

async function riposte(input) {
    await raiseShield(input, input.actor, input.opponent)
    const blocked = await attack(input, input.opponent, input.actor)
    input.actor.bot.deactivateItem()
    const counter = await attack(input)
    return { blocked, ...counter }
}

async function resolve(input) {
    await raiseShield(input, input.actor, input.opponent)
    const target = input.opponent.bot.entities[input.actor.bot.entity.id]
    await input.opponent.bot.lookAt(target.position.offset(0, 1, 0), true)
    input.opponent.bot.setControlState('forward', true)
    input.opponent.bot.setControlState('sprint', true)
    try {
        await input.context.waitUntil(async () => (await input.snapshot(input.opponent)).defense.sprinting, {
            label: 'axe attacker is sprinting', timeoutMs: 1500, intervalMs: 25,
        })
        const hit = await attack(input, input.opponent, input.actor)
        await input.actor.bot.waitForTicks(3)
        return { ...hit, resolved: await input.snapshot() }
    } finally {
        input.opponent.bot.clearControlStates()
        input.actor.bot.deactivateItem()
    }
}

async function splitter(input) {
    await raiseShield(input, input.opponent, input.actor)
    try { return await sprintHit(input) }
    finally { input.opponent.bot.deactivateItem() }
}

async function cleave(input) {
    const before = await input.snapshot()
    const target = input.actor.bot.entities[before.defense.targets.primary.entityId]
    input.context.expect(Boolean(target), 'Primary cleave target is visible')
    await input.actor.bot.lookAt(target.position.offset(0, 0.7, 0), true)
    input.actor.bot.attack(target)
    await input.context.waitUntil(async () => (await input.snapshot()).defense.targets.primary.health < before.defense.targets.primary.health, {
        label: 'natural axe strike damages primary cow', timeoutMs: 2500,
    })
    await input.actor.bot.waitForTicks(5)
    return { before: before.defense.targets, after: (await input.snapshot()).defense.targets }
}

function logBlocks(actor) {
    return [100, 101, 102].map(y => actor.bot.blockAt(actor.bot.entity.position.clone().set(2, y, 0))?.name)
}

async function bark(input) {
    const before = await input.snapshot()
    const block = input.actor.bot.blockAt(input.actor.bot.entity.position.clone().set(2, 100, 0))
    let timer
    try {
        await Promise.race([
            input.actor.bot.dig(block),
            new Promise((_, reject) => { timer = setTimeout(() => { input.actor.bot.stopDigging(); reject(new Error('Log dig timed out')) }, 10000) }),
        ])
    } finally { clearTimeout(timer) }
    await input.actor.bot.waitForTicks(5)
    return { before, after: await input.snapshot(), blocks: logBlocks(input.actor) }
}

async function chop(input) {
    const block = input.actor.bot.blockAt(input.actor.bot.entity.position.clone().set(2, 100, 0))
    await input.actor.bot.activateBlock(block)
    await input.actor.bot.waitForTicks(8)
    return { blocks: logBlocks(input.actor) }
}

export const defenseBehaviorCases = new Map([
    ['unarmed-iron-fists', {
        stage: 'defense-unarmed', sound: 'minecraft:block.anvil.land', soundVolume: 0.3, soundPitch: 1.7, particle: 'electric_spark',
        prepare, trigger: attack, verify: damageIncrease,
    }],
    ['unarmed-sucker-punch', {
        stage: 'defense-unarmed', sound: 'minecraft:entity.player.attack.strong', soundVolume: 1, soundPitch: 1.8, particle: 'sweep_attack',
        prepare, trigger: sprintHit, verify: damageIncrease,
    }],
    ['unarmed-pressure-point', {
        stage: 'defense-unarmed', sound: 'minecraft:block.pointed_dripstone.land', soundVolume: 0.6, soundPitch: 1.6, particle: 'wax_on',
        prepare, trigger: pressure,
        verify: ({ context, unlearned, active }) => {
            context.expect(!effect(unlearned.second.after, 'slowness') && !effect(unlearned.second.after, 'weakness'), 'Control punches apply no debuffs')
            context.expect(effect(active.first.after, 'slowness')?.amplifier === 0, 'First pressure strike applies Slowness I')
            context.expect(effect(active.second.after, 'slowness')?.amplifier === 1, 'Second pressure strike stacks Slowness II')
            context.expect(effect(active.second.after, 'weakness')?.amplifier === 1, 'Learned pressure strikes stack Weakness II')
            return result(['natural punches add and stack target slowness and weakness'], unlearned, active)
        },
    }],
    ['unarmed-second-wind', {
        stage: 'defense-kill', sound: 'minecraft:entity.player.levelup', soundVolume: 0.5, soundPitch: 1.7, particle: 'heart',
        prepare, trigger: kill,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.after.food === unlearned.before.food && !effect(unlearned.after, 'regeneration'), 'Control bare-hand kill restores no food or regeneration')
            context.expect(active.after.food > active.before.food && Boolean(effect(active.after, 'regeneration')), 'Learned bare-hand kill restores food and grants regeneration')
            return result(['natural bare-hand kill restores hunger and grants regeneration'], unlearned, active)
        },
    }],
    ['unarmed-grapple', {
        stage: 'defense-unarmed', sound: 'minecraft:entity.player.attack.knockback', soundVolume: 0.9, soundPitch: 1.2, particle: 'cloud',
        prepare, trigger: grapple,
        verify: ({ context, unlearned, active }) => {
            context.expect(active.throwDistance > unlearned.throwDistance + 2, 'Releasing sneak after learned grab throws target beyond normal punch knockback')
            return result(['natural sneak punch followed by release throws the victim farther than control'], unlearned, active)
        },
    }],
    ['sword-poison-blade', {
        stage: 'defense-sword', sound: 'minecraft:entity.bee.sting', soundVolume: 0.5, soundPitch: 1.3, particle: 'sneeze',
        prepare: input => prepare(input, 'wooden_sword'), trigger: poison,
        verify: ({ context, unlearned, active }) => {
            context.expect(!effect(unlearned.after, 'poison'), 'Control sword hit does not poison')
            context.expect(Boolean(effect(active.after, 'poison')), 'Learned sword strike poisons its target')
            return result(['natural learned sword hit applies target poison'], unlearned, active)
        },
    }],
    ['sword-bloody-blade', {
        stage: 'defense-sword', sound: 'minecraft:entity.player.attack.strong', soundVolume: 0.6, soundPitch: 0.8, particle: 'dust', particleColor: 0x9E1414,
        prepare: input => prepare(input, 'wooden_sword'), trigger: bleed,
        verify: ({ context, unlearned, active }) => {
            context.expect(Math.abs(unlearned.later.health - unlearned.after.health) < 0.01, 'Control target health stays constant after initial hit')
            context.expect(active.later.health < active.after.health - 0.1, 'Bleeding causes additional health loss without another attack')
            return result(['learned sword hit continues damaging target after the initial strike'], unlearned, active)
        },
    }],
    ['sword-blade-flow', {
        stage: 'defense-sword', sound: 'minecraft:block.amethyst_block.chime', soundVolume: 0.35, soundPitch: 1.02, particle: 'dust',
        prepare: input => prepare(input, 'wooden_sword'), trigger: flow,
        verify: ({ context, unlearned, active }) => {
            const key = 'minecraft:attack_speed'
            context.expect(unlearned.after.attributes[key] === unlearned.before.attributes[key], 'Control hit leaves attack speed unchanged')
            context.expect(active.after.attributes[key] > active.before.attributes[key], 'Learned hit increases attack speed')
            return result(['natural landed sword hit applies Blade Flow attack speed'], unlearned, active)
        },
    }],
    ['sword-hamstring', {
        stage: 'defense-sword', sound: 'minecraft:block.honey_block.slide', soundVolume: 0.5, soundPitch: 0.8, particle: 'dust', particleColor: 0x8FE3FF,
        prepare: input => prepare(input, 'wooden_sword'), trigger: hamstring,
        verify: ({ context, unlearned, active }) => {
            const key = 'minecraft:movement_speed'
            context.expect(active.after.attributes[key] < unlearned.after.attributes[key], 'Learned strike slows fleeing target movement attribute')
            context.expect(!active.after.defense.sprinting, 'Hamstring stops the target sprint')
            return result(['natural strike slows a sprinting target and stops its sprint'], unlearned, active)
        },
    }],
    ['sword-riposte-window', {
        stage: 'defense-riposte', sound: 'minecraft:entity.player.attack.crit', soundVolume: 0.8, soundPitch: 1.2, particle: 'sweep_attack',
        prepare: input => prepare(input, 'wooden_sword'), trigger: riposte, verify: damageIncrease,
    }],
    ['sword-lunge-strike', {
        stage: 'defense-sword', sound: 'minecraft:item.trident.riptide_1', soundVolume: 0.4, soundPitch: 1.5, particle: 'sweep_attack',
        prepare: input => prepare(input, 'wooden_sword'), trigger: lunge,
        verify: ({ context, unlearned, active }) => {
            const key = 'minecraft:entity_interaction_range'
            context.expect(unlearned.after.attributes[key] === unlearned.before.attributes[key], 'Control sprint hit does not extend reach')
            context.expect(active.after.attributes[key] > active.before.attributes[key], 'Learned sprint hit extends entity interaction reach')
            return result(['natural sprinting sword strike increases entity interaction range'], unlearned, active)
        },
    }],
    ['axe-cleave', {
        stage: 'defense-cleave', sound: 'minecraft:entity.player.attack.sweep', soundVolume: 1, soundPitch: 0.8, particle: 'crit',
        prepare: input => prepare(input, 'wooden_axe'), trigger: cleave,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.after.secondary.health === unlearned.before.secondary.health, 'Control axe hit spares adjacent cow')
            context.expect(active.after.secondary.health < active.before.secondary.health, 'Learned axe cleave damages adjacent cow')
            return result(['one natural axe strike damages the secondary target only after learning'], unlearned, active)
        },
    }],
    ['axe-shield-splitter', {
        stage: 'defense-splitter', sound: 'minecraft:item.shield.break', soundVolume: 0.8, soundPitch: 0.9, particle: 'crit', particleOffset: 0.3,
        prepare: input => prepare(input, 'wooden_axe'), trigger: splitter,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.after.defense.shieldCooldown > 0, 'Control sprint axe attack disables shield')
            context.expect(active.after.defense.shieldCooldown > unlearned.after.defense.shieldCooldown + 10, 'Learned axe strike prolongs shield disable')
            return result(['natural axe strike extends shield cooldown beyond vanilla control'], unlearned, active)
        },
    }],
    ['axe-bark-hide', {
        stage: 'defense-log', sound: 'minecraft:block.wood.place', soundVolume: 0.4, soundPitch: 0.82, particle: 'block',
        prepare: input => prepare(input, 'diamond_axe'), trigger: bark,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.after.defense.absorption === unlearned.before.defense.absorption, 'Control log break grants no absorption')
            context.expect(active.after.defense.absorption > active.before.defense.absorption, 'Learned axe log break grants absorption')
            context.expect(active.blocks[0] === 'air', 'The triggering log was actually broken')
            return result(['natural log break grants Bark Hide absorption'], unlearned, active)
        },
    }],
    ['axe-chop', {
        stage: 'defense-log', sound: 'minecraft:item.axe.strip', soundVolume: 1.25, soundPitch: 0.6, particle: 'crit',
        prepare: input => prepare(input, 'diamond_axe'), trigger: chop,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.blocks[0] === 'stripped_oak_log' && unlearned.blocks.slice(1).every(name => name === 'oak_log'), 'Control right-click only strips the selected log')
            context.expect(active.blocks.every(name => name === 'air'), 'Learned right-click fells the entire three-log column')
            return result(['natural axe right-click fells logs beyond the vanilla stripped block'], unlearned, active)
        },
    }],
    ['blocking-shieldbearers-resolve', {
        stage: 'defense-resolve', sound: 'minecraft:block.anvil.use', soundVolume: 0.5, soundPitch: 0.8, particle: 'end_rod', particleCount: 1, particleOffset: 0,
        prepare, trigger: resolve,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.resolved.defense.shieldCooldown > 0, 'Vanilla sprinting axe disables shield')
            context.expect(active.resolved.defense.shieldCooldown < unlearned.resolved.defense.shieldCooldown - 20, 'Learned Resolve shortens actual shield cooldown')
            context.expect(!effect(unlearned.resolved, 'resistance') && Boolean(effect(active.resolved, 'resistance')), 'Learned disabled shield grants resistance')
            return result(['natural axe disable recovers sooner and grants resistance'], unlearned, active)
        },
    }],
])
