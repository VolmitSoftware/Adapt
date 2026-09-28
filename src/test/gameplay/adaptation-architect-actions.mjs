async function place({ context, actor }) {
    actor.bot.setQuickBarSlot(0)
    actor.bot.setControlState('sneak', true)
    await actor.bot.waitForTicks(3)
    const position = actor.bot.entity.position.clone().set(2, 101, 2)
    await context.waitUntil(() => actor.bot.blockAt(position)?.name === 'stone', {
        label: 'bridge anchor loaded', timeoutMs: 5000,
    })
    await actor.bot.placeBlock(actor.bot.blockAt(position), position.clone().set(0, 0, -1))
    await context.waitUntil(() => actor.bot.blockAt(position.offset(0, 0, -1))?.name === 'stone', {
        label: 'survival placement creates the bridge block', timeoutMs: 5000,
    })
    await actor.bot.waitForTicks(5)
}

export const architectBehaviorCases = new Map([
    ['architect-steady-hands', {
        stage: 'build-bridge', attribute: 'minecraft:knockback_resistance', negativeWindowTicks: 10,
        sound: 'minecraft:block.wool.step', soundVolume: 0.3, soundPitch: 1.2, particle: 'dust',
        trigger: place,
    }],
    ['architect-supply-line', {
        stage: 'build-refill', negativeWindowTicks: 10,
        sound: 'minecraft:block.composter.fill', soundVolume: 0.6, soundPitch: 1.3, particle: 'wax_on',
        async trigger(input) {
            await place(input)
            return {
                held: input.actor.bot.heldItem?.name ?? null,
                heldCount: input.actor.bot.heldItem?.count ?? 0,
                remaining: input.actor.bot.inventory.items().filter(item => item.name === 'stone').reduce((sum, item) => sum + item.count, 0),
            }
        },
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.held === null, 'Unlearned placement consumes the last held stone')
            context.expect(active.held === 'stone' && active.heldCount === 32, 'Learned placement refills the hand with the reserve stack')
            context.expect(unlearned.remaining === 32 && active.remaining === 32, 'Refilling conserves all remaining stone')
            return { assertions: ['both trials place and consume one stone', 'only learned placement refills the empty hand', 'reserve transfer conserves remaining inventory'], measurements: { unlearned, active } }
        },
    }],
    ['architect-demolition', {
        stage: 'build-demolition', negativeWindowTicks: 5,
        sound: 'minecraft:block.amethyst_block.hit', soundVolume: 0.5, soundPitch: 0.7, particle: 'scrape',
        async trigger(input) {
            await place(input)
            const { actor, context } = input
            actor.bot.setQuickBarSlot(8)
            await actor.bot.waitForTicks(3)
            const target = actor.bot.entity.position.clone().set(2, 101, 1)
            const started = Date.now()
            await actor.bot.dig(actor.bot.blockAt(target))
            await context.waitUntil(() => actor.bot.blockAt(target)?.name === 'air', { label: 'placed stone removed', timeoutMs: 10000 })
            const elapsedMillis = Date.now() - started
            await actor.bot.waitForTicks(8)
            return { elapsedMillis, stone: actor.bot.inventory.items().filter(item => item.name === 'stone').reduce((sum, item) => sum + item.count, 0) }
        },
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.elapsedMillis > 3000 && active.elapsedMillis < unlearned.elapsedMillis / 2,
                'Learned demolition breaks placed stone much faster than bare-hand mining')
            context.expect(unlearned.stone === 3 && active.stone === 4, 'Learned demolition returns exactly the placed stone')
            return { assertions: ['unlearned bare-hand stone mining gives no stone', 'learned demolition instantly breaks the placed stone and restores its original item'], measurements: { unlearned, active } }
        },
    }],
])

architectBehaviorCases.set('architect-scaffolder', {
    stage: 'build-bridge', negativeWindowTicks: 1,
    sound: 'minecraft:block.deepslate.place', soundVolume: 0.6, soundPitch: 1.6, particle: 'reverse_portal',
    async trigger(input) {
        await place(input)
        await input.actor.bot.waitForTicks(640)
        const target = input.actor.bot.entity.position.clone().set(2, 101, 1)
        return { block: input.actor.bot.blockAt(target)?.name, stone: input.actor.bot.inventory.items().filter(item => item.name === 'stone').reduce((sum, item) => sum + item.count, 0) }
    },
    async verify({ context, unlearned, active }) {
        context.expect(unlearned.block === 'stone' && unlearned.stone === 3, 'Unlearned placement persists beyond the full scaffold lifetime')
        context.expect(active.block === 'air' && active.stone === 4, 'Learned scaffold dissolves and refunds exactly one stone')
        return { assertions: ['unlearned block persists beyond 30 seconds', 'learned scaffold dissolves after its configured lifetime', 'expired scaffold refunds the original block'], measurements: { unlearned, active } }
    },
})
architectBehaviorCases.set('architect-foundation', {
    stage: 'build-foundation', negativeWindowTicks: 1,
    sound: 'minecraft:block.note_block.hat', soundVolume: 0.5, soundPitch: 0.7, particle: 'dust',
    async trigger({ actor, snapshot, context }) {
        actor.bot.setControlState('jump', true)
        await actor.bot.waitForTicks(8)
        actor.bot.setControlState('jump', false)
        context.expect(actor.bot.entity.position.y > 105.5, 'Player jumps above the foundation anchor')
        const before = await snapshot()
        actor.bot.setControlState('sneak', true)
        await actor.bot.waitForTicks(5)
        const after = await snapshot()
        const glass = []
        for (let x = -1; x <= 1; x++) for (let y = 105; y <= 107; y++) for (let z = -1; z <= 1; z++) {
            const position = actor.bot.entity.position.clone().set(x, y, z)
            if (actor.bot.blockAt(position)?.name === 'tinted_glass') glass.push({ x, y, z })
        }
        actor.bot.setControlState('sneak', false)
        await actor.bot.waitForTicks(65)
        const remaining = glass.filter(position => actor.bot.blockAt(actor.bot.entity.position.clone().set(position.x, position.y, position.z))?.name === 'tinted_glass')
        return { glass, remaining, placed: (after.stats['architect.foundation.blocks-placed'] ?? 0) - (before.stats['architect.foundation.blocks-placed'] ?? 0) }
    },
    async verify({ context, unlearned, active }) {
        context.expect(unlearned.glass.length === 0 && unlearned.placed === 0, 'Unlearned midair sneak creates no platform')
        context.expect(active.glass.length > 0 && active.placed === active.glass.length, 'Learned midair sneak creates observed temporary glass blocks')
        context.expect(active.remaining.length === 0, 'Every observed foundation block expires after its configured lifetime')
        return { assertions: ['unlearned midair sneak creates no platform', 'learned midair sneak creates actual glass blocks under the player', 'the temporary platform expires naturally'], measurements: { unlearned, active } }
    },
})
