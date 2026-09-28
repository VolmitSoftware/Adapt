function countItems(actor, name) {
    return actor.bot.inventory.slots.reduce((count, item) => count + (item?.name === name ? item.count : 0), 0)
}

async function prepareTable({ context, actor }, ingredients) {
    await context.command(`/execute at ${actor.bot.username} run setblock 2 100 2 minecraft:crafting_table`, /Changed the block/, 5000)
    for (const [name, count] of Object.entries(ingredients)) {
        await context.command(`/give ${actor.bot.username} minecraft:${name} ${count}`, /Gave /, 5000)
    }
    await context.waitUntil(() => Object.entries(ingredients).every(([name, count]) => countItems(actor, name) >= count), {
        label: 'raw crafting ingredients received', timeoutMs: 5000,
    })
}

async function tableBlock({ context, actor }) {
    const position = actor.bot.entity.position.clone().set(2, 100, 2)
    await context.waitUntil(() => actor.bot.blockAt(position)?.name === 'crafting_table', {
        label: 'crafting table fixture loaded', timeoutMs: 5000,
    })
    return actor.bot.blockAt(position)
}

async function craftGrid(input, specification) {
    const { context, actor } = input
    const before = countItems(actor, specification.output)
    await actor.bot.activateBlock(await tableBlock(input))
    await context.waitUntil(() => actor.bot.currentWindow?.type === 'minecraft:crafting', {
        label: 'crafting table window opened', timeoutMs: 5000,
    })
    const window = actor.bot.currentWindow
    try {
        for (let index = 0; index < specification.grid.length; index++) {
            const name = specification.grid[index]
            if (!name) continue
            const ingredient = window.slots.slice(window.inventoryStart).find(item => item?.name === name)
            context.expect(Boolean(ingredient), `${name} is available for crafting slot ${index + 1}`)
            const sourceSlot = ingredient.slot
            const hasRemainder = ingredient.count > 1
            await actor.bot.clickWindow(sourceSlot, 0, 0)
            await actor.bot.waitForTicks(2)
            await actor.bot.clickWindow(index + 1, 1, 0)
            await actor.bot.waitForTicks(2)
            if (hasRemainder) {
                await actor.bot.clickWindow(sourceSlot, 0, 0)
                await actor.bot.waitForTicks(2)
            }
        }
        await context.waitUntil(() => window.slots[0]?.name === specification.output, {
            label: `${specification.output} server recipe preview`, timeoutMs: 5000,
        })
        await actor.bot.clickWindow(0, 0, 1)
        await actor.bot.waitForTicks(8)
    } finally {
        actor.bot.closeWindow(window)
    }
    await actor.bot.waitForTicks(4)
    return {
        output: specification.output, gained: countItems(actor, specification.output) - before,
        remaining: Object.fromEntries(Object.keys(specification.ingredients).map(name => [name, countItems(actor, name)])),
    }
}

function recipeCase(specification) {
    return {
        stage: 'agility', negativeWindowTicks: 20,
        description: `crafting ${specification.output} from its registered ingredients`,
        ...specification.feedback,
        prepare: input => prepareTable(input, specification.ingredients),
        trigger: input => craftGrid(input, specification),
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.gained === 0, 'Unlearned player cannot take the custom recipe output')
            context.expect(active.gained === specification.count, 'Learned player receives the exact custom recipe output')
            for (const [name, count] of Object.entries(specification.ingredients)) {
                context.expect(unlearned.remaining[name] === count, 'Denied recipe preserves all raw ingredients')
                context.expect(active.remaining[name] === 0, 'Successful recipe consumes its exact raw ingredients')
            }
            return {
                assertions: ['server exposes the registered recipe preview', 'unlearned crafting is denied without ingredient loss', 'learned crafting consumes ingredients and returns the expected item count'],
                measurements: { unlearned, active },
            }
        },
    }
}

async function consumeApple({ actor, equip, snapshot }) {
    await equip('apple')
    const before = await snapshot()
    await actor.bot.consume()
    await actor.bot.waitForTicks(4)
    const after = await snapshot()
    return { before: { food: before.food, saturation: before.saturation }, after: { food: after.food, saturation: after.saturation } }
}

async function craftPlanks({ context, actor }) {
    const itemType = actor.bot.registry.itemsByName.oak_planks.id
    const recipe = actor.bot.recipesFor(itemType, null, 1, null)[0]
    context.expect(Boolean(recipe), 'Vanilla plank recipe is available')
    await actor.bot.craft(recipe, 1, null)
    await actor.bot.waitForTicks(4)
    const planks = actor.bot.inventory.items().find(item => item.name === 'oak_planks')
    context.expect(Boolean(planks), 'Crafted planks reached the player inventory')
    return { count: planks.count, metadata: JSON.stringify(planks.components ?? planks.nbt ?? null), owner: actor.bot.player.uuid }
}

async function signedPlanks(input) {
    const { context, actor, snapshot } = input
    const crafted = await craftPlanks(input)
    const villager = Object.values(actor.bot.entities).find(entity => entity.name === 'villager')
    context.expect(Boolean(villager), 'A real villager is available to inspect the naturally crafted goods')
    await actor.bot.lookAt(villager.position.offset(0, 1, 0), true)
    await actor.bot.waitForTicks(3)
    actor.bot.activateEntity(villager)
    await actor.bot.waitForTicks(6)
    if (actor.bot.currentWindow) actor.bot.closeWindow(actor.bot.currentWindow)
    const after = await snapshot()
    return { ...crafted, effects: after.effects, signedTrades: after.stats['crafting.signature.signed-trades'] ?? 0 }
}

async function compact(input) {
    const { actor } = input
    const block = await tableBlock(input)
    await actor.bot.lookAt(block.position.offset(0.5, 0.5, 0.5), true)
    actor.bot.setControlState('sneak', true)
    await actor.bot.waitForTicks(4)
    actor.bot._client.write('block_dig', { status: 6, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 0 })
    await actor.bot.waitForTicks(8)
    return { ingots: countItems(actor, 'iron_ingot'), blocks: countItems(actor, 'iron_block') }
}

async function appraiseRelic({ context, actor, equip, snapshot }) {
    await equip('archer_pottery_sherd')
    const before = await snapshot()
    actor.bot.setControlState('sneak', true)
    await actor.bot.waitForTicks(4)
    const position = actor.bot.entity.position.clone().set(1, 99, 1)
    await context.waitUntil(() => actor.bot.blockAt(position)?.name === 'stone', {
        label: 'relic appraisal interaction surface loaded', timeoutMs: 5000,
    })
    await actor.bot.activateBlock(actor.bot.blockAt(position))
    await actor.bot.waitForTicks(8)
    const relic = actor.bot.inventory.items().find(item => item.name === 'archer_pottery_sherd')
    context.expect(Boolean(relic), 'Appraisal preserves the relic item')
    const after = await snapshot()
    return {
        count: relic.count, metadata: JSON.stringify(relic.components ?? relic.nbt ?? null),
        appraised: (after.stats['discovery.relic-appraiser.appraised'] ?? 0) - (before.stats['discovery.relic-appraiser.appraised'] ?? 0),
    }
}

async function enchantPreview({ context, actor, snapshot }) {
    const messages = []
    const onActionbar = message => messages.push(message.toString())
    actor.bot.on('actionBar', onActionbar)
    const position = actor.bot.entity.position.clone().set(2, 100, 2)
    await context.waitUntil(() => actor.bot.blockAt(position)?.name === 'enchanting_table', {
        label: 'enchanting table fixture loaded', timeoutMs: 5000,
    })
    const before = await snapshot()
    const table = await actor.bot.openEnchantmentTable(actor.bot.blockAt(position))
    try {
        await table.putTargetItem(table.items().find(item => item.name === 'iron_sword'))
        await context.waitUntil(() => table.enchantments.some(offer => offer.level > 0), {
            label: 'server enchantment offers received', timeoutMs: 5000,
        })
        await actor.bot.waitForTicks(8)
        const after = await snapshot()
        return {
            offers: table.enchantments.map(offer => ({ cost: offer.level, expected: { ...offer.expected } })),
            reveals: messages.filter(message => /[a-z ]+ \d+ \(\d+\)/.test(message)),
            revealedStat: (after.stats['enchanting.rune-sight.offers-revealed'] ?? 0) - (before.stats['enchanting.rune-sight.offers-revealed'] ?? 0),
        }
    } finally {
        table.close()
        actor.bot.off('actionBar', onActionbar)
    }
}

export const itemBehaviorCases = new Map([
    ['herbalism-cobweb', recipeCase({
        output: 'cobweb', count: 1, ingredients: { string: 9 }, grid: Array(9).fill('string'),
        feedback: { sound: 'minecraft:block.wool.break', soundVolume: 0.6, soundPitch: 1.2, particle: 'end_rod' },
    })],
    ['herbalism-myconid', recipeCase({
        output: 'mycelium', count: 1, ingredients: { dirt: 1, red_mushroom: 1, brown_mushroom: 1 },
        grid: ['dirt', 'red_mushroom', 'brown_mushroom'],
        feedback: { sound: 'minecraft:block.fungus.place', soundVolume: 0.6, soundPitch: 0.8, particle: 'crimson_spore' },
    })],
    ['herbalism-terralid', recipeCase({
        output: 'grass_block', count: 3, ingredients: { wheat_seeds: 3, dirt: 3 },
        grid: ['wheat_seeds', 'wheat_seeds', 'wheat_seeds', 'dirt', 'dirt', 'dirt'],
        feedback: { sound: 'minecraft:block.grass.break', soundVolume: 0.6, soundPitch: 1.0, particle: 'happy_villager', particleCount: 6 },
    })],
    ['herbalism-mushroom-blocks', recipeCase({
        output: 'red_mushroom_block', count: 1, ingredients: { red_mushroom: 4 },
        grid: ['red_mushroom', 'red_mushroom', null, 'red_mushroom', 'red_mushroom'],
        feedback: { sound: 'minecraft:block.fungus.place', soundVolume: 0.6, soundPitch: 0.9, particle: 'spore_blossom_air' },
    })],
    ['crafting-skulls', recipeCase({
        output: 'skeleton_skull', count: 1, ingredients: { bone: 8, bone_block: 1 },
        grid: ['bone', 'bone', 'bone', 'bone', 'bone_block', 'bone', 'bone', 'bone', 'bone'],
        feedback: { sound: 'minecraft:particle.soul_escape', soundVolume: 0.7, soundPitch: 0.6, particle: 'soul' },
    })],
    ['crafting-reconstruction', recipeCase({
        output: 'iron_ore', count: 1, ingredients: { stone: 1, iron_ingot: 8 },
        grid: ['stone', ...Array(8).fill('iron_ingot')],
        feedback: { sound: 'minecraft:block.stone.place', soundVolume: 0.8, soundPitch: 0.6, particle: 'crit' },
    })],
    ['herbalism-hippo', {
        stage: 'herbalism', negativeWindowTicks: 20,
        description: 'eating the same apple supplies extra hunger and saturation',
        sound: 'minecraft:block.pointed_dripstone.land', soundVolume: 1, soundPitch: 0.25, particle: 'composter',
        trigger: consumeApple,
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.before.food === active.before.food && unlearned.before.saturation === active.before.saturation, 'Apple trials start at equal hunger and saturation')
            context.expect(active.after.food > unlearned.after.food, 'Learned apple consumption restores more hunger')
            context.expect(active.after.saturation > unlearned.after.saturation, 'Learned apple consumption restores more saturation')
            return { assertions: ['identical food starts with equal hunger and saturation', 'learned consumption restores more hunger and saturation'], measurements: { unlearned, active } }
        },
    }],
    ['crafting-signature', {
        stage: 'crafting', negativeWindowTicks: 20,
        description: 'crafting adds the crafter identity to the returned item',
        sound: 'minecraft:entity.villager.yes', soundVolume: 0.6, soundPitch: 1.1,
        particle: 'happy_villager', particleCount: 6, particleOffset: 0.3,
        prepare: async ({ context, actor }) => {
            await context.command(`/execute at ${actor.bot.username} run summon minecraft:villager 2.5 100 2.5 {NoAI:1b,VillagerData:{type:"minecraft:plains",profession:"minecraft:farmer",level:2}}`, /Summoned/i, 5000)
            await context.waitUntil(() => Object.values(actor.bot.entities).some(entity => entity.name === 'villager'), {
                label: 'signature trading villager is client visible', timeoutMs: 3000,
            })
        },
        trigger: signedPlanks,
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.count === 4 && active.count === 4, 'Both crafting trials return four planks')
            context.expect(!unlearned.metadata.includes('adapt:crafting_signature_owner'), 'Unlearned crafted item has no signature')
            context.expect(active.metadata.includes('adapt:crafting_signature_owner') && active.metadata.includes(active.owner), 'Learned crafted item stores the actual crafter UUID')
            context.expect(!unlearned.effects.some(effect => effect.type === 'minecraft:hero_of_the_village'), 'Unsigned goods grant no village trading effect')
            context.expect(active.effects.some(effect => effect.type === 'minecraft:hero_of_the_village') && active.signedTrades > unlearned.signedTrades, 'Villager interaction recognizes naturally signed goods and grants its trade benefit')
            return { assertions: ['unlearned output has no crafter signature', 'learned output includes the actual crafter UUID', 'real villager interaction grants the signed-goods trade effect'], measurements: { unlearned, active } }
        },
    }],
    ['crafting-compactor', {
        stage: 'agility', negativeWindowTicks: 45,
        description: 'sneak and swap hands facing a crafting table compacts a full ingredient stack',
        sound: 'minecraft:block.stone.place', soundVolume: 0.4, soundPitch: 1.2, particle: 'crit',
        prepare: input => prepareTable(input, { iron_ingot: 64 }), trigger: compact,
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.ingots === 64 && unlearned.blocks === 0, 'Unlearned swap preserves the ingredient stack')
            context.expect(active.ingots === 1 && active.blocks === 7, 'Learned compaction conserves 64 ingots as seven blocks and one ingot')
            return { assertions: ['unlearned swap performs no compaction', 'learned swap compacts seven blocks and preserves the remainder'], measurements: { unlearned, active } }
        },
    }],
    ['enchanting-bookshelf-attunement', {
        stage: 'enchanting', negativeWindowTicks: 25,
        description: 'enchanting-table previews gain six virtual bookshelf power at maximum level',
        sound: 'minecraft:block.amethyst_block.chime', soundVolume: 0.3, soundPitch: 1.5,
        particle: 'enchant', particleCount: 6, particleOffset: 0.4,
        trigger: enchantPreview,
        async verify({ context, unlearned, active }) {
            const existing = unlearned.offers.map((offer, index) => ({ offer, index })).filter(({ offer }) => offer.cost > 0)
            context.expect(existing.length > 0, 'Control trial has real enchantment offers')
            for (const { offer, index } of existing) {
                context.expect(active.offers[index].cost === Math.min(30, offer.cost + 6), 'Learned offer cost includes the configured six-point virtual power')
            }
            return { assertions: ['unlearned table supplies vanilla offers', 'learned preview raises each offer by six virtual bookshelf power'], measurements: { unlearned, active } }
        },
    }],
    ['enchanting-rune-sight', {
        stage: 'enchanting', negativeWindowTicks: 20,
        description: 'enchanting-table preview reveals enchantment names, levels, and costs in the actionbar',
        trigger: enchantPreview,
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.reveals.length === 0 && unlearned.revealedStat === 0, 'Unlearned preview reveals no rune information')
            context.expect(active.reveals.length > 0 && active.revealedStat > 0, 'Learned preview sends actual rune information and records revealed offers')
            return { assertions: ['unlearned preview has no rune reveal', 'learned preview sends enchantment names, levels, and costs through the actionbar'], measurements: { unlearned, active } }
        },
    }],
    ['discovery-relic-appraiser', {
        stage: 'agility', negativeWindowTicks: 20,
        description: 'sneak-right-clicking a pottery sherd marks that real item as appraised',
        sound: 'minecraft:block.amethyst_block.chime', soundVolume: 0.6, soundPitch: 1.4, particle: 'wax_on',
        async prepare({ context, actor }) {
            await context.command(`/give ${actor.bot.username} minecraft:archer_pottery_sherd 1`, /Gave /, 5000)
        },
        trigger: appraiseRelic,
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.count === 1 && active.count === 1, 'Both trials preserve the pottery sherd')
            context.expect(!unlearned.metadata.includes('adapt:relic_appraised') && unlearned.appraised === 0, 'Unlearned interaction does not appraise the item')
            context.expect(active.metadata.includes('adapt:relic_appraised') && active.appraised === 1, 'Learned interaction marks the real item and records one appraisal')
            return { assertions: ['unlearned interaction leaves the relic unappraised', 'learned interaction marks the real relic item and records exactly one appraisal'], measurements: { unlearned, active } }
        },
    }],
])

itemBehaviorCases.set('blocking-chainarmorer', recipeCase({
    output: 'chainmail_boots', count: 1, ingredients: { iron_nugget: 4 },
    grid: ['iron_nugget', null, 'iron_nugget', 'iron_nugget', null, 'iron_nugget'],
    feedback: { sound: 'minecraft:item.armor.equip_chain', soundVolume: 0.6, soundPitch: 1.0, particle: 'wax_on' },
}))
itemBehaviorCases.set('blocking-horsearmorer', recipeCase({
    output: 'iron_horse_armor', count: 1, ingredients: { iron_ingot: 8, saddle: 1 },
    grid: ['iron_ingot', 'iron_ingot', 'iron_ingot', 'iron_ingot', 'saddle', 'iron_ingot', 'iron_ingot', 'iron_ingot', 'iron_ingot'],
    feedback: { sound: 'minecraft:entity.horse.saddle', soundVolume: 0.6, soundPitch: 1.0, particle: 'wax_on' },
}))
itemBehaviorCases.set('blocking-saddlecrafter', recipeCase({
    output: 'saddle', count: 1, ingredients: { leather: 5 },
    grid: ['leather', null, 'leather', 'leather', 'leather', 'leather'],
    feedback: { sound: 'minecraft:entity.horse.saddle', soundVolume: 0.7, soundPitch: 1.0, particle: 'wax_on' },
}))
itemBehaviorCases.set('blocking-phalanx-crafter', recipeCase({
    output: 'shield', count: 1, ingredients: { white_wool: 3, oak_planks: 3, iron_ingot: 1 },
    grid: ['white_wool', 'white_wool', 'white_wool', 'oak_planks', 'iron_ingot', 'oak_planks', null, 'oak_planks'],
    feedback: { sound: 'minecraft:block.anvil.use', soundVolume: 0.4, soundPitch: 1.2, particle: 'wax_on' },
}))
itemBehaviorCases.set('crafting-xp', {
    stage: 'crafting', negativeWindowTicks: 10,
    sound: 'minecraft:block.amethyst_block.chime', soundVolume: 0.3, soundPitch: 1.3, particle: 'glow',
    async trigger(input) {
        const before = input.actor.bot.experience.points
        const crafted = await craftPlanks(input)
        await input.actor.bot.waitForTicks(35)
        return { crafted: crafted.count, xp: input.actor.bot.experience.points - before }
    },
    async verify({ context, unlearned, active }) {
        context.expect(unlearned.crafted === 4 && active.crafted === 4, 'Both trials craft four actual planks')
        context.expect(unlearned.xp === 0 && active.xp === 7, 'Only the learned craft grants the configured seven vanilla XP points')
        return { assertions: ['both trials craft four planks', 'learned crafting grants and collects exactly seven vanilla XP points'], measurements: { unlearned, active } }
    },
})

itemBehaviorCases.set('rift-enderchest', {
    stage: 'agility', negativeWindowTicks: 10,
    sound: 'minecraft:block.ender_chest.open', soundVolume: 1, soundPitch: 1, particle: 'reverse_portal',
    async prepare({ context, actor, equip }) {
        await context.command(`/give ${actor.bot.username} minecraft:ender_chest 1`, /Gave /, 5000)
        await equip('ender_chest')
    },
    async trigger({ actor, snapshot }) {
        const before = await snapshot()
        await actor.bot.look(0, Math.PI / 3, true)
        await actor.bot.waitForTicks(3)
        actor.bot.swingArm('right')
        await actor.bot.waitForTicks(8)
        const after = await snapshot()
        const result = { window: actor.bot.currentWindow?.type ?? null, opened: (after.stats['rift.enderchest.opens'] ?? 0) - (before.stats['rift.enderchest.opens'] ?? 0) }
        if (actor.bot.currentWindow) actor.bot.closeWindow(actor.bot.currentWindow)
        return result
    },
    async verify({ context, unlearned, active }) {
        context.expect(unlearned.window === null && unlearned.opened === 0, 'Unlearned item use opens no inventory')
        context.expect(active.window === 'minecraft:generic_9x3' && active.opened === 1, 'Learned item use opens the actual ender-chest inventory once')
        return { assertions: ['unlearned item use opens no inventory', 'learned item use opens a 27-slot ender chest and records one open'], measurements: { unlearned, active } }
    },
})
itemBehaviorCases.set('rift-resist', {
    stage: 'rift', negativeWindowTicks: 12,
    effect: 'minecraft:resistance', sound: 'minecraft:item.armor.equip_iron', soundVolume: 1, soundPitch: 1.24, particle: 'witch',
    async trigger({ actor, equip }) {
        await equip('ender_pearl')
        await actor.bot.look(0, Math.PI / 3, true)
        actor.bot.activateItem()
        await actor.bot.waitForTicks(3)
        actor.bot.deactivateItem()
    },
})
