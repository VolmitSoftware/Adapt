import { decodeSoundPacket } from './feedback.mjs'

function position(actor, x, y, z) {
    return actor.bot.entity.position.clone().set(x, y, z)
}

function itemCount(actor, name) {
    return actor.bot.inventory.slots.reduce((sum, item) => sum + (item?.name === name ? item.count : 0), 0)
}

function preparation(tool, sneak = false) {
    return async ({ actor, equip }) => {
        if (tool) await equip(tool)
        actor.bot.setControlState('sneak', sneak)
        await actor.bot.waitForTicks(4)
    }
}

async function targetBlock(input, x = 3, y = 100, z = 0) {
    await input.context.waitUntil(() => Boolean(input.actor.bot.blockAt(position(input.actor, x, y, z))), {
        label: 'harvest block fixture loaded', timeoutMs: 5000,
    })
    return input.actor.bot.blockAt(position(input.actor, x, y, z))
}

async function breakBlock(input, x = 3, y = 100, z = 0) {
    const block = await targetBlock(input, x, y, z)
    let finished = false
    let failure
    const pending = input.actor.bot.dig(block).then(() => { finished = true }, error => { failure = error; finished = true })
    try {
        await input.context.waitUntil(() => {
            if (failure) throw failure
            return finished
        }, { label: 'natural survival harvest dig', timeoutMs: 15000, intervalMs: 20 })
        await pending
    } finally {
        if (!finished) input.actor.bot.stopDigging()
    }
    await input.actor.bot.waitForTicks(3)
}

async function beginMining(input) {
    const block = await targetBlock(input)
    let failure
    const digging = input.actor.bot.dig(block).catch(error => { failure = error })
    await input.actor.bot.waitForTicks(3)
    const after = await input.snapshot()
    input.actor.bot.stopDigging()
    await digging
    if (failure && !/cancel|abort|stop/i.test(failure.message)) throw failure
    return after
}

async function breakAndSnapshot(input) {
    const before = await input.snapshot()
    await breakBlock(input)
    await input.actor.bot.waitForTicks(25)
    return { before, after: await input.snapshot() }
}

async function fragilePickaxe(input) {
    const before = await input.snapshot()
    let broken = 0
    for (let attempt = 0; attempt < 10; attempt++) {
        if (attempt > 0) {
            await input.context.command(`/execute at ${input.actor.bot.username} run setblock 3 100 0 minecraft:stone replace`, /Changed the block|block.*changed/i, 5000)
            if (!(await input.snapshot()).knowledge.inventory.some(item => item.type === 'minecraft:diamond_pickaxe')) {
                await input.context.command(`/give ${input.actor.bot.username} minecraft:diamond_pickaxe[minecraft:damage=1560] 1`, /Gave /, 5000)
                await input.equip('diamond_pickaxe')
            }
            await input.context.waitUntil(async () => (await targetBlock(input)).name === 'stone', {
                label: 'replacement stone input reaches client', timeoutMs: 3000,
            })
        }
        const ready = await input.snapshot()
        input.context.expect(ready.harvest.heldType === 'DIAMOND_PICKAXE' && ready.harvest.heldDamage === 1560,
            'Every durability trial begins with a real pickaxe at one remaining durability')
        await breakBlock(input)
        if (!(await input.snapshot()).knowledge.inventory.some(item => item.type === 'minecraft:diamond_pickaxe')) broken++
    }
    return { before, after: await input.snapshot(), broken, trials: 10 }
}

async function fourStones(input) {
    for (const [x, y, z] of [[3, 100, 0], [3, 100, 1], [3, 101, 0], [3, 101, 1]]) {
        await breakBlock(input, x, y, z)
    }
    return await input.snapshot()
}

async function useAir(input) {
    const before = await input.snapshot()
    await input.actor.bot.lookAt(input.actor.bot.entity.position.offset(0, 3, -10), true)
    input.actor.bot.activateItem()
    input.actor.bot.deactivateItem()
    await input.actor.bot.waitForTicks(20)
    return { before, after: await input.snapshot() }
}

async function fall(input) {
    const { actor, context, snapshot } = input
    const sounds = []
    const onSound = packet => sounds.push(decodeSoundPacket(actor.bot.registry, packet))
    actor.bot._client.on('sound_effect', onSound)
    try {
        const before = await snapshot()
        await actor.bot.lookAt(position(actor, 0.5, 109.6, 4.5), true)
        actor.bot.setControlState('forward', true)
        try {
            await context.waitUntil(() => actor.bot.entity.position.z > 1.6, { label: 'walk off raised platform', timeoutMs: 4000, intervalMs: 20 })
        } finally {
            actor.bot.setControlState('forward', false)
        }
        await context.waitUntil(() => actor.bot.entity.onGround && actor.bot.entity.position.y < 101, {
            label: 'natural fall lands on dirt', timeoutMs: 5000, intervalMs: 20,
        })
        await actor.bot.waitForTicks(5)
        const after = await snapshot()
        return { before, after, damage: before.health - after.health, sounds }
    } finally {
        actor.bot._client.off('sound_effect', onSound)
        actor.bot.clearControlStates()
    }
}

async function shield(input) {
    const { context, actor, opponent, snapshot } = input
    const sword = opponent.bot.inventory.items().find(item => item.name === 'wooden_sword')
    context.expect(Boolean(sword), 'Opponent has a real sword')
    await opponent.bot.equip(sword, 'hand')
    await opponent.bot.waitForTicks(25)
    const victim = opponent.bot.entities[actor.bot.entity.id]
    context.expect(Boolean(victim), 'Owner is visible to opponent')
    await opponent.bot.lookAt(victim.position.offset(0, 1, 0), true)
    const before = await snapshot()
    opponent.bot.attack(victim)
    await actor.bot.waitForTicks(10)
    const after = await snapshot()
    return { before, after, damage: before.health - after.health }
}

async function sow(input) {
    const before = await input.snapshot()
    const seeds = itemCount(input.actor, 'wheat_seeds')
    const block = await targetBlock(input, 3, 99, 0)
    await input.actor.bot.activateBlock(block, position(input.actor, 0, 1, 0))
    await input.actor.bot.waitForTicks(15)
    return { before, after: await input.snapshot(), seedsUsed: seeds - itemCount(input.actor, 'wheat_seeds') }
}

async function spores(input) {
    const before = await input.snapshot()
    const mushrooms = itemCount(input.actor, 'red_mushroom')
    const block = await targetBlock(input, 3, 99, 0)
    input.context.expect(block.name === 'mycelium', 'Mushroom placement has valid mycelium soil')
    await input.actor.bot.activateBlock(block, position(input.actor, 0, 1, 0))
    await input.actor.bot.waitForTicks(100)
    return { before, after: await input.snapshot(), mushroomsUsed: mushrooms - itemCount(input.actor, 'red_mushroom'),
        placed: input.actor.bot.blockAt(position(input.actor, 3, 100, 0))?.name }
}

async function compost(input) {
    const before = await input.snapshot()
    const block = await targetBlock(input)
    await input.actor.bot.activateBlock(block)
    await input.actor.bot.waitForTicks(30)
    return { before, after: await input.snapshot() }
}

async function grow(input) {
    const before = await input.snapshot()
    await input.actor.bot.waitForTicks(180)
    return { before, after: await input.snapshot() }
}

function report(assertions, unlearned, active) {
    return { assertions, measurements: { unlearned, active } }
}

function speedCase(stage, tool, feedback) {
    return {
        stage, prepare: preparation(tool), trigger: beginMining, negativeWindowTicks: 20,
        description: 'natural block damage raises the actual mining-speed attribute', ...feedback,
        async verify({ context, unlearned, active }) {
            context.expect(active.attributes['minecraft:block_break_speed'] > unlearned.attributes['minecraft:block_break_speed'], 'Learned block-damage trigger raises actual mining speed')
            return report(['ordinary mining provides unlearned speed control', 'the same natural block-damage event raises learned mining speed'], unlearned, active)
        },
    }
}

export const harvestBehaviorCases = new Map([
    ['pickaxe-deep-core', speedCase('harvest-deep', 'diamond_pickaxe', {
        sound: 'minecraft:block.amethyst_block.chime', soundVolume: 0.4, soundPitch: 1.4, particle: 'electric_spark',
    })],
    ['pickaxe-obsidian-rush', speedCase('harvest-obsidian', 'diamond_pickaxe', {
        sound: 'minecraft:block.respawn_anchor.charge', soundVolume: 0.5, soundPitch: 0.7, particle: 'dust',
    })],
    ['excavation-mudlark', speedCase('harvest-wet', 'diamond_shovel', {
        sound: 'minecraft:entity.player.splash', soundVolume: 0.3, soundPitch: 1.4,
        particle: 'splash', particleCount: 5, particleOffset: 0.3,
    })],
    ['pickaxe-stone-skin', {
        stage: 'harvest-stone', prepare: preparation('diamond_pickaxe'), trigger: fourStones, negativeWindowTicks: 20,
        description: 'four natural stone breaks build a resistance tier',
        sound: 'minecraft:block.stone.place', soundVolume: 0.5, soundPitch: 0.8, particle: 'cloud',
        async verify({ context, unlearned, active }) {
            context.expect(!unlearned.effects.some(effect => effect.type === 'minecraft:resistance'), 'Unlearned stone mining gives no resistance')
            context.expect(active.effects.some(effect => effect.type === 'minecraft:resistance' && effect.amplifier === 0), 'Four learned stone breaks grant resistance one')
            context.expect(Object.values(active.harvest.blocks).every(type => type === 'AIR'), 'All four stones were actually mined')
            return report(['unlearned four-block mining gives no resistance', 'four learned natural breaks grant resistance one'], unlearned, active)
        },
    }],
    ['pickaxe-unbreakable-pact', {
        stage: 'harvest-fragile', prepare: preparation('diamond_pickaxe'), trigger: fragilePickaxe, negativeWindowTicks: 20,
        description: 'a pickaxe at one durability survives actual survival mining',
        sound: 'minecraft:block.anvil.place', soundVolume: 0.4, soundPitch: 1.8,
        particle: 'crit', particleCount: 10, particleOffset: 0.3,
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.before.harvest.heldType === 'DIAMOND_PICKAXE'
                && !unlearned.after.knowledge.inventory.some(item => item.type === 'minecraft:diamond_pickaxe'), 'Unlearned one-durability pickaxe breaks naturally')
            context.expect(unlearned.broken === 10 && active.broken === 0, 'Ten actual mining trials break all unlearned tools and preserve the same learned tool')
            context.expect(active.after.harvest.heldType === 'DIAMOND_PICKAXE' && active.after.harvest.heldDamage === active.before.harvest.heldDamage, 'Learned pact preserves actual one-durability pickaxe')
            return report(['ten unlearned mining trials destroy ten nearly broken pickaxes', 'ten learned mining trials preserve the same remaining durability; probability of all trials skipping last-durability feedback is at most 0.25^10'], unlearned, active)
        },
    }],
    ['pickaxe-silk-spawner', {
        stage: 'harvest-spawner', prepare: preparation('diamond_pickaxe', true), trigger: breakAndSnapshot, negativeWindowTicks: 20,
        description: 'sneaking survival mining recovers an actual spawner item',
        sound: 'minecraft:entity.item.pickup', soundVolume: 0.6, soundPitch: 0.7, particle: 'portal',
        async verify({ context, unlearned, active }) {
            context.expect((unlearned.after.harvest.drops.SPAWNER ?? 0) === 0, 'Unlearned spawner break yields no spawner item')
            context.expect(active.after.harvest.drops.SPAWNER === 1, 'Learned spawner break yields exactly one physical spawner item')
            return report(['unlearned mining yields no spawner', 'learned sneak mining drops one actual spawner'], unlearned, active)
        },
    }],
    ['excavation-earth-mover', {
        stage: 'harvest-earth', prepare: preparation('diamond_shovel', true), trigger: useAir, negativeWindowTicks: 20,
        description: 'sneak shovel activation damages and slows a nearby hostile mob',
        sound: 'minecraft:block.rooted_dirt.break', soundVolume: 1, soundPitch: 0.6, particle: 'cloud',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.after.harvest.target.health === unlearned.before.harvest.target.health, 'Unlearned shovel use cannot remotely damage zombie')
            context.expect(active.after.harvest.target.health < active.before.harvest.target.health, 'Learned shovel wave removes actual hostile health')
            context.expect(active.after.harvest.target.effects['minecraft:slowness'] >= 0, 'Learned hostile target also receives slowness')
            context.expect(active.after.food < active.before.food, 'Earth mover charges its hunger cost')
            return report(['unlearned remote gesture leaves hostile unchanged', 'learned wave damages and slows hostile while charging hunger'], unlearned, active)
        },
    }],
    ['excavation-soft-fall', {
        stage: 'harvest-fall', trigger: fall, negativeWindowTicks: 20,
        description: 'a real fall onto dirt loses no health at maximum adaptation level',
        sound: 'minecraft:block.sand.break', soundVolume: 0.4, soundPitch: 0.8, particle: 'cloud',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.damage > 3, 'Unlearned control fall removes health')
            context.expect(active.damage === 0, 'Learned soft-ground fall preserves all health')
            const prevented = active.after.harvest.fall.before.damage - active.after.harvest.fall.after.damage
            const notes = [
                ['block.rooted_dirt.break', 0.6, Math.max(0.5, 0.9 - prevented * 0.02)],
                ['block.sand.break', 0.4, 0.8],
                ['block.wool.fall', 0.5, 1.2],
            ]
            for (const [name, volume, pitch] of notes) {
                const matches = packet => packet.name === name && Math.abs(packet.volume - volume) < 0.00001 && Math.abs(packet.pitch - pitch) < 0.00001
                context.expect(!unlearned.sounds.some(matches), `Unlearned fall emits no authored ${name} note`)
                context.expect(active.sounds.some(matches), `Learned fall emits authored ${name} at its exact volume and pitch`)
            }
            return report(['real unlearned platform fall removes health', 'the same learned fall onto dirt causes no health loss',
                'all three authored landing notes reach the player with exact volume and damage-dependent pitch'], unlearned, active)
        },
    }],
    ['herbalism-rooted-footing', {
        stage: 'harvest-fall', trigger: fall, negativeWindowTicks: 20,
        description: 'a real fall onto dirt trades food for reduced health loss',
        sound: 'minecraft:block.grass.break', soundVolume: 0.6, soundPitch: 0.8, particle: 'dust',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.damage > 3 && active.damage < unlearned.damage, 'Learned rooted landing loses less actual health')
            context.expect(active.after.food < active.before.food, 'Rooted landing pays its food cost')
            return report(['unlearned dirt landing establishes fall damage', 'learned landing reduces actual health loss by consuming food'], unlearned, active)
        },
    }],
    ['herbalism-hungry-shield', {
        stage: 'harvest-shield', trigger: shield, negativeWindowTicks: 20,
        description: 'a real opponent sword hit trades food for reduced damage',
        sound: 'minecraft:item.shield.block', soundVolume: 0.5, soundPitch: 0.9, particle: 'dust',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.damage > 0 && active.damage < unlearned.damage, 'Learned hunger shield reduces real sword damage')
            context.expect(active.after.food + active.after.saturation < active.before.food + active.before.saturation, 'Shield consumes actual food reserve')
            return report(['unlearned sword hit establishes actual damage', 'learned sword hit spends food and reduces health loss'], unlearned, active)
        },
    }],
    ['herbalism-seed-sower', {
        stage: 'harvest-seeds', prepare: preparation('wheat_seeds', true), trigger: sow, negativeWindowTicks: 20,
        description: 'one sneak seed use plants multiple farmland cells and pays for every crop',
        sound: 'minecraft:item.crop.plant', soundVolume: 0.6, soundPitch: 1.25, particle: 'dust',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.after.harvest.crops <= 1, 'Unlearned seed use plants at most one vanilla crop')
            context.expect(active.after.harvest.crops > 1, 'Learned single seed gesture plants several crops')
            context.expect(active.seedsUsed === active.after.harvest.crops - active.before.harvest.crops, 'Every learned crop consumes one actual seed')
            return report(['one unlearned seed gesture plants at most one crop', 'one learned gesture plants multiple crops with exact seed conservation'], unlearned, active)
        },
    }],
    ['herbalism-spore-bloom', {
        stage: 'harvest-spores', prepare: preparation('red_mushroom', true), trigger: spores, negativeWindowTicks: 20,
        description: 'a naturally placed mushroom spreads mycelium onto surrounding dirt',
        sound: 'minecraft:block.fungus.place', soundVolume: 0.45, soundPitch: 0.75, particle: 'spore_blossom_air',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.after.harvest.mycelium === 1 && unlearned.mushroomsUsed === 1, 'Unlearned placement only consumes its placed mushroom')
            context.expect(unlearned.placed === 'red_mushroom' && active.placed === 'air', 'Learned bloom replaces ordinary mushroom placement with its soil-spread action')
            context.expect(active.after.harvest.mycelium > 1 && active.mushroomsUsed > 1, 'Learned bloom converts real soil and consumes extra mushroom catalysts')
            context.expect(active.after.food < active.before.food, 'Bloom pays actual food cost')
            return report(['unlearned mushroom placement leaves surrounding dirt unchanged', 'learned placement converts soil and consumes mushrooms plus food'], unlearned, active)
        },
    }],
    ['herbalism-compost-cascade', {
        stage: 'harvest-compost', prepare: preparation(undefined, true), trigger: compost, negativeWindowTicks: 20,
        description: 'an empty-hand sneak composter use consumes nearby seeds and creates compost',
        sound: 'minecraft:block.composter.fill', soundVolume: 0.8, soundPitch: 1.25, particle: 'spore_blossom_air',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.after.harvest.drops.WHEAT_SEEDS === 64 && unlearned.after.harvest.compostLevel === 0, 'Unlearned empty-hand use leaves dropped seeds and empty composter unchanged')
            context.expect((active.after.harvest.drops.WHEAT_SEEDS ?? 0) < 64, 'Learned cascade consumes real nearby seed items')
            context.expect(active.after.harvest.compostLevel > 0 || (active.after.harvest.drops.BONE_MEAL ?? 0) > 0, 'Consumed seeds produce actual compost or bone meal')
            return report(['unlearned empty-hand use consumes no seeds', 'learned use consumes dropped seeds and creates compost output'], unlearned, active)
        },
    }],
    ['herbalism-growth-aura', {
        stage: 'harvest-growth', trigger: grow, negativeWindowTicks: 20,
        description: 'normal aura ticks advance real crop ages with vanilla random ticking disabled',
        sound: 'minecraft:item.crop.plant', soundVolume: 0.25, soundPitch: 1.5,
        particle: 'happy_villager',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.after.harvest.cropAgeTotal === 0, 'Control crops do not grow with random ticking disabled')
            context.expect(active.after.harvest.cropAgeTotal > 0, 'Learned aura advances actual wheat block ages')
            return report(['unlearned wheat remains age zero without vanilla random growth', 'learned aura grows actual wheat blocks'], unlearned, active)
        },
    }],
    ['herbalism-bee-shepherd', {
        stage: 'harvest-bees', prepare: preparation('dandelion'), trigger: grow, negativeWindowTicks: 20,
        description: 'holding a flower near a bee grows real crops through shepherd pulses',
        sound: 'minecraft:entity.bee.pollinate', soundVolume: 0.85, soundPitch: 1.25, particle: 'happy_villager',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.after.harvest.cropAgeTotal === 0, 'Unlearned flower holding does not grow controlled crops')
            context.expect(active.after.harvest.cropAgeTotal > 0, 'Learned flower holding advances actual crop ages')
            context.expect(active.after.food < active.before.food, 'Successful shepherd growth charges food')
            return report(['unlearned flower holding gives no crop growth', 'learned flower holding advances crop ages while charging food'], unlearned, active)
        },
    }],
])
