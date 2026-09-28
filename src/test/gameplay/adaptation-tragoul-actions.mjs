function count(actor, name) {
    return actor.bot.inventory.items().filter(item => item.name === name).reduce((total, item) => total + item.count, 0)
}

async function prepare(input, item, attacker = false) {
    input.actor.bot.clearControlStates()
    input.opponent.bot.clearControlStates()
    if (item) await input.equip(item, attacker ? input.opponent : input.actor)
    await input.actor.bot.waitForTicks(14)
}

async function defenderHit(input) {
    const before = await input.snapshot()
    const attackerBefore = await input.snapshot(input.opponent)
    const bonesBefore = count(input.actor, 'bone')
    const target = input.opponent.bot.entities[input.actor.bot.entity.id]
    input.context.expect(Boolean(target), 'Ordinary attacker sees the Tragoul defender')
    await input.opponent.bot.lookAt(target.position.offset(0, 1, 0), true)
    input.opponent.bot.attack(target)
    await input.actor.bot.waitForTicks(12)
    const after = await input.snapshot()
    const attackerAfter = await input.snapshot(input.opponent)
    return { defenderBefore: before.health, defenderAfter: after.health,
        attackerBefore: attackerBefore.health, attackerAfter: attackerAfter.health,
        consumedBones: bonesBefore - count(input.actor, 'bone'), attackerEffects: attackerAfter.effects }
}

async function siphon(input) {
    const before = await input.snapshot()
    const victimBefore = await input.snapshot(input.opponent)
    const target = input.actor.bot.entities[input.opponent.bot.entity.id]
    input.context.expect(Boolean(target), 'Soul Siphon has a real melee target')
    await input.actor.bot.lookAt(target.position.offset(0, 1, 0), true)
    input.actor.bot.attack(target)
    await input.actor.bot.waitForTicks(10)
    return { healthBefore: before.health, healthAfter: (await input.snapshot()).health,
        victimBefore: victimBefore.health, victimAfter: (await input.snapshot(input.opponent)).health }
}

async function primaryEntity(input) {
    const state = await input.snapshot()
    const id = state.tragoul.targets.primary.entityId
    const entity = input.actor.bot.entities[id]
    input.context.expect(Boolean(entity), 'Primary Tragoul fixture entity is visible')
    return entity
}

async function strikePrimary(input) {
    const target = await primaryEntity(input)
    await input.actor.bot.lookAt(target.position.offset(0, 0.8, 0), true)
    input.actor.bot.attack(target)
}

async function secondaryDamage(input, kill) {
    const before = await input.snapshot()
    await strikePrimary(input)
    await input.actor.bot.waitForTicks(kill ? 45 : 15)
    const after = await input.snapshot()
    input.context.expect(kill ? after.tragoul.targets.primary.dead : after.tragoul.targets.primary.health < before.tragoul.targets.primary.health,
        kill ? 'Natural sword strike kills the source mob' : 'Natural sword strike damages the primary mob')
    return { ownerBefore: before.health, ownerAfter: after.health,
        secondaryBefore: before.tragoul.targets.secondary.health, secondaryAfter: after.tragoul.targets.secondary.health,
        primaryDead: after.tragoul.targets.primary.dead }
}

function verifySecondary({ context, unlearned, active }) {
    context.expect(unlearned.secondaryAfter === unlearned.secondaryBefore, 'Unlearned attack leaves the unstruck secondary mob unharmed')
    context.expect(active.secondaryBefore === unlearned.secondaryBefore && active.secondaryAfter < active.secondaryBefore,
        'Learned attack damages the separate unstruck mob')
    return { assertions: ['unlearned attack leaves the secondary mob unharmed', 'learned attack damages the secondary mob without another player strike'], measurements: { unlearned, active } }
}

async function lastRites(input) {
    let deaths = 0
    const onDeath = () => { deaths++ }
    input.actor.bot.on('death', onDeath)
    try {
        const before = await input.snapshot()
        input.context.expect(before.health === 2, 'Lethal-hit trial starts at two health points')
        const target = input.opponent.bot.entities[input.actor.bot.entity.id]
        input.context.expect(Boolean(target), 'Lethal-hit defender is visible')
        await input.opponent.bot.lookAt(target.position.offset(0, 1, 0), true)
        input.opponent.bot.attack(target)
        await input.actor.bot.waitForTicks(12)
        if (deaths > 0) {
            await input.context.waitUntil(() => input.actor.bot.health > 0, { label: 'unlearned player respawns after lethal control hit', timeoutMs: 5000 })
        }
        const after = await input.snapshot()
        return { deaths, health: after.health, effects: after.effects }
    } finally {
        input.actor.bot.off('death', onDeath)
    }
}

async function plague(input) {
    const target = await primaryEntity(input)
    const initial = await input.snapshot()
    input.context.expect(initial.tragoul.targets.secondary.effects.length === 0, 'Secondary plague target begins without potion effects')
    await input.equip('bow')
    await input.actor.bot.lookAt(target.position.offset(0, 0.6, 0), true)
    input.actor.bot.activateItem()
    await input.actor.bot.waitForTicks(12)
    input.actor.bot.deactivateItem()
    await input.context.waitUntil(async () => (await input.snapshot()).tragoul.targets.primary.effects.some(effect => effect.type === 'minecraft:poison'),
        { label: 'natural tipped arrow poisons the primary mob', timeoutMs: 4000 })
    await input.actor.bot.waitForTicks(5)
    const poisoned = await input.snapshot()
    input.context.expect(poisoned.tragoul.targets.secondary.effects.length === 0, 'Tipped arrow leaves the secondary target unpoisoned')
    await input.equip('netherite_sword')
    await input.actor.bot.waitForTicks(14)
    for (let hit = 0; hit < 3; hit++) {
        if ((await input.snapshot()).tragoul.targets.primary.dead) break
        await strikePrimary(input)
        await input.actor.bot.waitForTicks(15)
    }
    const after = await input.snapshot()
    input.context.expect(after.tragoul.targets.primary.dead, 'Player naturally kills the poisoned mob')
    return { secondaryEffects: after.tragoul.targets.secondary.effects,
        primaryPoison: poisoned.tragoul.targets.primary.effects.find(effect => effect.type === 'minecraft:poison') }
}

async function summon(input) {
    const before = await input.snapshot()
    const bonesBefore = count(input.actor, 'bone')
    input.actor.bot.setControlState('sneak', true)
    await input.actor.bot.waitForTicks(3)
    await input.actor.bot.look(0, 0, true)
    input.actor.bot.activateItem()
    await input.actor.bot.waitForTicks(16)
    input.actor.bot.deactivateItem()
    input.actor.bot.setControlState('sneak', false)
    const summoned = await input.snapshot()
    await strikePrimary(input)
    await input.actor.bot.waitForTicks(5)
    const afterStrike = await input.snapshot()
    await input.actor.bot.waitForTicks(120)
    const after = await input.snapshot()
    return { consumedBones: bonesBefore - count(input.actor, 'bone'), servants: summoned.tragoul.servants,
        maxHealthBefore: before.attributes['minecraft:max_health'], maxHealthAfter: summoned.attributes['minecraft:max_health'],
        targetAfterStrike: afterStrike.tragoul.targets.primary.health, targetAfterServant: after.tragoul.targets.primary.health,
        lastDamagerServant: after.tragoul.targets.primary.lastDamagerServant ?? false }
}

async function sense(input) {
    const target = await primaryEntity(input)
    await strikePrimary(input)
    await input.actor.bot.waitForTicks(50)
    const state = await input.snapshot()
    const clientTarget = input.actor.bot.entities[target.id]
    input.context.expect(Boolean(clientTarget) && !state.tragoul.targets.primary.dead, 'Wounded sense target remains alive and visible')
    return { health: state.tragoul.targets.primary.health, glowing: Boolean(clientTarget.metadata[0] & 0x40) }
}

export const tragoulBehaviorCases = new Map([
    ['tragoul-thorns', {
        stage: 'tragoul-qa-defend', negativeWindowTicks: 1,
        prepare: input => prepare(input, 'wooden_sword', true), trigger: defenderHit,
        sound: 'minecraft:entity.player.attack.crit', soundVolume: 0.5, soundPitch: 1.4, particle: 'dust', particleCount: 4,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.defenderAfter < unlearned.defenderBefore && unlearned.attackerAfter === unlearned.attackerBefore,
                'Unlearned sword hit hurts only its defender')
            context.expect(active.defenderAfter < active.defenderBefore && active.attackerAfter < active.attackerBefore,
                'Learned defender reflects real health damage to the attacker')
            return { assertions: ['unlearned attacker loses no health', 'learned defender reflects damage from a natural sword hit'], measurements: { unlearned, active } }
        },
    }],
    ['tragoul-healing', {
        stage: 'tragoul-qa-defend', negativeWindowTicks: 1,
        prepare: input => prepare(input, 'wooden_sword', true), trigger: defenderHit,
        sound: 'minecraft:block.respawn_anchor.charge', soundVolume: 0.25, soundPitch: 1.6, particle: 'dust_color_transition',
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.attackerAfter === unlearned.attackerBefore, 'Unlearned defender drains no attacker health')
            context.expect(active.attackerAfter < active.attackerBefore && active.defenderAfter > unlearned.defenderAfter,
                'Learned defender drains attacker health and restores its own health after the same hit')
            return { assertions: ['unlearned hit does not drain', 'learned hit transfers attacker health to the defender'], measurements: { unlearned, active } }
        },
    }],
    ['tragoul-soul-siphon', {
        stage: 'tragoul-qa-siphon', negativeWindowTicks: 1,
        prepare: input => prepare(input, 'wooden_sword'), trigger: siphon,
        sound: 'minecraft:particle.soul_escape', soundVolume: 0.3, soundPitch: 1.4, particle: 'soul', particleCount: 3,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.healthAfter === unlearned.healthBefore && unlearned.victimAfter < unlearned.victimBefore,
                'Unlearned sword attack damages its target without healing the attacker')
            context.expect(active.healthAfter > active.healthBefore && active.victimAfter < active.victimBefore,
                'Learned sword attack converts actual target damage into healing')
            return { assertions: ['unlearned sword damage grants no healing', 'learned sword damage heals its injured attacker'], measurements: { unlearned, active } }
        },
    }],
    ['tragoul-marrow-armor', {
        stage: 'tragoul-qa-marrow', negativeWindowTicks: 1,
        prepare: input => prepare(input, 'wooden_sword', true), trigger: defenderHit,
        sound: 'minecraft:block.bone_block.place', soundVolume: 0.5, soundPitch: 0.8, particle: 'block',
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.consumedBones === 0 && unlearned.defenderAfter < unlearned.defenderBefore,
                'Unlearned defender takes damage without spending bones')
            const unlearnedDamage = unlearned.defenderBefore - unlearned.defenderAfter
            const activeDamage = active.defenderBefore - active.defenderAfter
            context.expect(active.consumedBones === 1 && activeDamage > 0 && activeDamage < unlearnedDamage,
                'Learned defender consumes one bone and absorbs part of the same sword hit')
            return { assertions: ['unlearned hit spends no bone', 'learned hit spends one bone and loses less health'], measurements: { unlearned, active } }
        },
    }],
    ['tragoul-curse-of-frailty', {
        stage: 'tragoul-qa-defend', negativeWindowTicks: 1,
        prepare: input => prepare(input, 'wooden_sword', true), trigger: defenderHit,
        sound: 'minecraft:entity.elder_guardian.curse', soundVolume: 0.35, soundPitch: 1.7, particle: 'warped_spore',
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.attackerEffects.length === 0, 'Unlearned sword attacker receives no debuffs')
            context.expect(active.attackerEffects.some(effect => effect.type === 'minecraft:weakness' && effect.amplifier === 1)
                && active.attackerEffects.some(effect => effect.type === 'minecraft:slowness'), 'Learned defender curses the actual attacker with weakness II and slowness')
            return { assertions: ['unlearned attacker has no debuffs', 'learned defender applies weakness II and slowness to its attacker'], measurements: { unlearned, active } }
        },
    }],
    ['tragoul-globe', {
        stage: 'tragoul-qa-globe', negativeWindowTicks: 1,
        prepare: input => prepare(input, 'wooden_sword'), trigger: input => secondaryDamage(input, false), verify: verifySecondary,
        sound: 'minecraft:entity.wither.shoot', soundVolume: 0.4, soundPitch: 1.6, particle: 'soul', particleCount: 1,
    }],
    ['tragoul-corpse-explosion', {
        stage: 'tragoul-qa-nova', negativeWindowTicks: 1,
        prepare: input => prepare(input, 'wooden_sword'), trigger: input => secondaryDamage(input, true), verify: verifySecondary,
        sound: 'minecraft:entity.generic.explode', soundVolume: 0.55, soundPitch: 1.35, particle: 'soul',
    }],
    ['tragoul-lance', {
        stage: 'tragoul-qa-lance', negativeWindowTicks: 1,
        prepare: input => prepare(input, 'wooden_sword'), trigger: input => secondaryDamage(input, true),
        sound: 'minecraft:item.trident.riptide_1', soundVolume: 0.6, soundPitch: 1.3, particle: 'sculk_soul',
        verify: input => {
            const evidence = verifySecondary(input)
            input.context.expect(input.active.ownerAfter < input.active.ownerBefore && input.unlearned.ownerAfter === input.unlearned.ownerBefore,
                'Lance hits pay their real owner health cost')
            evidence.assertions.push('learned lance also spends owner health')
            return evidence
        },
    }],
    ['tragoul-last-rites', {
        stage: 'tragoul-qa-rites', negativeWindowTicks: 1,
        prepare: input => prepare(input, 'wooden_sword', true), trigger: lastRites,
        sound: 'minecraft:item.totem.use', soundVolume: 0.8, soundPitch: 1.5, particle: 'sculk_soul',
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.deaths === 1, 'Unlearned lethal sword hit kills the player')
            context.expect(active.deaths === 0 && active.health === 1, 'Learned player survives the same lethal hit at exactly one health point')
            context.expect(active.effects.some(effect => effect.type === 'minecraft:invisibility') && active.effects.some(effect => effect.type === 'minecraft:resistance'),
                'Saved player receives both temporary spirit effects')
            return { assertions: ['unlearned lethal hit causes death', 'learned lethal hit leaves one health point with invisibility and resistance'], measurements: { unlearned, active } }
        },
    }],
    ['tragoul-plague-bearer', {
        stage: 'tragoul-qa-plague', negativeWindowTicks: 1,
        prepare: input => prepare(input), trigger: plague,
        sound: 'minecraft:entity.zombie.infect', soundVolume: 0.6, soundPitch: 1.4, particle: 'spore_blossom_air',
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.secondaryEffects.length === 0, 'Unlearned poisoned kill does not infect the separate mob')
            context.expect(active.secondaryEffects.some(effect => effect.type === 'minecraft:poison' && effect.amplifier > active.primaryPoison.amplifier),
                'Learned poisoned kill infects the unstruck mob with amplified poison')
            return { assertions: ['unlearned poisoned kill does not spread', 'learned natural poisoned kill spreads stronger poison to a separate mob'], measurements: { unlearned, active } }
        },
    }],
    ['tragoul-skeletal-servant', {
        stage: 'tragoul-qa-servant', negativeWindowTicks: 1,
        prepare: input => prepare(input, 'bone'), trigger: summon,
        sound: 'minecraft:block.bone_block.place', soundVolume: 0.6, soundPitch: 0.7, particle: 'soul',
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.consumedBones === 0 && unlearned.servants.length === 0 && unlearned.targetAfterServant === unlearned.targetAfterStrike,
                'Unlearned bone interaction summons nothing and causes no later target damage')
            context.expect(active.consumedBones === 3 && active.servants.length === 1 && active.maxHealthAfter === active.maxHealthBefore - 2,
                'Learned bone interaction summons one servant for three bones and two maximum health')
            context.expect(active.lastDamagerServant && active.targetAfterServant < unlearned.targetAfterStrike,
                'The summoned servant naturally attacks and damages the owner-designated mob')
            return { assertions: ['unlearned bones cannot summon', 'learned summon pays bone and maximum-health costs', 'real servant combat damages its designated target'], measurements: { unlearned, active } }
        },
    }],
    ['tragoul-death-sense', {
        stage: 'tragoul-qa-sense', negativeWindowTicks: 1,
        prepare: input => prepare(input), trigger: sense,
        sound: 'minecraft:block.note_block.bass', soundVolume: 0.2, soundPitch: 0.58, particle: 'sculk_soul',
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.health > 0 && !unlearned.glowing, 'Unlearned wounded target has no client glow flag')
            context.expect(active.health > 0 && active.glowing, 'Learned wounded target receives the client-visible glow metadata flag')
            return { assertions: ['unlearned wounded target does not glow', 'learned wounded living target receives client glow metadata'], measurements: { unlearned, active } }
        },
    }],
])
