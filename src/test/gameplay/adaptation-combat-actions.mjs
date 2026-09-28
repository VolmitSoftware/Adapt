async function prepareMelee({ context, actor, opponent, equip }, weapon) {
    actor.bot.clearControlStates()
    opponent.bot.clearControlStates()
    actor.bot.deactivateItem()
    opponent.bot.deactivateItem()
    if (weapon) await equip(weapon)
    await context.waitUntil(() => Boolean(actor.bot.entities[opponent.bot.entity.id]), {
        label: 'combat opponent visible to attacker', timeoutMs: 5000,
    })
    await actor.bot.waitForTicks(24)
}

async function strike({ context, actor, opponent, snapshot }) {
    const target = actor.bot.entities[opponent.bot.entity.id]
    context.expect(Boolean(target), 'Combat opponent remains visible')
    await actor.bot.lookAt(target.position.offset(0, 1, 0), true)
    if (actor.bot.entity.position.distanceTo(target.position) >= 2.8) {
        actor.bot.setControlState('forward', true)
        try {
            await context.waitUntil(() => actor.bot.entity.position.distanceTo(target.position) < 2.5, {
                label: 'attacker walks back into reach after knockback', timeoutMs: 2000, intervalMs: 20,
            })
        } finally {
            actor.bot.setControlState('forward', false)
        }
        await actor.bot.waitForTicks(4)
    }
    context.expect(actor.bot.entity.onGround, 'Melee damage is measured from a grounded attack')
    context.expect(actor.bot.entity.position.distanceTo(target.position) < 3, 'Opponent is inside melee reach')
    await actor.bot.lookAt(target.position.offset(0, 1, 0), true)
    const before = await snapshot(opponent)
    actor.bot.attack(target)
    let after
    await context.waitUntil(async () => {
        after = await snapshot(opponent)
        return after.health < before.health
    }, { label: 'natural melee attack changes opponent health', timeoutMs: 2000, intervalMs: 50 })
    context.expect(after.health > 0, 'Damage comparison keeps its opponent alive')
    return { target: opponent.bot.username, before: before.health, after: after.health, damage: before.health - after.health }
}

async function verifyDamageIncrease({ context, unlearned, active }) {
    context.expect(unlearned.target === active.target, 'Both damage measurements use the same opponent')
    context.expect(unlearned.damage > 0, 'Unlearned control attack deals damage')
    context.expect(Math.abs(unlearned.before - active.before) < 0.01, 'Both trials start at equal target health')
    context.expect(active.damage > unlearned.damage + 0.1, 'Learned adaptation increases actual health damage')
    return {
        assertions: ['natural unlearned attack deals baseline damage', 'same grounded attack deals more target-health damage after learning'],
        measurements: { unlearned, active },
    }
}

async function strikeTwice(input) {
    const first = await strike(input)
    await input.actor.bot.waitForTicks(14)
    const second = await strike(input)
    return { target: first.target, first, second }
}

async function verifyCombo({ context, unlearned, active }) {
    context.expect(Math.abs(unlearned.first.damage - unlearned.second.damage) < 0.1,
        'Unlearned punches have equal damage after the invulnerability window')
    context.expect(active.first.damage > unlearned.first.damage + 0.1, 'First learned punch gains combo damage')
    context.expect(active.second.damage > active.first.damage + 0.1, 'Second learned punch gains another combo stack')
    return {
        assertions: ['unlearned consecutive punches retain baseline damage', 'learned first punch gains damage', 'second landed punch increases damage again'],
        measurements: { unlearned, active },
    }
}

async function killCow({ context, actor }) {
    const cow = actor.bot.nearestEntity(entity => entity.name === 'cow')
    context.expect(Boolean(cow), 'Crescent Guard has a natural living kill target')
    context.expect(actor.bot.entity.position.distanceTo(cow.position) < 3, 'Cow is inside sword reach')
    await actor.bot.lookAt(cow.position.offset(0, 0.7, 0), true)
    actor.bot.attack(cow)
    await context.waitUntil(() => !actor.bot.entities[cow.id], {
        label: 'natural sword strike kills the fixture cow', timeoutMs: 3000,
    })
}

async function sunder(input) {
    const before = await input.snapshot(input.opponent)
    const hit = await strike(input)
    const after = await input.snapshot(input.opponent)
    return { ...hit, armorBefore: before.attributes['minecraft:armor'], armorAfter: after.attributes['minecraft:armor'] }
}

async function verifySunder({ context, unlearned, active }) {
    context.expect(unlearned.armorBefore > 0, 'Sunder target wears actual armor')
    context.expect(unlearned.armorAfter === unlearned.armorBefore, 'Unlearned axe hit does not shred armor')
    context.expect(active.armorBefore === unlearned.armorBefore, 'Both trials start with equal armor')
    context.expect(active.armorAfter < active.armorBefore, 'Learned axe hit reduces target armor attribute')
    return {
        assertions: ['unlearned axe hit preserves equipped armor value', 'learned axe hit decreases target armor attribute'],
        measurements: { unlearned, active },
    }
}

export const combatBehaviorCases = new Map([
    ['sword-duelists-focus', {
        stage: 'combat-swords',
        sound: 'minecraft:block.amethyst_block.chime', soundVolume: 0.3, soundPitch: 1.6, particle: 'dust',
        prepare: input => prepareMelee(input, 'wooden_sword'), trigger: strike, verify: verifyDamageIncrease,
    }],
    ['sword-dual-wield', {
        stage: 'swords-dual',
        sound: 'minecraft:block.amethyst_block.chime', soundVolume: 0.25, soundPitch: 1.6, particle: 'wax_on',
        prepare: input => prepareMelee(input, 'wooden_sword'), trigger: strike, verify: verifyDamageIncrease,
    }],
    ['sword-executioners-edge', {
        stage: 'sword-low-health',
        sound: 'minecraft:block.note_block.pling', soundVolume: 0.5, soundPitch: 1.4, particle: 'dust',
        prepare: input => prepareMelee(input, 'wooden_sword'), trigger: strike, verify: verifyDamageIncrease,
    }],
    ['sword-crescent-guard', {
        stage: 'hunter', effect: 'minecraft:absorption',
        sound: 'minecraft:item.totem.use', soundVolume: 0.35, soundPitch: 1.6, particle: 'heart',
        prepare: input => prepareMelee(input, 'wooden_sword'), trigger: killCow,
    }],
    ['unarmed-power', {
        stage: 'combat-unarmed',
        sound: 'minecraft:entity.player.attack.strong', soundVolume: 0.35, soundPitch: 1.2, particle: 'crit',
        prepare: input => prepareMelee(input),
        trigger: async input => {
            const state = await input.snapshot()
            if (state.learned['unarmed-power'] > 0) {
                await input.context.waitUntil(async () => (await input.snapshot()).attributes['minecraft:attack_damage'] > 1.1, {
                    label: 'learned bare-hand damage attribute is active before striking', timeoutMs: 7000, intervalMs: 100,
                })
            }
            return strike(input)
        },
        verify: verifyDamageIncrease,
    }],
    ['unarmed-glass-cannon', {
        stage: 'combat-unarmed',
        sound: 'minecraft:block.glass.break', soundVolume: 0.8, soundPitch: 1.2, particle: 'block',
        prepare: input => prepareMelee(input), trigger: strike, verify: verifyDamageIncrease,
    }],
    ['unarmed-combo-chain', {
        stage: 'combat-unarmed',
        sound: 'minecraft:entity.experience_orb.pickup', soundVolume: 0.55, soundPitch: 0.94, particle: 'crit',
        prepare: input => prepareMelee(input), trigger: strikeTwice, verify: verifyCombo,
    }],
    ['axe-sunder', {
        stage: 'axes-armored',
        sound: 'minecraft:item.shield.break', soundVolume: 0.5, soundPitch: 1.2, particle: 'dust',
        prepare: input => prepareMelee(input, 'wooden_axe'), trigger: sunder, verify: verifySunder,
    }],
])
