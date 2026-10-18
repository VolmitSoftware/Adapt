async function visibleGolem(input, key, label) {
    const state = (await input.snapshot()).golemancy[key]
    input.context.expect(Boolean(state), `Golemancy ${label} exists`)
    await input.context.waitUntil(() => Boolean(input.actor.bot.entities[state.entityId]), {
        label: `golemancy ${label} visible to player`, timeoutMs: 5000,
    })
    return input.actor.bot.entities[state.entityId]
}

function bindGolem(key, label, aimHeight) {
    return async input => {
        const { actor, snapshot } = input
        const golem = await visibleGolem(input, key, label)
        actor.bot.setControlState('sneak', true)
        try {
            await actor.bot.waitForTicks(4)
            await actor.bot.lookAt(golem.position.offset(0, aimHeight, 0), true)
            actor.bot.activateEntity(golem)
            await actor.bot.waitForTicks(70)
        } finally {
            actor.bot.setControlState('sneak', false)
        }
        return (await snapshot()).golemancy[key]
    }
}

const bind = bindGolem('golem', 'snow golem', 0.9)
const bindCopper = bindGolem('copperGolem', 'copper golem', 0.5)
const bindIron = bindGolem('ironGolem', 'iron golem', 1.2)

async function placeNode(input) {
    const { context, actor, snapshot } = input
    const position = actor.bot.entity.position.clone().set(2, 100, 0)
    await context.waitUntil(() => actor.bot.blockAt(position)?.name === 'chest', {
        label: 'golemancy node chest available', timeoutMs: 5000,
    })
    actor.bot.setControlState('sneak', true)
    try {
        await actor.bot.waitForTicks(4)
        await actor.bot.activateBlock(actor.bot.blockAt(position))
        await actor.bot.waitForTicks(10)
    } finally {
        actor.bot.setControlState('sneak', false)
    }
    return (await snapshot()).golemancy.node
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
    ['golem-copper-frame', {
        stage: 'golemancy-copper-binding', trigger: bindCopper, prerequisites: [{ name: 'golem-animus-binding', level: 5 }],
        prepare: ({ equip }) => equip('firework_star'),
        description: 'binding a copper golem needs Copper Frame, which raises its maximum health, heals it by the bonus and makes it faster',
        async verify({ context, unlearned, active }) {
            context.expect(!unlearned.bound, 'Without Copper Frame the copper golem stays unbound')
            context.expect(active.bound && active.boundToPlayer, 'With Copper Frame the copper golem binds to the player')
            context.expect(Math.abs(active.maxHealth - (active.maxHealthBase + 20)) < 0.0001, 'Copper Frame level 5 adds 20 maximum health to the bound copper golem')
            context.expect(Math.abs(active.health - active.maxHealth) < 0.0001, 'The health bonus also heals the golem to its new maximum')
            context.expect(Math.abs(active.speed - active.speedBase * 1.2) < 0.0001, 'Copper Frame level 5 makes the unweathered copper golem 20% faster')
            return {
                assertions: ['unlearned binding leaves the copper golem unbound', 'learned binding adds 20 maximum health, heals the golem to full, and raises its speed by 20%'],
                measurements: { unlearned, active },
            }
        },
    }],
    ['golem-iron-frame', {
        stage: 'golemancy-iron-binding', trigger: bindIron, prerequisites: [{ name: 'golem-animus-binding', level: 5 }],
        prepare: ({ equip }) => equip('firework_star'),
        description: 'binding an iron golem needs Iron Frame, which raises its maximum health, armor and attack and heals it by the health bonus',
        async verify({ context, unlearned, active }) {
            context.expect(!unlearned.bound, 'Without Iron Frame the iron golem stays unbound')
            context.expect(active.bound && active.boundToPlayer, 'With Iron Frame the iron golem binds to the player')
            context.expect(Math.abs(active.scale - 0.36) < 0.0001, 'The bound iron golem shrinks to the configured iron scale')
            context.expect(Math.abs(active.maxHealth - (active.maxHealthBase + 50)) < 0.0001, 'Iron Frame level 5 adds 50 maximum health to the bound iron golem')
            context.expect(Math.abs(active.health - active.maxHealth) < 0.0001, 'The health bonus also heals the golem to its new maximum')
            context.expect(Math.abs(active.armor - (active.armorBase + 10)) < 0.0001, 'Iron Frame level 5 adds 10 armor')
            context.expect(Math.abs(active.attack - (active.attackBase + 7.5)) < 0.0001, 'Iron Frame level 5 adds 7.5 attack damage')
            return {
                assertions: ['unlearned binding leaves the iron golem unbound', 'learned binding shrinks the golem to 0.36 scale, adds 50 maximum health, 10 armor and 7.5 attack, and heals it to full'],
                measurements: { unlearned, active },
            }
        },
    }],
    ['golem-guardian-core', {
        stage: 'golemancy-guardian-binding', trigger: bind, prerequisites: [{ name: 'golem-animus-binding', level: 5 }],
        prepare: ({ equip }) => equip('firework_star'),
        description: 'binding a snow golem with a Guardian Core needs Guardian Core learned and turns the golem into a working Guardian',
        async verify({ context, unlearned, active }) {
            context.expect(!unlearned.bound, 'Without Guardian Core the snow golem refuses the Guardian Core and stays unbound')
            context.expect(active.bound && active.boundToPlayer, 'With Guardian Core the snow golem binds to the player')
            context.expect(active.core === 'guardian', 'The bound golem carries the Guardian Core')
            context.expect(active.mode === 'work', 'A new Guardian starts in Work mode, guarding its home')
            return {
                assertions: ['unlearned binding leaves the snow golem unbound', 'learned binding makes the snow golem a Guardian in Work mode'],
                measurements: { unlearned, active },
            }
        },
    }],
    ['golem-node-weaving', {
        stage: 'golemancy-node', trigger: placeNode, prerequisites: [{ name: 'golem-animus-binding', level: 1 }],
        prepare: ({ equip }) => equip('brick'),
        description: 'sneak-right-clicking the top of a chest with a Deposit Node anchors the node there and uses up one node item',
        sound: 'minecraft:block.amethyst_block.place', soundVolume: 0.6, soundPitch: 1.4, particle: 'end_rod',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.nodes === 0, 'Unlearned sneak-right-click anchors no node')
            context.expect(unlearned.nodeItems === 2, 'Unlearned player keeps both Deposit Nodes')
            context.expect(active.nodes === 1 && active.type === 'deposit' && active.face === 'UP', 'Learned sneak-right-click anchors a Deposit Node to the top of the chest')
            context.expect(active.ownedByPlayer, 'The anchored node belongs to the player')
            context.expect(active.nodeItems === 1, 'Placing the node uses up one Deposit Node')
            return {
                assertions: ['unlearned click anchors nothing and keeps both nodes', 'learned click anchors one player-owned Deposit Node on the chest top and uses one item'],
                measurements: { unlearned, active },
            }
        },
    }],
])
