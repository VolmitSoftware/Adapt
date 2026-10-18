async function visibleGolem(input) {
    const state = (await input.snapshot()).golemancy.golem
    input.context.expect(Boolean(state), 'Golemancy snow golem exists')
    await input.context.waitUntil(() => Boolean(input.actor.bot.entities[state.entityId]), {
        label: 'golemancy snow golem visible to player', timeoutMs: 5000,
    })
    return input.actor.bot.entities[state.entityId]
}

async function bind(input) {
    const { actor, snapshot } = input
    const golem = await visibleGolem(input)
    actor.bot.setControlState('sneak', true)
    try {
        await actor.bot.waitForTicks(4)
        await actor.bot.lookAt(golem.position.offset(0, 0.9, 0), true)
        actor.bot.activateEntity(golem)
        await actor.bot.waitForTicks(70)
    } finally {
        actor.bot.setControlState('sneak', false)
    }
    return (await snapshot()).golemancy.golem
}

export const golemancyBehaviorCases = new Map([
    ['golem-animus-binding', {
        stage: 'golemancy-binding', trigger: bind,
        prepare: ({ equip }) => equip('firework_star'),
        description: 'sneak-right-clicking a snow golem with a Blank Animus Core binds it to the player after the ritual',
        sound: 'minecraft:block.enchantment_table.use', soundVolume: 0.7, soundPitchRange: [0.8, 1.6], particle: 'end_rod',
        async verify({ context, unlearned, active }) {
            context.expect(!unlearned.bound, 'Unlearned sneak-right-click leaves the snow golem unbound')
            context.expect(Math.abs(unlearned.scale - 1) < 0.0001, 'Unlearned snow golem keeps its normal size')
            context.expect(active.bound && active.boundToPlayer, 'Learned ritual binds the snow golem to the player')
            context.expect(Math.abs(active.scale - 0.5) < 0.0001, 'Bound snow golem shrinks to the configured frost scale')
            context.expect(active.aware, 'Bound snow golem is awake after the ritual')
            return {
                assertions: ['unlearned snow golem stays unbound at normal size', 'learned ritual binds the golem to the player, shrinks it to 0.5 scale, and wakes it'],
                measurements: { unlearned, active },
            }
        },
    }],
    ['golem-frost-frame', {
        stage: 'golemancy-binding', trigger: bind, prerequisites: [{ name: 'golem-animus-binding', level: 5 }],
        prepare: ({ equip }) => equip('firework_star'),
        description: 'binding a snow golem with Frost Frame learned raises its maximum health and heals it by the bonus',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.bound && unlearned.boundToPlayer, 'The snow golem binds while Frost Frame is unlearned')
            context.expect(Math.abs(unlearned.maxHealth - 4) < 0.0001, 'Without Frost Frame the bound snow golem keeps its 4 maximum health')
            context.expect(active.bound && active.boundToPlayer, 'The snow golem binds while Frost Frame is learned')
            context.expect(Math.abs(active.maxHealth - 24) < 0.0001, 'Frost Frame level 5 adds 20 maximum health to the bound snow golem')
            context.expect(Math.abs(active.health - 24) < 0.0001, 'The health bonus also heals the golem by 20')
            return {
                assertions: ['unlearned binding leaves the snow golem at 4 maximum health', 'learned binding raises the snow golem to 24 maximum health and heals it to full'],
                measurements: { unlearned, active },
            }
        },
    }],
])
