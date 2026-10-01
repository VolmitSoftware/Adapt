async function equipSwords({ actor, opponent, equip }) {
    await equip('iron_sword')
    await equip('iron_sword', opponent)
    await actor.bot.waitForTicks(25)
}

async function hit(input, attacker, victim) {
    const { context, actor } = input
    try {
        await context.waitUntil(async () => {
            const target = attacker.bot.entities[victim.bot.entity.id]
            context.expect(Boolean(target), 'Melee target is visible')
            if (attacker.bot.entity.position.distanceTo(target.position) < 2.7) return true
            await attacker.bot.lookAt(target.position.offset(0, 1, 0), true)
            attacker.bot.setControlState('forward', true)
            return false
        }, { label: 'attacker walks back into melee reach after knockback', timeoutMs: 4000, intervalMs: 50 })
    } finally { attacker.bot.setControlState('forward', false) }
    const target = attacker.bot.entities[victim.bot.entity.id]
    context.expect(Boolean(target) && attacker.bot.entity.position.distanceTo(target.position) < 3, 'Target is in actual melee reach')
    await attacker.bot.lookAt(target.position.offset(0, 1, 0), true)
    attacker.bot.attack(target)
    await actor.bot.waitForTicks(3)
}

async function receiveDamage(input) {
    const before = await input.snapshot()
    await hit(input, input.opponent, input.actor)
    const after = await input.snapshot()
    input.context.expect(after.health < before.health, 'Natural melee attack damages the player')
    return before.health - after.health
}

export const chronosBehaviorCases = new Map([
    ['chronos-aberrant-touch', {
        stage: 'chronos-combat', negativeWindowTicks: 1, prepare: equipSwords,
        sound: 'minecraft:block.lever.click', soundVolume: 0.34, soundPitch: 1.7, particle: 'sculk_soul',
        async trigger(input) {
            const before = await input.snapshot()
            await hit(input, input.actor, input.opponent)
            const after = await input.snapshot()
            const target = await input.snapshot(input.opponent)
            return { foodCost: before.food - after.food, slowness: target.effects.find(effect => effect.type === 'minecraft:slowness') ?? null }
        },
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.slowness === null && unlearned.foodCost === 0, 'Unlearned hit applies no slowness or food cost')
            context.expect(active.slowness?.amplifier === 0 && active.slowness.duration > 20 && active.foodCost === 1, 'Learned melee hit slows its actual target and consumes one food point')
            return { assertions: ['unlearned melee hit has no slowness or cost', 'learned hit applies slowness to the victim and charges one food point'], measurements: { unlearned, active } }
        },
    }],
    ['chronos-deja-vu', {
        stage: 'chronos-combat', negativeWindowTicks: 1, prepare: equipSwords,
        sound: 'minecraft:block.beacon.ambient', soundVolume: 0.3, soundPitch: 1.4, particle: 'reverse_portal',
        async trigger(input) {
            const first = await receiveDamage(input)
            await input.actor.bot.waitForTicks(24)
            const second = await receiveDamage(input)
            return { first, second }
        },
        async verify({ context, unlearned, active }) {
            context.expect(Math.abs(unlearned.first - unlearned.second) < 0.01 && Math.abs(active.first - unlearned.first) < 0.01, 'Initial and unlearned hits deal equal damage')
            context.expect(active.second > 0 && active.second < active.first * 0.8, 'Learned repeated damage is mitigated')
            return { assertions: ['unlearned repeated melee damage is unchanged', 'learned first hit remains normal', 'learned repeated cause deals less damage'], measurements: { unlearned, active } }
        },
    }],
    ['chronos-borrowed-time', {
        stage: 'chronos-combat', negativeWindowTicks: 1, prepare: equipSwords,
        sound: 'minecraft:block.sculk_catalyst.bloom', soundVolume: 0.4, soundPitch: 0.8, particle: 'soul', particleCount: 4,
        async trigger(input) {
            const before = await input.snapshot()
            const immediate = await receiveDamage(input)
            const hit = await input.snapshot()
            const deferred = (hit.stats['chronos.borrowed-time.damage-deferred'] ?? 0)
                - (before.stats['chronos.borrowed-time.damage-deferred'] ?? 0)
            await input.actor.bot.waitForTicks(24)
            const settledPosition = input.actor.bot.entity.position.clone()
            let paybackDisplacement = 0
            if (deferred > 0) {
                await input.context.waitUntil(async () => {
                    const state = await input.snapshot()
                    paybackDisplacement = Math.max(paybackDisplacement, input.actor.bot.entity.position.distanceTo(settledPosition))
                    return before.health - state.health >= immediate + deferred - 0.02
                }, { label: 'natural payback pulses settle the recorded damage debt', timeoutMs: 20000, intervalMs: 250 })
            } else await input.actor.bot.waitForTicks(240)
            const after = await input.snapshot()
            return { immediate, deferred, total: before.health - after.health, paybackDisplacement }
        },
        async verify({ context, unlearned, active }) {
            context.expect(Math.abs(unlearned.immediate - unlearned.total) < 0.01, 'Unlearned damage has no later payback')
            context.expect(active.immediate < unlearned.immediate && active.total > active.immediate, 'Learned damage is deferred and later repaid')
            context.expect(Math.abs(active.total - unlearned.total) < 0.02, 'Deferral preserves the complete damage amount')
            context.expect(active.paybackDisplacement < 0.08, 'Payback pulses leave the player stationary after melee knockback settles')
            return { assertions: ['unlearned damage is immediate', 'learned damage is initially reduced', 'natural payback pulses settle the full deferred damage without additional knockback'], measurements: { unlearned, active } }
        },
    }],
    ['chronos-overtime', {
        stage: 'chronos', negativeWindowTicks: 1,
        sound: 'minecraft:block.amethyst_block.chime', soundVolume: 0.35, soundPitch: 1.6, particle: 'wax_on',
        async trigger({ actor, equip, snapshot, context }) {
            await equip('potion')
            await actor.bot.consume()
            await actor.bot.waitForTicks(4)
            const effect = (await snapshot()).effects.find(effect => effect.type === 'minecraft:speed')
            context.expect(Boolean(effect), 'Drinking a real potion supplies speed')
            return { duration: effect.duration, amplifier: effect.amplifier }
        },
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.duration > 3500 && unlearned.duration <= 3600, 'Ordinary swiftness retains its vanilla three-minute duration')
            context.expect(active.amplifier === unlearned.amplifier && active.duration > unlearned.duration * 1.3, 'Learned potion lasts longer without changing its amplifier')
            return { assertions: ['unlearned potion has its normal duration', 'learned potion receives the configured duration extension with unchanged amplifier'], measurements: { unlearned, active } }
        },
    }],
    ['chronos-rewind', {
        stage: 'agility', negativeWindowTicks: 1,
        sound: 'minecraft:block.amethyst_cluster.place', soundVolume: 0.5, soundPitch: 1.6, particle: 'reverse_portal',
        async trigger({ actor, context }) {
            const start = actor.bot.entity.position.clone()
            const swap = () => actor.bot._client.write('block_dig', { status: 6, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 })
            actor.bot.setControlState('sneak', true)
            await actor.bot.waitForTicks(3)
            swap()
            await actor.bot.waitForTicks(4)
            await actor.bot.look(0, 0, true)
            actor.bot.setControlState('forward', true)
            await actor.bot.waitForTicks(35)
            actor.bot.setControlState('forward', false)
            await actor.bot.waitForTicks(4)
            const distance = actor.bot.entity.position.distanceTo(start)
            context.expect(distance > 1, 'Player walks away from the actual mark')
            swap()
            await actor.bot.waitForTicks(15)
            return { distance, returnedDistance: actor.bot.entity.position.distanceTo(start) }
        },
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.returnedDistance > 1, 'Unlearned hand swap leaves the player away from the mark')
            context.expect(active.returnedDistance < 0.4, 'Learned hand swap returns the player to the original mark')
            return { assertions: ['both trials walk away from the initial position', 'unlearned hand swap does not teleport', 'learned second hand swap rewinds to the original position'], measurements: { unlearned, active } }
        },
    }],
])

chronosBehaviorCases.set('chronos-pocket-watch', {
    stage: 'chronos-fall', negativeWindowTicks: 1, effect: 'minecraft:slow_falling',
    sound: 'minecraft:block.amethyst_block.chime', soundVolume: 0.3, soundPitch: 1.2, particle: 'cloud',
    async trigger({ actor, context, snapshot }) {
        try {
            await actor.bot.look(0, 0, true)
            actor.bot.setControlState('forward', true)
            await actor.bot.waitForTicks(9)
            actor.bot.setControlState('forward', false)
            await context.waitUntil(() => actor.bot.entity.velocity.y < -0.7, {
                label: 'natural fall accumulates downward momentum before sneaking', timeoutMs: 3000, intervalMs: 20,
            })
            const beforeVelocity = actor.bot.entity.velocity.y
            actor.bot.setControlState('sneak', true)
            await actor.bot.waitForTicks(8)
            context.expect(!actor.bot.entity.onGround && actor.bot.entity.position.y > 101, 'Sneaking player remains airborne above the catch pool')
            return { beforeVelocity, afterVelocity: actor.bot.entity.velocity.y, after: await snapshot() }
        } finally {
            actor.bot.clearControlStates()
        }
    },
    async verify({ context, unlearned, active }) {
        context.expect(unlearned.beforeVelocity < -0.7 && active.beforeVelocity < -0.7, 'Both trials build downward momentum before the first possible pulse')
        context.expect(!unlearned.after.effects.some(effect => effect.type === 'minecraft:slow_falling') && active.after.effects.some(effect => effect.type === 'minecraft:slow_falling'), 'Only the learned airborne sneak applies Slow Falling')
        context.expect(unlearned.afterVelocity < -0.7, 'Unlearned sneaking leaves the accumulated descent intact')
        context.expect(active.afterVelocity >= -0.55 && active.afterVelocity < 0, 'The learned pulse brakes existing downward speed to Slow Falling descent')
        return { assertions: ['both trials accumulate descent before sneaking', 'only learned sneaking grants Slow Falling', 'learned pulse brakes existing momentum while the control continues falling quickly'], measurements: { unlearned, active } }
    },
})
chronosBehaviorCases.set('chronos-temporal-echo', {
    stage: 'ranged', negativeWindowTicks: 1,
    sound: 'minecraft:block.amethyst_block.chime', soundVolume: 0.75, soundPitch: 1.35, particle: 'reverse_portal',
    async trigger({ actor, equip }) {
        const arrows = new Set()
        const onSpawn = entity => { if (entity.name === 'arrow') arrows.add(entity.id) }
        const count = () => actor.bot.inventory.items().filter(item => item.name === 'arrow').reduce((sum, item) => sum + item.count, 0)
        actor.bot.on('entitySpawn', onSpawn)
        try {
            await equip('bow')
            await actor.bot.look(0, 0.3, true)
            await actor.bot.waitForTicks(3)
            const before = count()
            actor.bot.activateItem()
            await actor.bot.waitForTicks(25)
            actor.bot.deactivateItem()
            await actor.bot.waitForTicks(25)
            return { arrows: [...arrows], consumed: before - count() }
        } finally {
            actor.bot.off('entitySpawn', onSpawn)
        }
    },
    async verify({ context, unlearned, active }) {
        context.expect(unlearned.arrows.length === 1 && active.arrows.length === 2, 'Only the learned shot creates a second real arrow')
        context.expect(unlearned.consumed === 1 && active.consumed === 1, 'The echo consumes no extra ammunition')
        return { assertions: ['unlearned shot creates one arrow', 'learned shot creates an additional delayed arrow', 'both shots consume exactly one arrow item'], measurements: { unlearned, active } }
    },
})
chronosBehaviorCases.set('chronos-hourglass-guard', {
    stage: 'chronos-lethal', negativeWindowTicks: 1, prepare: equipSwords,
    sound: 'minecraft:block.bell.use', soundVolume: 0.7, soundPitch: 0.5, particle: 'flash',
    async trigger(input) {
        let deaths = 0
        const onDeath = () => { deaths++ }
        input.actor.bot.on('death', onDeath)
        try {
            await hit(input, input.opponent, input.actor)
            await input.actor.bot.waitForTicks(40)
            return { deaths, health: (await input.snapshot()).health }
        } finally {
            input.actor.bot.off('death', onDeath)
        }
    },
    async verify({ context, unlearned, active }) {
        context.expect(unlearned.deaths === 1, 'Unlearned lethal strike kills the player')
        context.expect(active.deaths === 0 && active.health > 0 && active.health <= 1, 'Learned lethal strike leaves the player alive at the configured survival health')
        return { assertions: ['unlearned player dies from the same lethal strike', 'learned guard prevents death and leaves positive survival health'], measurements: { unlearned, active } }
    },
})

chronosBehaviorCases.set('chronos-instant-recall', {
    stage: 'chronos-recall', negativeWindowTicks: 1,
    sound: 'minecraft:block.note_block.bass', soundVolume: 0.45, soundPitch: 0.82, particle: 'totem_of_undying', particleCount: 26,
    async trigger({ actor, equip, snapshot, context }) {
        await equip('clock')
        const count = () => actor.bot.inventory.items().filter(item => item.name === 'clock').reduce((sum, item) => sum + item.count, 0)
        const origin = actor.bot.entity.position.clone()
        await actor.bot.waitForTicks(110)
        const before = await snapshot()
        const clocks = count()
        await actor.bot.look(0, 0, true)
        actor.bot.setControlState('forward', true)
        await actor.bot.waitForTicks(24)
        actor.bot.setControlState('forward', false)
        await actor.bot.waitForTicks(5)
        const distance = actor.bot.entity.position.distanceTo(origin)
        context.expect(distance > 3, 'Recall trial walks away from a naturally recorded position')
        actor.bot.activateItem()
        await actor.bot.waitForTicks(70)
        actor.bot.deactivateItem()
        const after = await snapshot()
        return { distance, returnedDistance: actor.bot.entity.position.distanceTo(origin), consumed: clocks - count(), healthBefore: before.health, healthAfter: after.health, gameMode: actor.bot.game.gameMode }
    },
    async verify({ context, unlearned, active }) {
        context.expect(unlearned.returnedDistance > 3 && unlearned.consumed === 0, 'Unlearned clock use does not recall or consume a clock')
        context.expect(active.returnedDistance < 0.6 && active.consumed === 1 && active.gameMode === 'survival', 'Learned recall returns to its recorded position, consumes one clock and restores survival mode')
        context.expect(Math.abs(active.healthAfter - active.healthBefore * 0.5) < 0.01, 'Recall charges the configured health fraction')
        return { assertions: ['unlearned clock has no recall behavior', 'natural history returns the learned player to the earlier location', 'exactly one clock and half the initial health are charged', 'temporary spectator mode ends in survival'], measurements: { unlearned, active } }
    },
})

for (const [name, stage, item, amplifier] of [
    ['chronos-stasis-field', 'chronos-stasis', 'amethyst_shard', 5],
    ['chronos-time-bomb', 'chronos-bomb', 'lingering_potion', 2],
]) chronosBehaviorCases.set(name, {
    stage, negativeWindowTicks: 1,
    sound: 'minecraft:block.note_block.bass', soundVolume: 0.8, soundPitch: 0.6, particle: 'flash', particleCount: 1,
    async trigger({ actor, equip, snapshot }) {
        await equip(item)
        const count = () => actor.bot.inventory.items().filter(value => value.name === item).reduce((sum, value) => sum + value.count, 0)
        const before = await snapshot()
        const items = count()
        actor.bot.setControlState('sneak', true)
        await actor.bot.look(0, item === 'amethyst_shard' ? 0 : -0.8, true)
        await actor.bot.waitForTicks(3)
        actor.bot.activateItem()
        await actor.bot.waitForTicks(40)
        actor.bot.deactivateItem()
        actor.bot.setControlState('sneak', false)
        const during = await snapshot()
        await actor.bot.waitForTicks(240)
        const after = await snapshot()
        return { consumed: items - count(), before: before.chronos, during: during.chronos, after: after.chronos }
    },
    async verify({ context, unlearned, active }) {
        context.expect(unlearned.consumed === 0 && unlearned.during.movementSpeed === unlearned.before.movementSpeed, 'Unlearned interaction creates no temporal field or item cost')
        context.expect(active.consumed === 1 && (amplifier === 5 ? active.during.movementSpeed < active.before.movementSpeed : active.before.ai && !active.during.ai), 'Learned cast consumes one item and applies its actual mob lock')
        context.expect(active.after.movementSpeed === active.before.movementSpeed && active.after.jumpStrength === active.before.jumpStrength && active.after.ai === active.before.ai, 'Natural field expiration restores mob attributes')
        if (amplifier === 5) context.expect(active.during.jumpStrength === 0, 'Stasis fully suppresses mob jumping')
        return { assertions: ['unlearned use leaves the nearby mob unchanged', 'learned cast consumes exactly one item', 'live mob movement attributes are suppressed during the field', 'attributes return to their original values after natural expiry'], measurements: { unlearned, active } }
    },
})

chronosBehaviorCases.set('chronos-time-bottle', {
    stage: 'chronos-bottle', negativeWindowTicks: 1,
    sound: 'minecraft:block.lever.click', soundVolume: 0.55, soundPitch: 0.8 + 60 / 175, particle: 'composter', particleCount: 6,
    async trigger({ actor, equip, snapshot }) {
        await equip('potion')
        const before = (await snapshot()).chronos
        await actor.bot.waitForTicks(65)
        const charged = (await snapshot()).chronos
        const crop = actor.bot.blockAt(actor.bot.entity.position.offset(2, 0, 0).floored())
        await actor.bot.activateBlock(crop)
        await actor.bot.waitForTicks(4)
        return { before, charged, after: (await snapshot()).chronos }
    },
    async verify({ context, unlearned, active }) {
        context.expect(unlearned.charged.storedSeconds === 120 && unlearned.after.storedSeconds === 120 && unlearned.after.cropAge === 0, 'Unlearned bottle neither charges nor accelerates the crop')
        context.expect(active.charged.storedSeconds > active.before.storedSeconds, 'Learned carried bottle gains stored seconds from natural runtime pulses')
        context.expect(active.after.cropAge > 0 && active.after.storedSeconds < active.charged.storedSeconds, 'Natural crop interaction spends stored time to advance growth with random ticks disabled')
        return { assertions: ['unlearned bottle preserves its stored time and the crop age', 'learned bottle accumulates time naturally', 'actual crop use advances growth and deducts stored time'], measurements: { unlearned, active } }
    },
})

chronosBehaviorCases.set('chronos-accelerate', {
    stage: 'chronos-accelerate', negativeWindowTicks: 1,
    sound: 'minecraft:block.amethyst_block.chime', soundVolume: 0.3, soundPitch: 1.6, particle: 'enchant', particleCount: 2,
    async trigger({ actor, snapshot }) {
        const before = (await snapshot()).chronos
        await actor.bot.waitForTicks(360)
        const after = (await snapshot()).chronos
        return { before, after, excessCookTicks: (after.cooked - before.cooked) * 200 + after.cookProgress - before.cookProgress - (after.serverTick - before.serverTick) * 280 }
    },
    async verify({ context, unlearned, active }) {
        context.expect(Math.abs(unlearned.excessCookTicks) <= 280, 'Unlearned furnaces advance at the ordinary server tick rate')
        context.expect(active.excessCookTicks > unlearned.excessCookTicks + 120, 'Natural learned aura pulses advance furnace progress beyond the same elapsed tick budget')
        return { assertions: ['unlearned furnaces retain vanilla cooking speed', 'learned aura adds measured cooking progress beyond elapsed server ticks'], measurements: { unlearned, active, furnaceCount: 280, observationTicks: 360 } }
    },
})
