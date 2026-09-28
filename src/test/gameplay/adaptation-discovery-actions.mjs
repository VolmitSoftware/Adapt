function result(assertions, unlearned, active) {
    return { assertions, measurements: { unlearned, active } }
}

function count(state, type) {
    return state.knowledge.inventory.reduce((sum, item) => sum + (item.type === `minecraft:${type}` ? item.amount : 0), 0)
}

function enchanted(item) {
    return (item.componentMap?.get('enchantments')?.data?.enchantments?.length ?? 0) > 0
}

function books(state) {
    return [...state.knowledge.inventory, ...state.knowledge.drops].filter(item => item.type === 'minecraft:enchanted_book')
}

async function prepare(input, tool) {
    input.actor.bot.clearControlStates()
    input.opponent.bot.clearControlStates()
    if (tool) await input.equip(tool)
    await input.actor.bot.waitForTicks(14)
}

function block(input, x = 2, z = 0) {
    return input.actor.bot.blockAt(input.actor.bot.entity.position.clone().set(x, 100, z))
}

async function brush(input) {
    const bonus = new Set(['diamond', 'emerald', 'gold_ingot', 'amethyst_shard', 'brick', 'clay_ball', 'bone', 'flint', 'string', 'coal'])
    const completed = []
    for (let attempt = 0; attempt < 12; attempt++) {
        if (attempt > 0) {
            await input.context.command(`/execute at ${input.actor.bot.username} run setblock 2 100 0 minecraft:suspicious_sand{item:{id:"minecraft:paper",count:1}} replace`, /Changed the block|block.*changed/i, 5000)
            await input.context.waitUntil(() => block(input)?.name === 'suspicious_sand', {
                label: 'fresh archaeological input block is client visible', timeoutMs: 3000,
            })
        }
        const target = block(input)
        input.context.expect(target?.name === 'suspicious_sand', 'Each brush trial starts with actual suspicious sand')
        await input.equip('brush')
        await input.actor.bot.lookAt(target.position.offset(0.5, 0.5, 0.5), true)
        await input.actor.bot.waitForTicks(3)
        await input.actor.bot.activateBlock(target)
        try {
            await input.context.waitUntil(() => input.actor.bot.blockAt(target.position)?.name === 'sand', {
                label: `natural brushing completes trial ${attempt + 1} at the nearby input block`, timeoutMs: 9000,
            })
        } finally { input.actor.bot.deactivateItem() }
        await input.actor.bot.waitForTicks(60)
        const after = await input.snapshot()
        const paper = [...after.knowledge.inventory, ...after.knowledge.drops]
            .filter(item => item.type === 'minecraft:paper').reduce((sum, item) => sum + item.amount, 0)
        input.context.expect(paper === attempt + 1, 'Every completed natural brush releases its one ordinary paper loot item')
        completed.push({ type: input.actor.bot.blockAt(target.position)?.name, paper })
        const rewards = after.knowledge.inventory.filter(item => bonus.has(item.type.replace('minecraft:', '')))
        if (rewards.length > 0) return { attempts: attempt + 1, rewards, completed }
    }
    return { attempts: completed.length, rewards: [], completed }
}

async function siphon(input) {
    let kills = 0
    const initial = await input.snapshot()
    for (const target of initial.discovery.targets) {
        const entity = input.actor.bot.entities[target.entityId]
        input.context.expect(Boolean(entity), 'Enchanted-helmet source mob is visible')
        await input.actor.bot.lookAt(entity.position.offset(0, 1, 0), true)
        input.actor.bot.attack(entity)
        await input.actor.bot.waitForTicks(26)
        const after = await input.snapshot()
        input.context.expect(after.discovery.targets.find(value => value.entityId === target.entityId).dead, 'Natural sword strike kills the enchanted source mob')
        kills++
        const dropped = books(after)
        if (dropped.length > 0) return { kills, books: dropped }
    }
    return { kills, books: [] }
}

async function grind(input) {
    await input.actor.bot.activateBlock(block(input))
    await input.context.waitUntil(() => input.actor.bot.currentWindow?.type === 'minecraft:grindstone', {
        label: 'grindstone opens', timeoutMs: 5000,
    })
    const window = input.actor.bot.currentWindow
    let attempts = 0
    try {
        for (; attempts < 30;) {
            const source = window.slots.slice(window.inventoryStart).find(item => item?.name === 'iron_sword' && enchanted(item))
            input.context.expect(Boolean(source), 'A fresh enchanted source sword is available')
            await input.actor.bot.clickWindow(source.slot, 0, 0)
            await input.actor.bot.clickWindow(0, 0, 0)
            await input.context.waitUntil(() => window.slots[2]?.name === 'iron_sword' && !enchanted(window.slots[2]), {
                label: 'vanilla grindstone produces a disenchanted sword', timeoutMs: 4000,
            })
            await input.actor.bot.clickWindow(2, 0, 1)
            await input.actor.bot.waitForTicks(4)
            attempts++
            const recovered = books(await input.snapshot())
            if (recovered.length > 0) return { attempts, books: recovered }
        }
        return { attempts, books: [] }
    } finally { input.actor.bot.closeWindow(window) }
}

async function lapis(input) {
    const before = await input.snapshot()
    let spent = 0
    let attempts = 0
    for (; attempts < 30;) {
        const table = await input.actor.bot.openEnchantmentTable(block(input))
        try {
            const sword = table.items().find(item => item.name === 'iron_sword' && !enchanted(item))
            input.context.expect(Boolean(sword), 'Natural enchanting has a fresh unenchanted sword')
            await table.putTargetItem(sword)
            await table.putLapis(table.items().find(item => item.name === 'lapis_lazuli'))
            await input.context.waitUntil(() => table.enchantments.some(option => option.level > 0), {
                label: 'natural enchantment offer is ready', timeoutMs: 5000,
            })
            const option = table.enchantments.findIndex(value => value.level > 0)
            await table.enchant(option)
            const output = await table.takeTargetItem()
            input.context.expect(enchanted(output), 'Natural table operation creates an enchanted sword')
            spent += option + 1
            attempts++
        } finally { table.close() }
        await input.actor.bot.waitForTicks(4)
        const refund = count(await input.snapshot(), 'lapis_lazuli') - count(before, 'lapis_lazuli') + spent
        if (refund > 0) return { attempts, spent, refund }
    }
    return { attempts, spent, refund: 0 }
}

async function soul(input) {
    let deaths = 0
    const onDeath = () => { deaths++ }
    input.actor.bot.on('death', onDeath)
    try {
        input.actor.bot.setControlState('sneak', true)
        await input.actor.bot.waitForTicks(3)
        await input.actor.bot.activateBlock(block(input, 2, 2))
        await input.actor.bot.waitForTicks(8)
        if (input.actor.bot.currentWindow) input.actor.bot.closeWindow(input.actor.bot.currentWindow)
        input.actor.bot.setControlState('sneak', false)
        const target = input.opponent.bot.entities[input.actor.bot.entity.id]
        input.context.expect(Boolean(target), 'Soul Link owner is visible to the ordinary attacker')
        await input.opponent.bot.lookAt(target.position.offset(0, 1, 0), true)
        input.opponent.bot.attack(target)
        await input.context.waitUntil(() => deaths > 0 && input.actor.bot.health > 0, {
            label: 'Soul Link owner dies and naturally respawns', timeoutMs: 7000,
        })
        await input.actor.bot.waitForTicks(26)
        const after = await input.snapshot()
        return { deaths, retained: after.knowledge.inventory.filter(item => item.type === 'minecraft:diamond_pickaxe') }
    } finally { input.actor.bot.off('death', onDeath) }
}

async function villager(input) {
    const before = await input.snapshot()
    const entity = input.actor.bot.entities[before.discovery.targets[0].entityId]
    const merchant = await input.actor.bot.openVillager(entity)
    try {
        await input.actor.bot.waitForTicks(5)
        const opened = await input.snapshot()
        await merchant.trade(0, 1)
        await input.actor.bot.waitForTicks(5)
        const after = await input.snapshot()
        return { emeraldCost: count(before, 'emerald') - count(after, 'emerald'), bread: count(after, 'bread'),
            levelsPaid: before.knowledge.level - opened.knowledge.level, effects: opened.effects }
    } finally { merchant.close() }
}

async function glimmer(input) {
    await input.actor.bot.lookAt(block(input).position.offset(0.5, 0.5, 0.5), true)
    await input.actor.bot.waitForTicks(60)
    const glowing = bot => Object.values(bot.entities).filter(entity => entity.name === 'block_display' && (entity.metadata[0] & 0x40))
        .map(entity => ({ id: entity.id, x: entity.position.x, y: entity.position.y, z: entity.position.z }))
    return { actor: glowing(input.actor.bot), opponent: glowing(input.opponent.bot) }
}

async function polymath(input) {
    const initial = await input.snapshot()
    const samples = []
    for (let sample = 0; sample < 24; sample++) {
        await input.actor.bot.waitForTicks(5)
        const state = await input.snapshot()
        samples.push({ multiplier: state.discovery.xpMultiplier, boosts: state.discovery.polymathBoosts })
    }
    return { multiplier: Math.max(...samples.map(sample => sample.multiplier)),
        boosts: Math.max(...samples.map(sample => sample.boosts)),
        qualifyingLevels: initial.discovery.qualifyingLevels, samples }
}

async function cartographer(input) {
    const before = await input.snapshot()
    input.actor.bot.setControlState('sneak', true)
    await input.actor.bot.waitForTicks(3)
    await input.actor.bot.look(0, 0, true)
    input.actor.bot.activateItem()
    await input.actor.bot.waitForTicks(25)
    input.actor.bot.deactivateItem()
    input.actor.bot.setControlState('sneak', false)
    const after = await input.snapshot()
    return { before: before.discovery, after: after.discovery,
        display: Object.values(input.actor.bot.entities).some(entity => entity.name === 'block_display' && (entity.metadata[0] & 0x40)) }
}

async function sixth(input) {
    const before = await input.snapshot()
    const actionBars = []
    const onActionBar = message => actionBars.push(message.toString())
    input.actor.bot.on('actionBar', onActionBar)
    try {
        await input.actor.bot.waitForTicks(400)
        const after = await input.snapshot()
        return { sensesBefore: before.discovery.senses, sensesAfter: after.discovery.senses,
            progress: input.actor.bot.experience.progress,
            actionBars: [...new Set(actionBars)] }
    } finally { input.actor.bot.off('actionBar', onActionBar) }
}

export const discoveryBehaviorCases = new Map([
    ['discovery-cartographer-pulse', {
        stage: 'discovery-qa-cartographer', negativeWindowTicks: 1, prepare: input => prepare(input, 'compass'), trigger: cartographer,
        sound: 'minecraft:item.lodestone_compass.lock', soundVolume: 0.8, soundPitch: 1.3, particle: 'electric_spark', particleCount: 1,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.before.compassX === unlearned.after.compassX && unlearned.before.compassZ === unlearned.after.compassZ
                && unlearned.before.food === unlearned.after.food && !unlearned.display, 'Unlearned compass use leaves the target and food unchanged')
            context.expect(active.after.compassX === active.after.structureX && active.after.compassZ === active.after.structureZ
                && active.before.food - active.after.food === 2 && active.display,
                'Learned compass locks onto a real generated structure, consumes two hunger, and sends its direction display')
            return result(['unlearned compass has no pulse outcome', 'learned natural compass use targets actual generated structure metadata and pays food'], unlearned, active)
        },
    }],
    ['discovery-sixth-sense', {
        stage: 'discovery-qa-sixth', negativeWindowTicks: 1, prepare: input => prepare(input), trigger: sixth,
        sound: 'minecraft:block.amethyst_block.chime', soundVolume: 0.22, soundPitch: 1.9, particle: 'end_rod', particleCount: 1,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.sensesAfter === unlearned.sensesBefore && unlearned.progress === 0,
                'Unlearned player beside a generated structure receives no detection or XP-bar pin')
            context.expect(active.sensesAfter > unlearned.sensesAfter && active.progress > 0 && active.progress < 1 && active.actionBars.some(message => /\b(?:N|NE|E|SE|S|SW|W|NW)\s+\d+m\b/.test(message)),
                'Learned passive detection sends a structure HUD and distance-based client experience bar')
            return result(['unlearned generated structure proximity has no detection', 'learned natural ticks discover a real structure and send HUD and proximity bar packets'], unlearned, active)
        },
    }],
    ['discovery-archaeologist', {
        stage: 'discovery-qa-brush', negativeWindowTicks: 1, prepare: input => prepare(input, 'brush'), trigger: brush,
        sound: 'minecraft:item.brush.brushing.sand.complete', soundVolume: 1, soundPitch: 1.15, particle: 'enchant', particleCount: 3,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.attempts === 12 && unlearned.rewards.length === 0, 'Twelve unlearned completed brushes yield no bonus material')
            context.expect(active.attempts > 0 && active.attempts <= 12 && active.rewards.length > 0, 'Learned completed brushing yields an actual bonus item within twelve trials')
            return result(['unlearned completed brushing has no bonus item', 'learned natural brushing creates a source-listed bonus item'], unlearned, active)
        },
    }],
    ['enchanting-arcane-siphon', {
        stage: 'discovery-qa-siphon', negativeWindowTicks: 1, prepare: input => prepare(input, 'wooden_axe'), trigger: siphon,
        sound: 'minecraft:block.amethyst_block.chime', soundVolume: 0.6, soundPitch: 1.3, particle: 'enchant',
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.kills === 20 && unlearned.books.length === 0, 'Twenty unlearned enchanted-mob kills produce no enchanted book')
            context.expect(active.kills > 0 && active.books.some(item => item.enchantments['minecraft:protection'] === 4), 'Learned natural kill drops a Protection IV book from Protection II gear')
            return result(['unlearned natural kills produce no book', 'learned kill siphons and upgrades the actual equipped enchantment'], unlearned, active)
        },
    }],
    ['enchanting-grindstone-recovery', {
        stage: 'discovery-qa-grind', negativeWindowTicks: 1, prepare: input => prepare(input), trigger: grind,
        sound: 'minecraft:block.grindstone.use', soundVolume: 0.95, soundPitch: 1.15, particle: 'flash', particleCount: 1,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.attempts === 30 && unlearned.books.length === 0, 'Thirty unlearned grindstone outputs return no enchantment book')
            context.expect(active.books.some(item => item.enchantments['minecraft:sharpness'] === 2), 'Learned ordinary grindstone output also recovers Sharpness II on a book')
            return result(['unlearned disenchants produce no book', 'learned actual disenchanted output recovers its removed enchantment'], unlearned, active)
        },
    }],
    ['enchanting-lapis-return', {
        stage: 'discovery-qa-lapis', negativeWindowTicks: 1, prepare: input => prepare(input), trigger: lapis,
        sound: 'minecraft:block.amethyst_block.chime', soundVolume: 0.7, soundPitch: 0.9, particle: 'glow', particleCount: 3,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.attempts === 30 && unlearned.refund === 0, 'Thirty unlearned enchants consume exactly their ordinary lapis costs')
            context.expect(active.attempts > 0 && active.refund === 3, 'Max-level natural enchanting returns three lapis above the ordinary cost')
            return result(['unlearned enchanting consumes ordinary lapis', 'max-level completed enchanting refunds three actual lapis'], unlearned, active)
        },
    }],
    ['enchanting-soul-link', {
        stage: 'discovery-qa-soul', negativeWindowTicks: 1,
        prepare: async input => { await prepare(input, 'diamond_pickaxe'); await input.equip('wooden_sword', input.opponent) }, trigger: soul,
        sound: 'minecraft:block.amethyst_block.chime', soundVolume: 0.6, soundPitch: 0.9, particle: 'end_rod', particleCount: 4,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.deaths === 1 && unlearned.retained.length === 0, 'Unlearned anvil interaction does not preserve the item through actual death')
            context.expect(active.deaths === 1 && active.retained.length === 1 && active.retained[0].enchantments['minecraft:efficiency'] === 3,
                'Learned mark returns the same enchanted pickaxe after actual death and respawn')
            return result(['unlearned death loses the enchanted item', 'learned mark preserves its exact item type and enchantment through death'], unlearned, active)
        },
    }],
    ['discovery-villager-att', {
        stage: 'discovery-qa-villager', negativeWindowTicks: 1, prepare: input => prepare(input), trigger: villager,
        sound: 'minecraft:entity.villager.celebrate', soundVolume: 1, soundPitch: 1, particle: 'wax_on', particleCount: 2,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.bread === 1 && unlearned.emeraldCost === 20 && unlearned.levelsPaid === 0, 'Unlearned real trade pays its ordinary twenty emeralds without level cost')
            context.expect(active.bread === 1 && active.emeraldCost < 20 && active.levelsPaid === 1 && active.effects.some(effect => effect.type === 'minecraft:hero_of_the_village'),
                'Learned session pays one level and completes a discounted real trade')
            return result(['unlearned trade costs twenty emeralds', 'learned hero session reduces actual trade cost and consumes its level payment'], unlearned, active)
        },
    }],
    ['discovery-keen-eye', {
        stage: 'discovery-qa-keen', negativeWindowTicks: 1, prepare: input => prepare(input), trigger: glimmer,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.actor.length === 0 && unlearned.opponent.length === 0, 'Unlearned chest has no private glowing display')
            context.expect(active.actor.some(entity => Math.abs(entity.x - 2) < 1 && entity.y === 100 && Math.abs(entity.z) < 1) && active.opponent.length === 0,
                'Learned visible chest receives a glowing block display only for its learner')
            return result(['unlearned chest has no glow display', 'learned chest glimmer reaches only its intended client'], unlearned, active)
        },
    }],
    ['discovery-polymath', {
        stage: 'discovery-qa-polymath', negativeWindowTicks: 1, prepare: input => prepare(input), trigger: polymath,
        sound: 'minecraft:block.amethyst_block.chime', soundVolume: 0.25, soundPitch: 1.9, particle: 'enchant', particleCount: 4,
        verify: ({ context, unlearned, active }) => {
            context.expect(Object.keys(active.qualifyingLevels).length > 0
                && JSON.stringify(Object.entries(active.qualifyingLevels).sort()) === JSON.stringify(Object.entries(unlearned.qualifyingLevels).sort()),
                'Both trials contain the same actual skills at or above the configured five-level threshold')
            context.expect(active.multiplier >= unlearned.multiplier + 0.059 && active.boosts > unlearned.boosts,
                'Learned ticking converts the same qualifying skill levels into an active global XP multiplier')
            return result(['qualifying skill loadout is identical in both trials', 'learned passive ticking increases the effective global XP multiplier'], unlearned, active)
        },
    }],
    ['discovery-trailblazer', {
        stage: 'discovery-qa-trail', negativeWindowTicks: 25, attribute: 'minecraft:movement_speed',
        prepare: input => prepare(input), trigger: async input => {
            input.actor.bot.setControlState('forward', true)
            await input.actor.bot.waitForTicks(8)
            input.actor.bot.clearControlStates()
        },
        sound: 'minecraft:entity.player.levelup', soundVolume: 0.5, soundPitch: 1.4, particle: 'dust', particleCount: 1,
    }],
])
