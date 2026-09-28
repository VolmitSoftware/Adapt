function report(assertions, unlearned, active) {
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

async function shield(input, targetPosition) {
    await input.actor.bot.lookAt(targetPosition, true)
    await input.actor.bot.waitForTicks(3)
    input.actor.bot.activateItem(true)
    await input.context.waitUntil(async () => (await input.snapshot()).guard.blocking, {
        label: 'shield is naturally blocking', timeoutMs: 2500, intervalMs: 25,
    })
    await input.actor.bot.waitForTicks(6)
}

async function melee(input, attacker, victim) {
    const target = attacker.bot.entities[victim.bot.entity.id]
    input.context.expect(Boolean(target), 'Natural melee victim remains visible')
    await attacker.bot.lookAt(target.position.offset(0, 1, 0), true)
    if (attacker.bot.entity.position.distanceTo(target.position) > 2.6) {
        attacker.bot.setControlState('forward', true)
        try {
            await input.context.waitUntil(() => attacker.bot.entity.position.distanceTo(target.position) < 2.4, {
                label: 'attacker returns to natural reach', timeoutMs: 3000, intervalMs: 20,
            })
        } finally { attacker.bot.setControlState('forward', false) }
    }
    const before = await input.snapshot(victim)
    attacker.bot.attack(target)
    let after
    await input.context.waitUntil(async () => {
        after = await input.snapshot(victim)
        return after.guard.lastHit?.sequence > (before.guard.lastHit?.sequence ?? 0)
    }, { label: 'natural melee damage event arrives', timeoutMs: 2500, intervalMs: 25 })
    await attacker.bot.waitForTicks(3)
    return { before, after: await input.snapshot(victim) }
}

async function repeatedBlocks(input, temper) {
    await shield(input, input.opponent.bot.entity.position.offset(0, 1, 0))
    const before = await input.snapshot()
    const attackerBefore = await input.snapshot(input.opponent)
    let after = before
    let attackerAfter = attackerBefore
    let trials = 0
    try {
        for (; trials < (temper ? 20 : 40); trials++) {
            await melee(input, input.opponent, input.actor)
            after = await input.snapshot()
            attackerAfter = await input.snapshot(input.opponent)
            input.context.expect(after.health > 0 && attackerAfter.health > 0, 'Shield trials keep both players alive')
            if (temper ? after.guard.boots.damage < before.guard.boots.damage : attackerAfter.health < attackerBefore.health) {
                trials++
                break
            }
            await input.actor.bot.waitForTicks(13)
        }
    } finally { input.actor.bot.deactivateItem() }
    return { before, after, attackerBefore, attackerAfter, trials }
}

async function perfect(input) {
    const before = await input.snapshot()
    const attackerBefore = await input.snapshot(input.opponent)
    const target = input.opponent.bot.entities[input.actor.bot.entity.id]
    await input.actor.bot.lookAt(input.opponent.bot.entity.position.offset(0, 1, 0), true)
    await input.opponent.bot.lookAt(target.position.offset(0, 1, 0), true)
    await input.actor.bot.waitForTicks(3)
    input.actor.bot.activateItem(true)
    await input.actor.bot.waitForTicks(1)
    input.opponent.bot.attack(target)
    await input.context.waitUntil(async () => Boolean((await input.snapshot()).guard.firstHit), {
        label: 'strike lands immediately after raising shield', timeoutMs: 2500, intervalMs: 25,
    })
    await input.actor.bot.waitForTicks(3)
    input.actor.bot.deactivateItem()
    return { before, after: await input.snapshot(), attackerBefore, attackerAfter: await input.snapshot(input.opponent) }
}

async function mirror(input) {
    await shield(input, input.opponent.bot.entity.position.offset(0, 1.2, 0))
    let reflected = null
    let trials = 0
    try {
        for (; trials < 30 && !reflected; trials++) {
            await input.opponent.bot.lookAt(input.actor.bot.entity.position.offset(0, 1.4, 0), true)
            input.opponent.bot.activateItem()
            await input.opponent.bot.waitForTicks(22)
            input.opponent.bot.deactivateItem()
            for (let tick = 0; tick < 12 && !reflected; tick++) {
                const state = await input.snapshot()
                for (const projectile of state.guard.projectiles) {
                    if (projectile.shooter !== input.actor.bot.player.uuid || projectile.velocityX <= 0) continue
                    const entity = input.actor.bot.entities[projectile.entityId]
                    if (entity?.velocity.x > 0) reflected = { ...projectile, clientVelocityX: entity.velocity.x }
                }
                await input.actor.bot.waitForTicks(1)
            }
        }
    } finally { input.actor.bot.deactivateItem(); input.opponent.bot.deactivateItem() }
    return { reflected, trials, actor: input.actor.bot.player.uuid }
}

async function interpose(input) {
    input.actor.bot.setControlState('sneak', true)
    await shield(input, input.actor.bot.entity.position.offset(8, 1, 0))
    const before = await input.snapshot()
    const allyBefore = await input.snapshot(input.opponent)
    await input.opponent.bot.lookAt(input.opponent.bot.entity.position.offset(6, 0, 0), true)
    input.opponent.bot.setControlState('forward', true)
    let allyAfter
    try {
        await input.context.waitUntil(async () => {
            allyAfter = await input.snapshot(input.opponent)
            return Boolean(allyAfter.guard.firstHit)
        }, { label: 'ally naturally contacts cactus beside guarding player', timeoutMs: 4000, intervalMs: 25 })
    } finally { input.opponent.bot.setControlState('forward', false) }
    input.opponent.bot.setControlState('back', true)
    await input.opponent.bot.waitForTicks(4)
    input.opponent.bot.setControlState('back', false)
    input.actor.bot.deactivateItem()
    input.actor.bot.setControlState('sneak', false)
    return { before, after: await input.snapshot(), allyBefore, allyAfter }
}

async function wall(input) {
    const before = await input.snapshot(input.opponent)
    const shooter = Object.values(input.actor.bot.entities).find(entity => entity.name === 'skeleton')
    input.context.expect(Boolean(shooter), 'Shield Wall has a natural skeleton archer')
    await shield(input, shooter.position.offset(0, 1.3, 0))
    await input.context.command(`/execute at ${input.actor.bot.username} run data merge entity @e[type=minecraft:skeleton,tag=adapt-qa-guard-shooter,limit=1] {NoAI:0b}`, /Modified entity data/, 5000)
    let after
    try {
        await input.context.waitUntil(async () => {
            after = await input.snapshot(input.opponent)
            return after.health < before.health
        }, { label: 'natural skeleton arrow reaches ally behind shield wall', timeoutMs: 12000, intervalMs: 50 })
    } catch (error) {
        error.message += `; last observed ally ${JSON.stringify({ health: after?.health, location: after?.location, shooter: after?.guard.targets.shooter, projectiles: after?.guard.projectiles.slice(-8) })}`
        throw error
    } finally { input.actor.bot.deactivateItem() }
    return { before, after, damage: before.health - after.health }
}

async function jumpStrike(input, bash) {
    const before = await input.snapshot()
    const target = input.actor.bot.entities[before.guard.targets.primary.entityId]
    await input.actor.bot.lookAt(target.position.offset(0, 0.9, 0), true)
    if (bash) {
        input.actor.bot.setControlState('forward', true)
        input.actor.bot.setControlState('sprint', true)
        await input.actor.bot.waitForTicks(2)
        input.actor.bot.setControlState('sprint', false)
        input.actor.bot.setControlState('forward', false)
    }
    input.actor.bot.setControlState('jump', true)
    try {
        await input.context.waitUntil(() => !input.actor.bot.entity.onGround && input.actor.bot.entity.velocity.y < (bash ? -0.23 : -0.15), {
            label: 'real jump reaches descending critical-hit phase', timeoutMs: 2000, intervalMs: 10,
        })
        input.actor.bot.attack(target)
    } finally { input.actor.bot.setControlState('jump', false) }
    await input.context.waitUntil(async () => Boolean((await input.snapshot()).guard.targets.primary?.firstHit), {
        label: 'falling melee strike reaches primary target', timeoutMs: 2500, intervalMs: 25,
    })
    await input.actor.bot.waitForTicks(4)
    return { before, after: await input.snapshot() }
}

async function multiarmor(input) {
    const before = await input.snapshot()
    const elytra = input.actor.bot.inventory.items().find(value => value.name === 'elytra')
    const chest = input.actor.bot.inventory.items().find(value => value.name === 'iron_chestplate')
    await input.actor.bot.clickWindow(elytra.slot, 0, 0)
    await input.actor.bot.waitForTicks(2)
    await input.actor.bot.clickWindow(chest.slot, 0, 0)
    await input.actor.bot.waitForTicks(2)
    const empty = input.actor.bot.inventory.slots.findIndex((value, slot) => slot >= 9 && slot <= 44 && !value)
    await input.actor.bot.clickWindow(empty, 0, 0)
    await input.actor.bot.waitForTicks(4)
    const merged = input.actor.bot.inventory.items().find(value => value.name === 'elytra')
    input.context.expect(Boolean(merged), 'Elytra remains available after natural merge click')
    await input.actor.bot.equip(merged, 'torso')
    await input.actor.bot.look(-Math.PI / 2, 0, true)
    input.actor.bot.setControlState('forward', true)
    await input.actor.bot.waitForTicks(12)
    input.actor.bot.setControlState('forward', false)
    await input.actor.bot.waitForTicks(4)
    return { before, after: await input.snapshot() }
}

async function heirloom(input) {
    const block = input.actor.bot.blockAt(input.actor.bot.entity.position.clone().set(2, 100, 2))
    await input.actor.bot.activateBlock(block)
    await input.context.waitUntil(() => input.actor.bot.currentWindow?.type === 'minecraft:anvil', { label: 'heirloom anvil opens', timeoutMs: 4000 })
    const window = input.actor.bot.currentWindow
    try {
        const sword = window.slots.slice(window.inventoryStart).find(value => value?.name === 'wooden_sword')
        await input.actor.bot.clickWindow(sword.slot, 0, 0)
        await input.actor.bot.waitForTicks(2)
        await input.actor.bot.clickWindow(0, 0, 0)
        await input.actor.bot.waitForTicks(2)
        input.actor.bot._client.write('name_item', { name: 'Trial Edge' })
        await input.context.waitUntil(() => window.slots[2]?.name === 'wooden_sword', { label: 'natural rename produces sword result', timeoutMs: 4000 })
        await input.actor.bot.clickWindow(2, 0, 1)
        await input.actor.bot.waitForTicks(4)
    } finally { input.actor.bot.closeWindow(window) }
    await input.equip('wooden_sword')
    await input.actor.bot.waitForTicks(4)
    const before = await input.snapshot()
    for (let attempt = 0; attempt < 5; attempt++) {
        const state = await input.snapshot()
        if (state.guard.kills >= 5) break
        const targetState = Object.entries(state.guard.targets).find(([name, value]) => name.startsWith('heirloom') && value.health > 0)?.[1]
        input.context.expect(Boolean(targetState), 'Five natural heirloom kill targets remain accounted for')
        const target = input.actor.bot.entities[targetState.entityId]
        await input.actor.bot.lookAt(target.position.offset(0, 0.7, 0), true)
        input.actor.bot.attack(target)
        await input.context.waitUntil(() => !input.actor.bot.entities[target.id], { label: 'named sword naturally kills fixture cow', timeoutMs: 3000 })
        await input.actor.bot.waitForTicks(24)
    }
    return { before, after: await input.snapshot() }
}

async function machete(input) {
    await input.actor.bot.look(-Math.PI / 2, 0, true)
    await input.actor.bot.waitForTicks(3)
    const position = input.actor.bot.entity.position.clone().set(2, 101, 0)
    const neighbor = position.offset(1, 0, 0)
    const before = input.actor.bot.blockAt(neighbor)?.name
    input.actor.bot._client.write('block_dig', { status: 0, location: position, face: 4, sequence: 0 })
    await input.actor.bot.waitForTicks(2)
    input.actor.bot._client.write('block_dig', { status: 1, location: position, face: 4, sequence: 0 })
    await input.actor.bot.waitForTicks(6)
    return { before, after: input.actor.bot.blockAt(neighbor)?.name }
}

async function whetstone(input) {
    const before = await input.snapshot()
    input.actor.bot.setControlState('sneak', true)
    const block = input.actor.bot.blockAt(input.actor.bot.entity.position.clone().set(2, 100, 0))
    await input.actor.bot.activateBlock(block)
    await input.actor.bot.waitForTicks(8)
    if (input.actor.bot.currentWindow) input.actor.bot.closeWindow(input.actor.bot.currentWindow)
    input.actor.bot.setControlState('sneak', false)
    const honed = await input.snapshot()
    const hit = await melee(input, input.actor, input.opponent)
    return { before, honed, hit, damage: hit.before.health - hit.after.health }
}

async function charge(input) {
    const before = await input.snapshot(input.opponent)
    const target = input.actor.bot.entities[input.opponent.bot.entity.id]
    await input.actor.bot.lookAt(target.position.offset(0, 1, 0), true)
    input.actor.bot.setControlState('forward', true)
    input.actor.bot.setControlState('sprint', true)
    try {
        await input.context.waitUntil(() => input.actor.bot.entity.position.distanceTo(target.position) < 2.7, {
            label: 'natural running charge reaches melee distance', timeoutMs: 4000, intervalMs: 10,
        })
        input.actor.bot.attack(target)
        await input.actor.bot.waitForTicks(1)
    } finally { input.actor.bot.clearControlStates() }
    await input.context.waitUntil(async () => (await input.snapshot(input.opponent)).health < before.health, {
        label: 'natural running punch deals health damage', timeoutMs: 2500, intervalMs: 25,
    })
    const after = await input.snapshot(input.opponent)
    return { before, after, damage: before.health - after.health }
}

async function disarm(input) {
    let after = await input.snapshot()
    let trials = 0
    for (; trials < 80; trials++) {
        const state = after.guard.targets.disarm
        input.context.expect(state?.health > 0, 'Disarm trial target survives natural punches')
        const target = input.actor.bot.entities[state.entityId]
        await input.actor.bot.lookAt(target.position.offset(0, 1, 0), true)
        input.actor.bot.attack(target)
        await input.actor.bot.waitForTicks(12)
        after = await input.snapshot()
        if (after.guard.targets.disarm.hand.type === 'minecraft:air') {
            trials++
            break
        }
    }
    return { after, trials }
}

async function clap(input) {
    const before = await input.snapshot()
    const origin = input.opponent.bot.entity.position.clone()
    input.actor.bot.setControlState('sneak', true)
    await input.actor.bot.look(-Math.PI / 2, Math.PI / 6, true)
    await input.actor.bot.waitForTicks(3)
    input.actor.bot.swingArm('right')
    let farthest = 0
    const observe = () => { farthest = Math.max(farthest, input.opponent.bot.entity.position.distanceTo(origin)) }
    input.opponent.bot.on('physicsTick', observe)
    try { await input.actor.bot.waitForTicks(20) }
    finally { input.opponent.bot.off('physicsTick', observe); input.actor.bot.setControlState('sneak', false) }
    return { before, after: await input.snapshot(), pushedDistance: farthest }
}

export const guardBehaviorCases = new Map([
    ['blocking-perfect-guard', {
        stage: 'guard-block', sound: 'minecraft:item.shield.block', soundVolume: 1, soundPitch: 1.6, particle: 'end_rod',
        prepare: input => prepare(input), trigger: perfect,
        verify: ({ context, unlearned, active }) => {
            context.expect(!unlearned.after.guard.firstHit.cancelled, 'Control immediately raised shield does not cancel damage event')
            context.expect(active.after.guard.firstHit.cancelled && active.after.health === active.before.health, 'Perfect Guard cancels actual timed strike without health loss')
            context.expect(active.attackerAfter.guard.effects.includes('minecraft:weakness') && !unlearned.attackerAfter.guard.effects.includes('minecraft:weakness'), 'Perfect Guard weakens the real attacker')
            return report(['natural timed shield raise negates strike and staggers attacker'], unlearned, active)
        },
    }],
    ['blocking-counter-guard', {
        stage: 'guard-block', sound: 'minecraft:item.trident.return', soundVolume: 0.7, soundPitch: 1.3, particle: 'end_rod',
        prepare: input => prepare(input), trigger: input => repeatedBlocks(input, false),
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.attackerAfter.health === unlearned.attackerBefore.health, 'Control shield blocks do not hurt attacker')
            context.expect(active.attackerAfter.health < active.attackerBefore.health, 'Counter Guard deals real reflected health damage')
            return report(['bounded natural shield hits reflect damage; configured chance0.35, no-proc bound0.65^40'], unlearned, active)
        },
    }],
    ['blocking-tempered-guard', {
        stage: 'guard-temper', sound: 'minecraft:block.anvil.land', soundVolume: 0.25, soundPitch: 1.8, particle: 'wax_on',
        prepare: input => prepare(input), trigger: input => repeatedBlocks(input, true),
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.after.guard.boots.damage === unlearned.before.guard.boots.damage, 'Control blocked hits do not repair armor')
            context.expect(active.after.guard.boots.damage < active.before.guard.boots.damage, 'Natural shield durability damage triggers actual armor repair')
            return report(['bounded natural blocks repair worn equipment; chance0.55, no-proc bound0.45^20'], unlearned, active)
        },
    }],
    ['blocking-mirror-block', {
        stage: 'guard-mirror', sound: 'minecraft:item.shield.block', soundVolume: 1, soundPitch: 1.35, particle: 'end_rod', particleCount: 1, particleOffset: 0,
        prepare: input => prepare(input), trigger: mirror,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.reflected === null, 'Control blocked arrows keep their original shooter')
            context.expect(active.reflected?.shooter === active.actor && active.reflected.clientVelocityX > 0, 'Reflected natural arrow belongs to defender and travels back on client')
            return report(['natural bow projectile changes shooter and reverses client velocity; chance0.45, no-proc bound0.55^30'], unlearned, active)
        },
    }],
    ['blocking-interpose', {
        stage: 'guard-interpose', sound: 'minecraft:item.shield.block', soundVolume: 0.8, soundPitch: 0.9, particle: 'end_rod', particleCount: 1, particleOffset: 0,
        prepare: input => prepare(input), trigger: interpose,
        verify: ({ context, unlearned, active }) => {
            context.expect(active.allyAfter.guard.firstHit.cause === 'CONTACT' && unlearned.allyAfter.guard.firstHit.cause === 'CONTACT', 'Both allies naturally contact same cactus')
            context.expect(active.allyAfter.guard.firstHit.damage < unlearned.allyAfter.guard.firstHit.damage, 'Guard reduces actual low-health ally contact damage')
            context.expect(active.after.guard.offhand.damage > active.before.guard.offhand.damage, 'Redirected damage wears guarding shield')
            return report(['natural ally damage is reduced and charged to nearby guarding shield durability'], unlearned, active)
        },
    }],
    ['blocking-shield-wall', {
        stage: 'guard-wall', sound: 'minecraft:block.amethyst_block.hit', soundVolume: 0.5, soundPitch: 1.5, particle: 'crit',
        prepare: input => prepare(input), trigger: wall,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.after.guard.firstHit.cause === 'PROJECTILE' && active.after.guard.firstHit.cause === 'PROJECTILE', 'Both trials use real skeleton arrows')
            context.expect(Math.abs(unlearned.damage / unlearned.after.guard.firstHit.originalDamage - 1) < 0.001, 'Control natural arrow deals its full original damage')
            context.expect(active.damage > 0 && Math.abs(active.damage / active.after.guard.firstHit.originalDamage - 0.4) < 0.001, 'Shield Wall reduces actual incoming arrow damage by configured 60 percent')
            return report(['natural projectile damages ally less while inside a learned shield wall'], unlearned, active)
        },
    }],
    ['blocking-bulwark-bash', {
        stage: 'guard-bash', sound: 'minecraft:item.shield.block', soundVolume: 1, soundPitch: 0.85, particle: 'cloud',
        prepare: input => prepare(input, 'wooden_sword'), trigger: input => jumpStrike(input, true),
        verify: ({ context, unlearned, active }) => {
            for (const trial of [unlearned, active]) {
                const hit = trial.after.guard.targets.primary.firstHit
                context.expect(hit.attackerFallDistance > 0.08 && hit.sprintAgeMs < 900, 'Actual hit meets the configured fall distance and recent-sprint window')
            }
            context.expect(!unlearned.after.guard.targets.secondary.effects.includes('minecraft:slowness'), 'Control jumping strike does not stun adjacent target')
            context.expect(active.after.guard.targets.primary.effects.includes('minecraft:slowness') && active.after.guard.targets.secondary.effects.includes('minecraft:slowness'), 'Learned bash stuns both natural targets')
            context.expect(active.after.guard.shieldCooldown > 0, 'Bash applies actual shield cooldown')
            return report(['natural recent-sprint falling strike stuns primary and nearby targets and consumes shield cooldown'], unlearned, active)
        },
    }],
    ['blocking-multiarmor', {
        stage: 'guard-multiarmor', sound: 'minecraft:item.armor.equip_elytra', soundVolume: 1, soundPitch: 0.77, particle: 'end_rod',
        prepare: input => prepare(input), trigger: multiarmor,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.after.guard.chest.type === 'minecraft:elytra', 'Control inventory swap and grounded walking preserve ordinary Elytra')
            context.expect(active.after.guard.chest.type === 'minecraft:iron_chestplate', 'Naturally merged Elytra changes into armor while walking on ground')
            return report(['natural armor-Elytra inventory merge produces equipped gear that changes with movement'], unlearned, active)
        },
    }],
    ['sword-crimson-cyclone', {
        stage: 'guard-cyclone', sound: 'minecraft:entity.player.attack.sweep', soundVolume: 1, soundPitch: 0.6, particle: 'crimson_spore',
        prepare: input => prepare(input, 'wooden_sword'), trigger: input => jumpStrike(input, false),
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.after.guard.targets.primary.firstHit.critical && active.after.guard.targets.primary.firstHit.critical, 'Both trials are natural falling critical hits')
            context.expect(unlearned.after.guard.targets.secondary.health === 20, 'Control critical hit does not damage nearby cow')
            context.expect(active.after.guard.targets.secondary.health < 20 && active.after.food < active.before.food, 'Cyclone damages adjacent target and consumes hunger')
            return report(['natural sword critical hit damages nearby target and pays cyclone hunger cost'], unlearned, active)
        },
    }],
    ['sword-heirloom-edge', {
        stage: 'guard-heirloom', sound: 'minecraft:block.anvil.use', soundVolume: 0.3, soundPitch: 1.7, particle: 'totem_of_undying',
        prepare: input => prepare(input, 'wooden_sword'), trigger: heirloom,
        verify: ({ context, unlearned, active }) => {
            const key = 'minecraft:attack_damage'
            context.expect(unlearned.after.guard.kills === 5 && active.after.guard.kills === 5, 'Both named swords make five real kills')
            context.expect(unlearned.after.attributes[key] === unlearned.before.attributes[key], 'Control named sword gains no attack damage')
            context.expect(active.after.attributes[key] > active.before.attributes[key], 'Naturally renamed Heirloom sword banks permanent attack damage')
            return report(['natural anvil rename and five kills grow actual equipped sword damage'], unlearned, active)
        },
    }],
    ['sword-machete', {
        stage: 'guard-machete', sound: 'minecraft:item.axe.strip', soundVolume: 0.35, soundPitch: 1.4, particle: 'wax_on',
        prepare: input => prepare(input, 'wooden_sword'), trigger: machete,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.after === 'oak_leaves', 'Control sword click leaves neighboring foliage intact')
            context.expect(active.after === 'air', 'Learned sword click removes neighboring foliage inside guaranteed proc distance')
            return report(['natural sword click clears adjacent foliage inside maximum-level guaranteed radius'], unlearned, active)
        },
    }],
    ['sword-whetstone-ritual', {
        stage: 'guard-whetstone', sound: 'minecraft:block.grindstone.use', soundVolume: 0.9, soundPitch: 1.2, particle: 'dust',
        prepare: input => prepare(input, 'wooden_sword'), trigger: whetstone,
        verify: ({ context, unlearned, active }) => {
            context.expect(active.before.guard.level - active.honed.guard.level === 2, 'Ritual spends two actual vanilla levels')
            context.expect(active.damage > unlearned.damage, 'Honed sword deals more actual target health damage')
            const sword = active.honed.guard.inventory.find(value => value.type === 'minecraft:wooden_sword')
            context.expect(sword.damage === 15, 'Ritual charges configured sword durability')
            return report(['natural sneak grindstone use pays XP and durability and increases subsequent hit damage'], unlearned, active)
        },
    }],
    ['unarmed-battering-charge', {
        stage: 'guard-charge', sound: 'minecraft:entity.player.attack.knockback', soundVolume: 1, soundPitch: 0.95, particle: 'explosion',
        prepare: input => prepare(input), trigger: charge,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.damage > 0 && active.damage > unlearned.damage + 0.5, 'Actual running learned punch deals bonus health damage')
            return report(['natural sprint movement followed by bare-hand strike adds charge damage'], unlearned, active)
        },
    }],
    ['unarmed-disarm', {
        stage: 'guard-disarm', sound: 'minecraft:item.lead.break', soundVolume: 0.9, soundPitch: 0.8, particle: 'crit',
        prepare: input => prepare(input), trigger: disarm,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.after.guard.targets.disarm.hand.type === 'minecraft:iron_sword', 'Control punches leave mob weapon equipped')
            context.expect(active.after.guard.targets.disarm.hand.type === 'minecraft:air', 'Learned natural punch disarms mob')
            context.expect([...active.after.guard.inventory, ...active.after.guard.drops].some(value => value.type === 'minecraft:iron_sword'), 'Disarmed weapon becomes actual dropped or collected item')
            return report(['bounded natural punches remove and drop mob equipment; chance0.22, no-proc bound0.78^80'], unlearned, active)
        },
    }],
    ['unarmed-shockwave-clap', {
        stage: 'guard-clap', sound: 'minecraft:block.bell.resonate', soundVolume: 0.7, soundPitch: 1.4, particle: 'explosion',
        prepare: input => prepare(input), trigger: clap,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.pushedDistance < 0.2, 'Control sneak swing does not move nearby player')
            context.expect(active.pushedDistance > 2 && active.before.food - active.after.food === 2, 'Learned clap pushes nearby player and consumes hunger')
            return report(['natural bare-hand sneak air swing launches a cone target and pays hunger'], unlearned, active)
        },
    }],
])
