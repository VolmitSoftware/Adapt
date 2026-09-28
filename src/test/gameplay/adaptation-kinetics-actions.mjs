function report(assertions, unlearned, active) {
    return { assertions, measurements: { unlearned, active } }
}

function primary(state) {
    return state.kinetics.targets.primary
}

function distance(first, second) {
    return Math.hypot(first.x - second.x, first.z - second.z)
}

function stab(bot) {
    bot._client.write('block_dig', { status: 7, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 })
}

async function prepare(input, tool) {
    input.actor.bot.clearControlStates()
    input.opponent.bot.clearControlStates()
    input.actor.bot.deactivateItem()
    await input.equip(tool)
    await input.actor.bot.look(-Math.PI / 2, 0, true)
    await input.actor.bot.waitForTicks(24)
}

async function target(input) {
    const state = await input.snapshot()
    const entity = input.actor.bot.entities[primary(state).entityId]
    input.context.expect(Boolean(entity), 'Kinetics target is a real client-visible entity')
    return { state, entity }
}

async function smash(input) {
    const { state: before, entity } = await target(input)
    await input.actor.bot.look(-Math.PI / 2, 0, true)
    await input.actor.bot.waitForTicks(3)
    input.actor.bot.setControlState('forward', true)
    try {
        await input.context.waitUntil(() => input.actor.bot.entity.position.x > 1.5, {
            label: 'player naturally walks off elevated smash platform', timeoutMs: 3000, intervalMs: 10,
        })
    } finally { input.actor.bot.setControlState('forward', false) }
    await input.context.waitUntil(() => input.actor.bot.entity.position.y < 102.7 && input.actor.bot.entity.velocity.y < -0.2, {
        label: 'mace attack occurs after falling more than three blocks', timeoutMs: 2500, intervalMs: 10,
    })
    input.actor.bot.attack(entity)
    await input.actor.bot.waitForTicks(4)
    const after = await input.snapshot()
    input.context.expect(after.kinetics.smash.landed === true && after.kinetics.smash.fallDistance >= 2, 'Natural falling mace strike actually lands a smash')
    input.context.expect(primary(after).health < primary(before).health, 'Mace strike damages the real target')
    await input.actor.bot.waitForTicks(20)
    return { before, after, settled: await input.snapshot() }
}

async function spearHit(input) {
    const { state: before, entity } = await target(input)
    await input.actor.bot.lookAt(entity.position.offset(0, 1, 0), true)
    await input.actor.bot.waitForTicks(3)
    stab(input.actor.bot)
    await input.actor.bot.waitForTicks(5)
    const after = await input.snapshot()
    input.context.expect(primary(after).health < primary(before).health, 'Natural spear strike damages its target')
    return { before, after, damage: primary(before).health - primary(after).health }
}

async function charge(input) {
    const { state: before, entity } = await target(input)
    await input.actor.bot.lookAt(entity.position.offset(0, 0.8, 0), true)
    await input.actor.bot.waitForTicks(3)
    input.actor.bot.setControlState('forward', true)
    input.actor.bot.setControlState('sprint', true)
    try {
        await input.context.waitUntil(() => input.actor.bot.entity.position.distanceTo(entity.position) < 2.7, {
            label: 'spear attacker runs into melee reach', timeoutMs: 3500, intervalMs: 10,
        })
        stab(input.actor.bot)
        await input.actor.bot.waitForTicks(1)
    } finally { input.actor.bot.clearControlStates() }
    await input.actor.bot.waitForTicks(4)
    const after = await input.snapshot()
    input.context.expect(primary(after).health < primary(before).health, 'Running spear attack actually hits')
    return { before, after, damage: primary(before).health - primary(after).health }
}

async function deadZone(input) {
    const before = await input.snapshot()
    const attackerBefore = await input.snapshot(input.opponent)
    const defender = input.opponent.bot.entities[input.actor.bot.entity.id]
    input.context.expect(Boolean(defender), 'Close-range attacker can see spear defender')
    await input.opponent.bot.lookAt(defender.position.offset(0, 1, 0), true)
    await input.opponent.bot.waitForTicks(3)
    input.opponent.bot.attack(defender)
    await input.actor.bot.waitForTicks(4)
    const shoved = await input.snapshot(input.opponent)
    await input.actor.bot.waitForTicks(5)
    const attacker = input.actor.bot.entities[input.opponent.bot.entity.id]
    input.context.expect(Boolean(attacker), 'Riposte target remains visible')
    await input.actor.bot.lookAt(attacker.position.offset(0, 1, 0), true)
    await input.actor.bot.waitForTicks(3)
    stab(input.actor.bot)
    await input.actor.bot.waitForTicks(4)
    const countered = await input.snapshot(input.opponent)
    await input.actor.bot.waitForTicks(14)
    return { before, attackerBefore, shoved, countered, after: await input.snapshot(),
        displacement: distance(attackerBefore.kinetics, shoved.kinetics),
        damage: shoved.health - countered.health }
}

async function lunge(input) {
    const { state: before, entity } = await target(input)
    const origin = input.actor.bot.entity.position.clone()
    let maximumSpeed = 0
    const measure = () => { maximumSpeed = Math.max(maximumSpeed, Math.hypot(input.actor.bot.entity.velocity.x, input.actor.bot.entity.velocity.z)) }
    input.actor.bot.on('physicsTick', measure)
    try {
        await input.actor.bot.lookAt(entity.position.offset(0, 1, 0), true)
        await input.actor.bot.waitForTicks(3)
        stab(input.actor.bot)
        await input.actor.bot.waitForTicks(20)
    } finally { input.actor.bot.off('physicsTick', measure) }
    return { before, after: await input.snapshot(), maximumSpeed, distance: input.actor.bot.entity.position.distanceTo(origin) }
}

async function mounted(input) {
    const before = await input.snapshot()
    const cart = input.actor.bot.entities[before.kinetics.mountId]
    input.context.expect(Boolean(cart), 'Real minecart is visible for natural mounting')
    const entity = input.actor.bot.entities[primary(before).entityId]
    input.context.expect(Boolean(entity), 'Mounted spear target is visible')
    await input.actor.bot.lookAt(entity.position.offset(0, 1, 0), true)
    await input.actor.bot.waitForTicks(3)
    await input.actor.bot.mount(cart)
    try {
        await input.context.waitUntil(async () => (await input.snapshot()).kinetics.mounted, {
            label: 'player mounts minecart through entity interaction', timeoutMs: 3000,
        })
        await input.context.command(`/execute at ${input.actor.bot.username} run fill -4 99 0 9 99 0 minecraft:redstone_block`, /filled|blocks|changed|modified/i, 5000)
        let inRange
        await input.context.waitUntil(async () => {
            inRange = await input.snapshot()
            return inRange.kinetics.mounted && inRange.kinetics.vehicleSpeed > 0.1
                && distance(inRange.kinetics, primary(inRange)) < 2.8
        }, {
            label: 'powered rail naturally carries mounted attacker into spear range', timeoutMs: 6000, intervalMs: 10,
        })
        const dx = primary(inRange).x - inRange.kinetics.x
        const dz = primary(inRange).z - inRange.kinetics.z
        const dy = primary(inRange).y + 0.8 - inRange.kinetics.y - inRange.kinetics.eyeHeight
        input.actor.bot._client.write('look', {
            yaw: -Math.atan2(dx, dz) * 180 / Math.PI,
            pitch: -Math.atan2(dy, Math.hypot(dx, dz)) * 180 / Math.PI,
            flags: { onGround: false, hasHorizontalCollision: false },
        })
        stab(input.actor.bot)
        let after
        await input.context.waitUntil(async () => {
            after = await input.snapshot()
            return primary(after).health < primary(before).health
        }, { label: 'moving mounted spear strike deals actual damage', timeoutMs: 3000, intervalMs: 50 })
        return { before, after, damage: primary(before).health - primary(after).health }
    } finally {
        input.actor.bot.setControlState('sneak', true)
        try {
            await input.context.waitUntil(async () => !(await input.snapshot()).kinetics.mounted, {
                label: 'sneak input dismounts the real minecart', timeoutMs: 3000,
            })
        } finally { input.actor.bot.setControlState('sneak', false) }
    }
}

export const kineticsBehaviorCases = new Map([
    ['kinetics-breachwright', {
        stage: 'kinetic-smash', prepare: input => prepare(input, 'mace'), trigger: smash,
        verify: ({ context, unlearned, active }) => {
            context.expect(primary(unlearned.after).attributes.armor === 20 && primary(unlearned.after).attributes.armor_toughness === 8, 'Control smash preserves target armor attributes')
            context.expect(primary(active.after).attributes.armor === 14 && primary(active.after).attributes.armor_toughness === 4, 'Learned smash removes six armor and four toughness from real target')
            return report(['natural mace smash shreds actual target armor and toughness by configured amounts'], unlearned, active)
        },
    }],
    ['kinetics-quake-guard', {
        stage: 'kinetic-smash', prepare: input => prepare(input, 'mace'), trigger: smash,
        verify: ({ context, unlearned, active }) => {
            for (const name of ['knockback_resistance', 'armor_toughness', 'safe_fall_distance']) {
                context.expect(unlearned.after.kinetics.attributes[name] === unlearned.before.kinetics.attributes[name], `Control smash preserves ${name}`)
                context.expect(active.after.kinetics.attributes[name] > active.before.kinetics.attributes[name], `Learned smash grants ${name}`)
            }
            return report(['natural landed mace smash grants all three Quake Guard brace attributes'], unlearned, active)
        },
    }],
    ['kinetics-rebound-anvil', {
        stage: 'kinetic-smash', prepare: input => prepare(input, 'mace'), trigger: smash,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.after.kinetics.attributes.fall_damage_multiplier === unlearned.before.kinetics.attributes.fall_damage_multiplier, 'Control smash grants no fall relief modifier')
            if ('bounciness' in active.before.kinetics.attributes) {
                context.expect(unlearned.after.kinetics.attributes.bounciness === unlearned.before.kinetics.attributes.bounciness, 'Control smash grants no bounce modifier')
                context.expect(active.after.kinetics.attributes.bounciness > active.before.kinetics.attributes.bounciness, 'Learned smash adds real bounciness where the server provides the attribute')
            }
            context.expect(active.after.kinetics.attributes.fall_damage_multiplier < active.before.kinetics.attributes.fall_damage_multiplier, 'Learned smash applies fall damage relief')
            return report(['natural landed mace smash reduces the real fall damage multiplier; bounce checked when registered'], unlearned, active)
        },
    }],
    ['kinetics-windburst', {
        stage: 'kinetic-smash', sound: 'minecraft:entity.wind_charge.wind_burst', soundVolume: 0.9, soundPitch: 0.8, particle: 'gust',
        prepare: input => prepare(input, 'mace'), trigger: smash,
        verify: ({ context, unlearned, active }) => {
            const controlDistance = distance(unlearned.before.kinetics.targets.secondary, unlearned.settled.kinetics.targets.secondary)
            const activeDistance = distance(active.before.kinetics.targets.secondary, active.settled.kinetics.targets.secondary)
            context.expect(controlDistance < 0.05, 'Secondary target beyond vanilla smash radius remains stationary without Windburst')
            context.expect(activeDistance > 0.3, 'Learned radial shockwave physically pushes the secondary target beyond vanilla smash radius')
            context.expect(active.after.kinetics.attributes.explosion_knockback_resistance > unlearned.after.kinetics.attributes.explosion_knockback_resistance, 'Windburst grants its actual temporary blast brace')
            return report(['falling mace smash pushes a secondary target and grants the blast brace'], unlearned, active)
        },
    }],
    ['kinetics-charge-lance', {
        stage: 'kinetic-charge', prepare: input => prepare(input, 'wooden_spear'), trigger: charge,
        verify: ({ context, unlearned, active }) => {
            context.expect(primary(active.after).lastHit.attackerMove.distance >= 0.18 && primary(active.after).lastHit.tick - primary(active.after).lastHit.attackerMove.tick <= 1, 'Learned attack actually meets configured natural speed threshold in its latest accepted movement')
            context.expect(active.damage > unlearned.damage * 1.2, 'Running learned spear strike deals more health damage than identical control')
            return report(['natural running spear attack gains measured speed-dependent damage'], unlearned, active)
        },
    }],
    ['kinetics-dead-zone', {
        stage: 'kinetic-dead-zone', sound: 'minecraft:item.shield.block', soundVolume: 0.4, soundPitch: 1.5, particle: 'dust', particleColor: 0xC9D9E8,
        prepare: input => prepare(input, 'wooden_spear'), trigger: deadZone,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.displacement < 0.1 && active.displacement > 0.5, 'Learned close-range hit physically shoves the attacker')
            context.expect(active.damage > unlearned.damage * 1.3, 'Actual immediate spear riposte deals increased health damage')
            return report(['natural close-range hit shoves attacker and arms a stronger immediate spear counterattack'], unlearned, active)
        },
    }],
    ['kinetics-impale-pin', {
        stage: 'kinetic-pin', sound: 'minecraft:block.chain.place', soundVolume: 0.45, soundPitch: 0.65, particle: 'dust', particleColor: 0xB7D4E8,
        prepare: input => prepare(input, 'wooden_spear'), trigger: spearHit,
        verify: ({ context, unlearned, active }) => {
            context.expect(!primary(unlearned.after).effects.some(effect => effect.type === 'slowness'), 'Control spear hit at sweet range grants no slow')
            context.expect(primary(active.after).effects.some(effect => effect.type === 'slowness' && effect.amplifier === 2), 'Natural learned sweet-range hit pins actual target with Slowness III')
            return report(['natural spear strike at four blocks applies configured sweet-range slowness'], unlearned, active)
        },
    }],
    ['kinetics-lunge-conductor', {
        stage: 'kinetic-lunge', sound: 'minecraft:item.trident.riptide_1', soundVolume: 0.35, soundPitch: 1.6, particle: 'cloud',
        prepare: input => prepare(input, 'wooden_spear'), trigger: lunge,
        verify: ({ context, unlearned, active }) => {
            context.expect(Number.isFinite(unlearned.after.kinetics.lunge.power), 'Control Lunge enchantment produces a real vanilla lunge event')
            context.expect(active.after.kinetics.lunge.power === unlearned.after.kinetics.lunge.power + 3, 'Learned natural lunge increases actual event power by three')
            context.expect(active.maximumSpeed > unlearned.maximumSpeed && active.distance > unlearned.distance, 'Boosted lunge produces greater client-visible speed and travel')
            return report(['natural enchanted spear jab increases lunge power and actual player motion'], unlearned, active)
        },
    }],
    ['kinetics-mounted-shock', {
        stage: 'kinetic-mounted', sound: 'minecraft:entity.horse.gallop', soundVolume: 0.4, soundPitch: 1.2, particle: 'sweep_attack',
        prepare: input => prepare(input, 'wooden_spear'), trigger: mounted,
        verify: ({ context, unlearned, active }) => {
            context.expect(primary(active.after).lastHit.mountSpeed > 0.1, 'Learned mounted hit uses a naturally moving vehicle')
            context.expect(active.damage > unlearned.damage * 1.2, 'Moving learned mounted spear hit deals more actual damage')
            return report(['natural minecart-mounted spear hit gains speed-dependent damage'], unlearned, active)
        },
    }],
])
