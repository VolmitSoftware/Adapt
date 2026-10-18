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

async function signalHold(input) {
    const { actor, snapshot } = input
    const bound = await bind(input)
    actor.bot.setQuickBarSlot(1)
    await actor.bot.waitForTicks(4)
    await actor.bot.look(actor.bot.entity.yaw, Math.PI / 2, true)
    actor.bot.setControlState('sneak', true)
    try {
        await actor.bot.waitForTicks(4)
        actor.bot.swingArm('right')
        await actor.bot.waitForTicks(4)
    } finally {
        actor.bot.setControlState('sneak', false)
    }
    await actor.bot.waitForTicks(25)
    actor.bot.swingArm('right')
    await actor.bot.waitForTicks(20)
    return { bound, signaled: (await snapshot()).golemancy.golem }
}

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

async function loadPlinth(input) {
    const { context, actor, snapshot } = input
    const center = actor.bot.entity.position.clone().set(2, 100, 3)
    await context.waitUntil(() => actor.bot.blockAt(center)?.name === 'lodestone', {
        label: 'golemancy plinth lodestone available', timeoutMs: 5000,
    })
    const sneakClick = async () => {
        actor.bot.setControlState('sneak', true)
        try {
            await actor.bot.waitForTicks(4)
            await actor.bot.activateBlock(actor.bot.blockAt(center))
            await actor.bot.waitForTicks(20)
        } finally {
            actor.bot.setControlState('sneak', false)
        }
    }
    await sneakClick()
    const loaded = (await snapshot()).golemancy.plinth
    actor.bot.setQuickBarSlot(1)
    await actor.bot.waitForTicks(2)
    await sneakClick()
    const retrieved = (await snapshot()).golemancy.plinth
    return { loaded, retrieved }
}

async function mendGolem(input) {
    const { actor, snapshot } = input
    const golem = await visibleGolem(input, 'golem', 'bound snow golem')
    await actor.bot.waitForTicks(4)
    await actor.bot.lookAt(golem.position.offset(0, 0.5, 0), true)
    actor.bot.activateEntity(golem)
    await actor.bot.waitForTicks(10)
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
    ['golem-gatherer-core', {
        stage: 'golemancy-gatherer-binding', trigger: bind,
        prerequisites: [{ name: 'golem-animus-binding', level: 5 }, { name: 'golem-node-weaving', level: 1 }],
        prepare: ({ equip }) => equip('firework_star'),
        description: 'binding a snow golem with a Gatherer Core needs Gatherer Core learned and turns the golem into a working Gatherer',
        async verify({ context, unlearned, active }) {
            context.expect(!unlearned.bound, 'Without Gatherer Core the snow golem refuses the Gatherer Core and stays unbound')
            context.expect(active.bound && active.boundToPlayer, 'With Gatherer Core the snow golem binds to the player')
            context.expect(active.core === 'gatherer', 'The bound golem carries the Gatherer Core')
            context.expect(active.mode === 'work', 'A new Gatherer starts in Work mode, gathering around its home')
            return {
                assertions: ['unlearned binding leaves the snow golem unbound', 'learned binding makes the snow golem a Gatherer in Work mode'],
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
    ['golem-harvester-core', {
        stage: 'golemancy-harvester-binding', trigger: bind,
        prerequisites: [{ name: 'golem-animus-binding', level: 5 }, { name: 'golem-node-weaving', level: 1 }],
        prepare: ({ equip }) => equip('firework_star'),
        description: 'binding a snow golem with a Harvester Core needs Harvester Core learned and turns the golem into a working Harvester',
        async verify({ context, unlearned, active }) {
            context.expect(!unlearned.bound, 'Without Harvester Core the snow golem refuses the Harvester Core and stays unbound')
            context.expect(active.bound && active.boundToPlayer, 'With Harvester Core the snow golem binds to the player')
            context.expect(active.core === 'harvester', 'The bound golem carries the Harvester Core')
            context.expect(active.mode === 'work', 'A new Harvester starts in Work mode, ready for Harvest Nodes')
            return {
                assertions: ['unlearned binding leaves the snow golem unbound', 'learned binding makes the snow golem a Harvester in Work mode'],
                measurements: { unlearned, active },
            }
        },
    }],
    ['golem-lumber-core', {
        stage: 'golemancy-lumber-binding', trigger: bindCopper,
        prerequisites: [{ name: 'golem-animus-binding', level: 5 }, { name: 'golem-node-weaving', level: 1 }, { name: 'golem-copper-frame', level: 1 }],
        prepare: ({ equip }) => equip('firework_star'),
        description: 'binding a copper golem with a Lumber Core needs Lumber Core learned and turns the golem into a working Lumber golem',
        async verify({ context, unlearned, active }) {
            context.expect(!unlearned.bound, 'Without Lumber Core the copper golem refuses the Lumber Core and stays unbound')
            context.expect(active.bound && active.boundToPlayer, 'With Lumber Core the copper golem binds to the player')
            context.expect(active.core === 'lumber', 'The bound golem carries the Lumber Core')
            context.expect(active.mode === 'work', 'A new Lumber golem starts in Work mode, ready for Lumber Nodes')
            return {
                assertions: ['unlearned binding leaves the copper golem unbound', 'learned binding makes the copper golem a Lumber golem in Work mode'],
                measurements: { unlearned, active },
            }
        },
    }],
    ['golem-breaker-core', {
        stage: 'golemancy-breaker-binding', trigger: bindIron,
        prerequisites: [{ name: 'golem-animus-binding', level: 5 }, { name: 'golem-node-weaving', level: 1 }, { name: 'golem-iron-frame', level: 1 }],
        prepare: ({ equip }) => equip('firework_star'),
        description: 'binding an iron golem with a Breaker Core needs Breaker Core learned and turns the golem into a working Breaker',
        async verify({ context, unlearned, active }) {
            context.expect(!unlearned.bound, 'Without Breaker Core the iron golem refuses the Breaker Core and stays unbound')
            context.expect(active.bound && active.boundToPlayer, 'With Breaker Core the iron golem binds to the player')
            context.expect(active.core === 'breaker', 'The bound golem carries the Breaker Core')
            context.expect(active.mode === 'work', 'A new Breaker starts in Work mode, ready for Breaker Nodes')
            return {
                assertions: ['unlearned binding leaves the iron golem unbound', 'learned binding makes the iron golem a Breaker in Work mode'],
                measurements: { unlearned, active },
            }
        },
    }],
    ['golem-rancher-core', {
        stage: 'golemancy-rancher-binding', trigger: bindCopper,
        prerequisites: [{ name: 'golem-animus-binding', level: 5 }, { name: 'golem-node-weaving', level: 1 }, { name: 'golem-copper-frame', level: 1 }],
        prepare: ({ equip }) => equip('firework_star'),
        description: 'binding a copper golem with a Rancher Core needs Rancher Core learned and turns the golem into a working Rancher',
        async verify({ context, unlearned, active }) {
            context.expect(!unlearned.bound, 'Without Rancher Core the copper golem refuses the Rancher Core and stays unbound')
            context.expect(active.bound && active.boundToPlayer, 'With Rancher Core the copper golem binds to the player')
            context.expect(active.core === 'rancher', 'The bound golem carries the Rancher Core')
            context.expect(active.mode === 'work', 'A new Rancher starts in Work mode, ready for Pasture Nodes')
            return {
                assertions: ['unlearned binding leaves the copper golem unbound', 'learned binding makes the copper golem a Rancher in Work mode'],
                measurements: { unlearned, active },
            }
        },
    }],
    ['golem-sorter-core', {
        stage: 'golemancy-sorter-binding', trigger: bindCopper,
        prerequisites: [{ name: 'golem-animus-binding', level: 5 }, { name: 'golem-node-weaving', level: 1 }, { name: 'golem-copper-frame', level: 1 }],
        prepare: ({ equip }) => equip('firework_star'),
        description: 'binding a copper golem with a Sorter Core needs Sorter Core learned and turns the golem into a working Sorter',
        async verify({ context, unlearned, active }) {
            context.expect(!unlearned.bound, 'Without Sorter Core the copper golem refuses the Sorter Core and stays unbound')
            context.expect(active.bound && active.boundToPlayer, 'With Sorter Core the copper golem binds to the player')
            context.expect(active.core === 'sorter', 'The bound golem carries the Sorter Core')
            context.expect(active.mode === 'work', 'A new Sorter starts in Work mode, ready for Input Nodes')
            return {
                assertions: ['unlearned binding leaves the copper golem unbound', 'learned binding makes the copper golem a Sorter in Work mode'],
                measurements: { unlearned, active },
            }
        },
    }],
    ['golem-angler-core', {
        stage: 'golemancy-angler-binding', trigger: bindCopper,
        prerequisites: [{ name: 'golem-animus-binding', level: 5 }, { name: 'golem-node-weaving', level: 1 }, { name: 'golem-copper-frame', level: 1 }],
        prepare: ({ equip }) => equip('firework_star'),
        description: 'binding a copper golem with an Angler Core needs Angler Core learned and turns the golem into a working Angler',
        async verify({ context, unlearned, active }) {
            context.expect(!unlearned.bound, 'Without Angler Core the copper golem refuses the Angler Core and stays unbound')
            context.expect(active.bound && active.boundToPlayer, 'With Angler Core the copper golem binds to the player')
            context.expect(active.core === 'angler', 'The bound golem carries the Angler Core')
            context.expect(active.mode === 'work', 'A new Angler starts in Work mode, ready for Fishing Nodes')
            return {
                assertions: ['unlearned binding leaves the copper golem unbound', 'learned binding makes the copper golem an Angler in Work mode'],
                measurements: { unlearned, active },
            }
        },
    }],
    ['golem-overseer', {
        stage: 'golemancy-overseer', trigger: signalHold, prerequisites: [{ name: 'golem-animus-binding', level: 5 }],
        prepare: ({ equip }) => equip('firework_star'),
        description: 'sneak-left-clicking the air with the Crook cycles the signal to Hold, and left-clicking sends it to the bound companion',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.bound.bound && unlearned.bound.mode === 'follow', 'Without Overseer the companion binds and follows the player')
            context.expect(unlearned.signaled.mode === 'follow', 'Without Overseer the Crook signal leaves the companion following')
            context.expect(active.bound.bound && active.bound.mode === 'follow', 'With Overseer the companion binds and follows the player')
            context.expect(active.signaled.mode === 'stay', 'With Overseer the Hold signal makes the companion stay')
            return {
                assertions: ['unlearned Crook signals leave the companion in Follow mode', 'learned Hold signal switches the companion to Stay mode'],
                measurements: { unlearned, active },
            }
        },
    }],
    ['golem-runic-augments', {
        stage: 'golemancy-plinth', trigger: loadPlinth, prerequisites: [{ name: 'golem-animus-binding', level: 2 }],
        prepare: ({ equip }) => equip('disc_fragment_5'),
        description: 'sneak-right-clicking an Animus Plinth lodestone with a Blank Rune places it on the plinth, and an empty-hand sneak-right-click takes it back',
        async verify({ context, unlearned, active }) {
            context.expect(!unlearned.loaded.center && unlearned.loaded.blankRunes === 1, 'Without Runic Augments the plinth takes nothing and the Blank Rune stays with the player')
            context.expect(active.loaded.center === 'blank_rune' && active.loaded.blankRunes === 0, 'With Runic Augments the Blank Rune moves onto the lodestone')
            context.expect(!active.retrieved.center && active.retrieved.blankRunes === 1, 'An empty-hand sneak-right-click takes the Blank Rune back')
            return {
                assertions: ['unlearned click leaves the plinth empty and keeps the Blank Rune', 'learned click loads the Blank Rune onto the lodestone and an empty-hand click returns it'],
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
    ['golem-mending', {
        stage: 'golemancy-mending', trigger: mendGolem, prerequisites: [{ name: 'golem-animus-binding', level: 1 }],
        prepare: ({ equip }) => equip('snow_block'),
        description: 'right-clicking your hurt bound snow golem with a snow block heals it by the base amount plus the level bonus',
        sound: 'minecraft:block.amethyst_block.chime', soundVolume: 0.6, soundPitch: 1.6, particle: 'heart',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.bound && unlearned.boundToPlayer, 'The snow golem is bound to the player before the unlearned click')
            context.expect(Math.abs(unlearned.health - 2) < 0.0001, 'Without Golem Mending the snow block leaves the golem at 2 health')
            context.expect(active.bound && active.boundToPlayer, 'The snow golem is bound to the player before the learned click')
            context.expect(active.health >= 6 - 0.0001 && active.health <= 7 + 0.0001, 'Golem Mending level 5 heals 2 health doubled by the level bonus')
            return {
                assertions: ['unlearned snow block click leaves the bound golem at 2 health', 'learned snow block click heals the bound golem by 4 health'],
                measurements: { unlearned, active },
            }
        },
    }],
])
