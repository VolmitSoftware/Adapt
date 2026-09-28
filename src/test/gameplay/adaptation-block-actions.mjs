function position(actor, x, y, z) {
    const current = actor.bot.entity.position
    return current.offset(x - current.x, y - current.y, z - current.z)
}

function count(actor, name) {
    return actor.bot.inventory.items().filter(item => item.name === name).reduce((total, item) => total + item.count, 0)
}

async function prepare({ context, actor, equip }, tool, blockName, sneak = false) {
    actor.bot.clearControlStates()
    if (tool) await equip(tool)
    await context.waitUntil(() => actor.bot.blockAt(position(actor, 3, 100, 0))?.name === blockName, {
        label: `${blockName} block fixture loaded`, timeoutMs: 5000,
    })
    actor.bot.setControlState('sneak', sneak)
    await actor.bot.waitForTicks(3)
}

async function dig({ context, actor }) {
    const target = actor.bot.blockAt(position(actor, 3, 100, 0))
    context.expect(Boolean(target) && target.name !== 'air', 'Natural dig starts with a real block')
    context.expect(actor.bot.entity.position.distanceTo(target.position.offset(0.5, 0.5, 0.5)) > 2.5,
        'Actor remains outside automatic item pickup range')
    let complete = false
    let failure
    const digging = actor.bot.dig(target).then(() => { complete = true }, error => {
        failure = error
        complete = true
    })
    try {
        await context.waitUntil(() => {
            if (failure) throw failure
            return complete
        }, { label: 'survival block digging completes', timeoutMs: 10000, intervalMs: 20 })
        await digging
    } finally {
        if (!complete) actor.bot.stopDigging()
    }
    await context.waitUntil(() => actor.bot.blockAt(target.position)?.name === 'air', {
        label: 'natural dig removes the selected block', timeoutMs: 5000,
    })
    await actor.bot.waitForTicks(12)
}

async function collect({ context, actor }) {
    actor.bot.setControlState('sneak', false)
    const destination = position(actor, 3.5, 100, 0.5)
    await actor.bot.lookAt(destination.offset(0, 1.5, 0), true)
    actor.bot.setControlState('forward', true)
    try {
        await context.waitUntil(() => actor.bot.entity.position.distanceTo(destination) < 0.65, {
            label: 'actor walks to collect natural block drops', timeoutMs: 4000, intervalMs: 20,
        })
    } finally {
        actor.bot.setControlState('forward', false)
    }
    await actor.bot.waitForTicks(20)
}

function pickupCase({ stage, tool, block, item, amount, sound, soundVolume, soundPitch, particle }) {
    return {
        stage, sound, soundVolume, soundPitch, particle,
        prepare: input => prepare(input, tool, block),
        trigger: async input => {
            const before = count(input.actor, item)
            const start = input.actor.bot.entity.position.clone()
            await dig(input)
            return { item, gained: count(input.actor, item) - before, movement: input.actor.bot.entity.position.distanceTo(start) }
        },
        verify: async ({ context, unlearned, active }) => {
            context.expect(unlearned.movement < 0.2 && active.movement < 0.2, 'Both pickup trials keep the actor stationary')
            context.expect(unlearned.gained === 0, 'Unlearned block drops do not enter distant inventory')
            context.expect(active.gained === amount, `Learned adaptation directly stores exactly ${amount} ${item}`)
            return {
                assertions: ['unlearned distant mining adds no inventory items', `learned stationary mining adds exactly ${amount} ${item}`],
                measurements: { unlearned, active },
            }
        },
    }
}

function multiBlockCase({ stage, tool, block, cells, sound, soundVolume, soundPitch, particle }) {
    return {
        stage, sound, soundVolume, soundPitch, particle,
        prepare: input => prepare(input, tool, block, true),
        trigger: async input => {
            const read = () => cells.map(([x, y, z]) => input.actor.bot.blockAt(position(input.actor, x, y, z))?.name)
            const before = read()
            input.context.expect(before.every(name => name === block), 'Every planned test block exists before digging')
            await dig(input)
            await input.actor.bot.waitForTicks(8)
            return { before, after: read() }
        },
        verify: async ({ context, unlearned, active }) => {
            context.expect(unlearned.after.filter(name => name === 'air').length === 1,
                'Unlearned dig removes only its directly targeted block')
            context.expect(unlearned.after.filter(name => name === block).length === cells.length - 1,
                'Unlearned dig leaves every adjacent test block intact')
            context.expect(active.after.every(name => name === 'air'), 'Learned dig removes every planned adjacent block')
            return {
                assertions: ['unlearned natural dig breaks exactly one block', `learned natural dig clears all ${cells.length} configured fixture blocks`],
                measurements: { unlearned, active },
            }
        },
    }
}

const plane = []
for (let y = 99; y <= 101; y++) for (let z = -1; z <= 1; z++) plane.push([3, y, z])
const column = [[3, 100, 0], [3, 101, 0], [3, 102, 0]]
const oreCluster = [[3, 100, 0], [3, 101, 0], [4, 100, 0], [4, 101, 0]]

export const blockBehaviorCases = new Map([
    ['axe-drop-to-inventory', pickupCase({
        stage: 'block-log', tool: 'diamond_axe', block: 'oak_log', item: 'oak_log', amount: 1,
        sound: 'minecraft:block.amethyst_block.hit', soundVolume: 0.3, soundPitch: 1.8, particle: 'enchant',
    })],
    ['pickaxe-drop-to-inventory', pickupCase({
        stage: 'block-iron', tool: 'diamond_pickaxe', block: 'iron_ore', item: 'raw_iron', amount: 1,
        sound: 'minecraft:block.amethyst_block.chime', soundVolume: 0.25, soundPitch: 1.6, particle: 'wax_on',
    })],
    ['excavation-drop-to-inventory', pickupCase({
        stage: 'block-clay', tool: 'diamond_shovel', block: 'clay', item: 'clay_ball', amount: 4,
        sound: 'minecraft:block.calcite.hit', soundVolume: 0.3, soundPitch: 1.6, particle: 'enchant',
    })],
    ['herbalism-drop-to-inventory', pickupCase({
        stage: 'block-pumpkin', tool: 'diamond_hoe', block: 'pumpkin', item: 'pumpkin', amount: 1,
        sound: 'minecraft:block.note_block.chime', soundVolume: 0.5, soundPitch: 1.6, particle: 'happy_villager',
    })],
    ['axe-wood-veinminer', multiBlockCase({
        stage: 'block-log', tool: 'diamond_axe', block: 'oak_log', cells: column,
        sound: 'minecraft:item.axe.strip', soundVolume: 1, soundPitch: 0.7, particle: 'crit',
    })],
    ['axe-leaf-veinminer', multiBlockCase({
        stage: 'block-leaves', tool: 'diamond_axe', block: 'oak_leaves', cells: column,
        sound: 'minecraft:entity.player.attack.sweep', soundVolume: 0.5, soundPitch: 1.2, particle: 'happy_villager',
    })],
    ['pickaxe-veinminer', multiBlockCase({
        stage: 'block-ore', tool: 'diamond_pickaxe', block: 'diamond_ore', cells: oreCluster,
        sound: 'minecraft:block.amethyst_cluster.break', soundVolume: 0.7, soundPitch: 0.9, particle: 'electric_spark',
    })],
    ['excavation-tunneler', multiBlockCase({
        stage: 'block-clay-plane', tool: 'diamond_shovel', block: 'clay', cells: plane,
        sound: 'minecraft:item.shovel.flatten', soundVolume: 0.7, soundPitch: 0.8, particle: 'block',
    })],
    ['pickaxe-tunnel-bore', multiBlockCase({
        stage: 'block-stone-plane', tool: 'diamond_pickaxe', block: 'stone', cells: plane,
        sound: 'minecraft:entity.ravager.step', soundVolume: 0.4, soundPitch: 0.6, particle: 'cloud',
    })],
    ['pickaxe-autosmelt', {
        stage: 'block-iron',
        sound: 'minecraft:block.lava.pop', soundVolume: 1, soundPitch: 1, particle: 'flame',
        prepare: input => prepare(input, 'diamond_pickaxe', 'iron_ore'),
        trigger: async input => {
            await dig(input)
            await collect(input)
            return { rawIron: count(input.actor, 'raw_iron'), ingots: count(input.actor, 'iron_ingot') }
        },
        verify: async ({ context, unlearned, active }) => {
            context.expect(unlearned.rawIron === 1 && unlearned.ingots === 0, 'Unlearned iron ore yields one raw iron')
            context.expect(active.rawIron === 0 && [1, 2].includes(active.ingots), 'Learned ore converts into ingots with optional one-item bonus')
            return {
                assertions: ['unlearned ore yields exactly one raw iron', 'learned ore yields ingots and no raw iron'],
                measurements: { unlearned, active },
            }
        },
    }],
    ['architect-glass', {
        stage: 'block-glass',
        sound: 'minecraft:block.large_amethyst_bud.break', soundVolume: 0.7, soundPitch: 1, particle: 'reverse_portal',
        prepare: input => prepare(input, undefined, 'glass'),
        trigger: async input => {
            input.context.expect(!input.actor.bot.heldItem, 'Glass recovery uses an empty hand')
            await dig(input)
            await collect(input)
            return { glass: count(input.actor, 'glass') }
        },
        verify: async ({ context, unlearned, active }) => {
            context.expect(unlearned.glass === 0, 'Unlearned bare-hand glass break yields no item')
            context.expect(active.glass === 1, 'Learned bare-hand glass break returns exactly one glass')
            return {
                assertions: ['unlearned glass break yields no glass', 'learned natural glass break recovers exactly one block'],
                measurements: { unlearned, active },
            }
        },
    }],
    ['herbalism-replant', {
        stage: 'block-carrots',
        sound: 'minecraft:item.shovel.flatten', soundVolume: 1, soundPitch: 0.66, particle: 'composter',
        prepare: input => prepare(input, 'diamond_hoe', 'carrots'),
        trigger: async ({ actor }) => {
            const location = position(actor, 3, 100, 0)
            const before = actor.bot.blockAt(location)
            const beforeAge = Number(before.getProperties().age)
            await actor.bot.activateBlock(before)
            await actor.bot.waitForTicks(8)
            const after = actor.bot.blockAt(location)
            return { before: { name: before.name, age: beforeAge }, after: { name: after.name, age: Number(after.getProperties().age) } }
        },
        verify: async ({ context, unlearned, active }) => {
            context.expect(unlearned.before.age === 7 && active.before.age === 7, 'Both harvest trials begin with mature carrots')
            context.expect(unlearned.after.name === 'carrots' && unlearned.after.age === 7, 'Unlearned right-click leaves the mature crop untouched')
            context.expect(active.after.name === 'carrots' && active.after.age >= 0 && active.after.age < 7,
                'Learned right-click harvests and replants carrots at a young growth age')
            return {
                assertions: ['unlearned right-click leaves mature carrots intact', 'learned hoe right-click resets carrot growth while preserving the planted crop'],
                measurements: { unlearned, active },
            }
        },
    }],
])
