function outcome(assertions, unlearned, active) {
    return { assertions, measurements: { unlearned, active } }
}

function item(state, type) {
    return state.knowledge.inventory.find(value => value.type === `minecraft:${type}`)
}

function count(state, type) {
    return state.knowledge.inventory.reduce((total, value) => total + (value.type === `minecraft:${type}` ? value.amount : 0), 0)
}

async function prepare(input, tool) {
    input.actor.bot.clearControlStates()
    input.opponent.bot.clearControlStates()
    input.actor.bot.deactivateItem()
    if (tool) await input.equip(tool)
    await input.actor.bot.waitForTicks(4)
}

function block(input) {
    return input.actor.bot.blockAt(input.actor.bot.entity.position.clone().set(2, 100, 0))
}

async function open(input, type) {
    await input.actor.bot.activateBlock(block(input))
    await input.context.waitUntil(() => input.actor.bot.currentWindow?.type === `minecraft:${type}`, {
        label: `${type} opens through natural block use`, timeoutMs: 5000,
    })
    return input.actor.bot.currentWindow
}

async function insert(input, window, name, targetSlot) {
    const source = window.slots.slice(window.inventoryStart).find(value => value?.name === name)
    input.context.expect(Boolean(source), `${name} is available in survival inventory`)
    await input.actor.bot.clickWindow(source.slot, 0, 0)
    await input.actor.bot.waitForTicks(2)
    await input.actor.bot.clickWindow(targetSlot, 0, 0)
    await input.actor.bot.waitForTicks(3)
}

async function mend(input) {
    const before = await input.snapshot()
    input.actor.bot.setControlState('sneak', true)
    await input.actor.bot.waitForTicks(3)
    await input.actor.bot.look(0, Math.PI / 2, true)
    await input.actor.bot.waitForTicks(3)
    input.actor.bot.swingArm('right')
    await input.actor.bot.waitForTicks(8)
    input.actor.bot.setControlState('sneak', false)
    return { before, after: await input.snapshot() }
}

async function quick(input) {
    const before = await input.snapshot()
    const book = input.actor.bot.inventory.items().find(value => value.name === 'enchanted_book')
    const sword = input.actor.bot.inventory.items().find(value => value.name === 'iron_sword')
    input.context.expect(Boolean(book && sword), 'Quick Enchant has a real book and sword')
    await input.actor.bot.clickWindow(book.slot, 0, 0)
    await input.actor.bot.waitForTicks(2)
    await input.actor.bot.clickWindow(sword.slot, 0, 0)
    await input.actor.bot.waitForTicks(4)
    const empty = input.actor.bot.inventory.slots.findIndex((value, index) => index >= 9 && index <= 44 && !value)
    input.context.expect(empty >= 0, 'Cursor contents have an empty storage slot')
    await input.actor.bot.clickWindow(empty, 0, 0)
    await input.actor.bot.waitForTicks(4)
    return { before, after: await input.snapshot() }
}

async function infusion(input) {
    const before = await input.snapshot()
    const window = await open(input, 'anvil')
    try {
        await insert(input, window, 'iron_sword', 0)
        await insert(input, window, 'enchanted_book', 1)
        await input.actor.bot.clickWindow(0, 1, 0)
        await input.actor.bot.waitForTicks(6)
    } finally { input.actor.bot.closeWindow(window) }
    await input.actor.bot.waitForTicks(4)
    return { before, after: await input.snapshot() }
}

async function tome(input) {
    const before = await input.snapshot()
    await input.actor.bot.lookAt(block(input).position.offset(0.5, 0.5, 0.5), true)
    await input.actor.bot.waitForTicks(3)
    const book = input.actor.bot.inventory.items().find(value => value.name === 'enchanted_book')
    input.context.expect(Boolean(book), 'Rebinding has a real multi-enchantment book')
    await input.actor.bot.tossStack(book)
    await input.actor.bot.waitForTicks(8)
    return { before, after: await input.snapshot() }
}

async function reroll(input) {
    const before = await input.snapshot()
    input.actor.bot.setControlState('sneak', true)
    await input.actor.bot.waitForTicks(3)
    await input.actor.bot.activateBlock(block(input))
    await input.actor.bot.waitForTicks(8)
    if (input.actor.bot.currentWindow) input.actor.bot.closeWindow(input.actor.bot.currentWindow)
    input.actor.bot.setControlState('sneak', false)
    return { before, after: await input.snapshot() }
}

async function cleanse(input) {
    const before = await input.snapshot()
    const window = await open(input, 'grindstone')
    try {
        await insert(input, window, 'iron_sword', 0)
        await input.context.waitUntil(() => window.slots[2]?.name === 'iron_sword', {
            label: 'natural grindstone result appears', timeoutMs: 4000,
        })
        input.actor.bot.setControlState('sneak', true)
        await input.actor.bot.waitForTicks(3)
        await input.actor.bot.clickWindow(2, 0, 1)
        await input.actor.bot.waitForTicks(6)
    } finally {
        input.actor.bot.setControlState('sneak', false)
        input.actor.bot.closeWindow(window)
    }
    await input.actor.bot.waitForTicks(4)
    return { before, after: await input.snapshot() }
}

async function anvil(input) {
    const before = await input.snapshot()
    const window = await open(input, 'anvil')
    let preview
    try {
        await insert(input, window, 'iron_sword', 0)
        await insert(input, window, 'enchanted_book', 1)
        await input.context.waitUntil(() => window.slots[2]?.name === 'iron_sword', {
            label: 'anvil offers upgraded sword', timeoutMs: 4000,
        })
        preview = await input.snapshot()
        await input.actor.bot.clickWindow(2, 0, 1)
        await input.actor.bot.waitForTicks(6)
    } finally { input.actor.bot.closeWindow(window) }
    await input.actor.bot.waitForTicks(4)
    return { before, preview, after: await input.snapshot() }
}

async function collectExperience(input) {
    const before = await input.snapshot()
    const orb = Object.values(input.actor.bot.entities).find(entity => entity.name === 'experience_orb')
    input.context.expect(Boolean(orb), 'The real experience orb is visible before collection')
    let after
    try {
        await input.context.waitUntil(async () => {
            after = await input.snapshot()
            if (after.knowledge.experience > before.knowledge.experience) return true
            input.context.expect(Math.abs(after.location.x) < 18 && Math.abs(after.location.z) < 18,
                'Orb collection stays on the arena platform', { before: before.location, after: after.location })
            const target = input.actor.bot.entities[orb.id]
            if (target) {
                await input.actor.bot.lookAt(target.position, true)
                input.actor.bot.setControlState('forward', input.actor.bot.entity.position.distanceTo(target.position) > 0.8)
            } else input.actor.bot.setControlState('forward', false)
            return false
        }, { label: 'player walks to and collects the real experience orb', timeoutMs: 10000, intervalMs: 100 })
    } finally { input.actor.bot.setControlState('forward', false) }
    await input.actor.bot.waitForTicks(5)
    return { before, after: await input.snapshot() }
}

async function resist(input) {
    const before = await input.snapshot()
    const target = input.opponent.bot.entities[input.actor.bot.entity.id]
    input.context.expect(Boolean(target), 'Low-health XP Resist target is visible')
    await input.opponent.bot.lookAt(target.position.offset(0, 1, 0), true)
    input.opponent.bot.attack(target)
    let after
    await input.context.waitUntil(async () => {
        after = await input.snapshot()
        return after.health < before.health
    }, { label: 'natural sword hit damages low-health target', timeoutMs: 2500 })
    return { before, after, damage: before.health - after.health }
}

async function notes(input) {
    const before = await input.snapshot()
    const first = input.actor.bot.entities[before.knowledge.targets.first.entityId]
    await input.actor.bot.lookAt(first.position.offset(0, 0.7, 0), true)
    input.actor.bot.attack(first)
    await input.context.waitUntil(() => !input.actor.bot.entities[first.id], { label: 'first species kill completes naturally', timeoutMs: 3000 })
    await input.actor.bot.waitForTicks(24)
    const banked = await input.snapshot()
    const second = input.actor.bot.entities[banked.knowledge.targets.second.entityId]
    await input.actor.bot.lookAt(second.position.offset(0, 0.7, 0), true)
    input.actor.bot.attack(second)
    let after
    await input.context.waitUntil(async () => {
        after = await input.snapshot()
        return after.knowledge.targets.second.health < banked.knowledge.targets.second.health
    }, { label: 'subsequent natural sword strike hits same species', timeoutMs: 2500 })
    return { before, banked, after, damage: banked.knowledge.targets.second.health - after.knowledge.targets.second.health }
}

async function enchant(input) {
    const before = await input.snapshot()
    const table = await input.actor.bot.openEnchantmentTable(block(input))
    let enchanted
    try {
        await table.putTargetItem(table.items().find(value => value.name === 'iron_sword'))
        await table.putLapis(table.items().find(value => value.name === 'lapis_lazuli'))
        await input.context.waitUntil(() => table.enchantments.some(option => option.level > 0), {
            label: 'natural enchantment offers available', timeoutMs: 5000,
        })
        await table.enchant(table.enchantments.findIndex(option => option.level > 0))
        enchanted = await table.takeTargetItem()
    } finally { table.close() }
    input.context.expect(Boolean(enchanted), 'Enchanted item returns to inventory')
    await input.actor.bot.waitForTicks(30)
    return { before, after: await input.snapshot() }
}

export const knowledgeBehaviorCases = new Map([
    ['discovery-better-mending', {
        stage: 'knowledge-mending', sound: 'minecraft:entity.experience_orb.pickup', soundVolume: 0.5, soundPitch: 1.2, particle: 'enchant',
        prepare: input => prepare(input, 'diamond_pickaxe'), trigger: mend,
        verify: ({ context, unlearned, active }) => {
            context.expect(item(unlearned.after, 'diamond_pickaxe').damage === 240, 'Control sneak click does not repair Mending tool')
            context.expect(item(active.after, 'diamond_pickaxe').damage < 240, 'Learned sneak click repairs the actual tool')
            context.expect(active.after.knowledge.experience < active.before.knowledge.experience, 'Repair spends real vanilla XP')
            return outcome(['natural sneak attack repairs Mending tool and consumes XP'], unlearned, active)
        },
    }],
    ['discovery-xp-resist', {
        stage: 'knowledge-resist', sound: 'minecraft:entity.iron_golem.repair', soundVolume: 0.8, soundPitch: 0.8, particle: 'electric_spark',
        prepare: input => prepare(input), trigger: resist,
        verify: ({ context, unlearned, active }) => {
            context.expect(active.damage > 0 && active.damage < unlearned.damage * 0.2, 'XP Resist reduces actual critical-health damage')
            context.expect(active.after.knowledge.level < active.before.knowledge.level, 'Damage reduction spends vanilla experience levels')
            return outcome(['same natural sword hit deals less health damage and spends XP at critical health'], unlearned, active)
        },
    }],
    ['discovery-field-notes', {
        stage: 'knowledge-notes', sound: 'minecraft:ui.toast.challenge_complete', soundVolume: 0.4, soundPitch: 1.2, particle: 'totem_of_undying', particleCount: 10, particleOffset: 0.2,
        prepare: input => prepare(input, 'wooden_sword'), trigger: notes,
        verify: ({ context, unlearned, active }) => {
            context.expect(active.damage > unlearned.damage + 0.01, 'Learning and killing a species increases subsequent health damage against that species')
            context.expect(active.banked.stats['discovery.field-notes.bonus.COW'] > (active.before.stats['discovery.field-notes.bonus.COW'] ?? 0), 'Actual kill banks permanent species damage')
            return outcome(['natural species kill banks damage used on the next same-species target'], unlearned, active)
        },
    }],
    ['discovery-unity', {
        stage: 'knowledge-unity', sound: 'minecraft:block.amethyst_block.chime', soundVolume: 0.4, soundPitch: 1.7, particle: 'wax_on',
        prepare: input => prepare(input), trigger: collectExperience,
        verify: ({ context, unlearned, active }) => {
            const key = 'discovery.unity.orbs-distributed'
            context.expect((unlearned.after.stats[key] ?? 0) === (unlearned.before.stats[key] ?? 0), 'Control orb pickup does not distribute skill XP')
            context.expect(active.after.stats[key] > (active.before.stats[key] ?? 0), 'Learned natural pickup distributes an orb award')
            context.expect(Object.keys(active.after.xp).some(skill => active.after.xp[skill] > active.before.xp[skill]), 'Orb distribution raises actual skill XP')
            return outcome(['natural experience pickup distributes real skill XP'], unlearned, active)
        },
    }],
    ['enchanting-quick-enchant', {
        stage: 'knowledge-quick', sound: 'minecraft:block.enchantment_table.use', soundVolume: 1, soundPitch: 1.7, particle: 'end_rod',
        prepare: input => prepare(input), trigger: quick,
        verify: ({ context, unlearned, active }) => {
            context.expect(!item(unlearned.after, 'iron_sword').enchantments['minecraft:sharpness'], 'Control cursor swap does not enchant sword')
            context.expect(item(active.after, 'iron_sword').enchantments['minecraft:sharpness'] === 3, 'Learned cursor application transfers Sharpness III')
            context.expect(count(active.after, 'book') === 0 && count(active.after, 'enchanted_book') === 0, 'Fully transferred book is consumed')
            return outcome(['natural inventory book-on-item click enchants sword and consumes stored enchantment'], unlearned, active)
        },
    }],
    ['enchanting-infusion-transfer', {
        stage: 'knowledge-infusion', sound: 'minecraft:block.anvil.use', soundVolume: 0.6, soundPitch: 1.3, particle: 'crit',
        prepare: input => prepare(input), trigger: infusion,
        verify: ({ context, unlearned, active }) => {
            context.expect(!item(unlearned.after, 'iron_sword').enchantments['minecraft:sharpness'], 'Control anvil right-click takes unenchanted sword')
            context.expect(item(active.after, 'iron_sword').enchantments['minecraft:sharpness'] === 3, 'Infusion transfers Sharpness III into actual sword')
            context.expect(active.before.knowledge.level - active.after.knowledge.level === 3, 'Infusion charges configured maximum-level XP cost')
            context.expect(count(active.after, 'book') === 1, 'Guaranteed maximum-level source survival leaves stripped book')
            return outcome(['natural anvil right-click transfers enchantment, strips source and charges levels'], unlearned, active)
        },
    }],
    ['enchanting-tome-rebinding', {
        stage: 'knowledge-tome', sound: 'minecraft:item.book.page_turn', soundVolume: 0.9, soundPitch: 1.2, particle: 'crit',
        prepare: input => prepare(input, 'enchanted_book'), trigger: tome,
        verify: ({ context, unlearned, active }) => {
            const control = unlearned.after.knowledge.drops.filter(value => value.type === 'minecraft:enchanted_book')
            const split = active.after.knowledge.drops.filter(value => value.type === 'minecraft:enchanted_book')
            context.expect(control.length === 1 && Object.keys(control[0].enchantments).length === 2, 'Control toss keeps both enchants on one book')
            context.expect(split.length === 2 && split.every(value => Object.keys(value.enchantments).length === 1), 'Learned toss splits both enchants into separate real dropped books')
            context.expect(active.before.knowledge.level - active.after.knowledge.level === 2, 'Rebinding charges two levels')
            return outcome(['natural book toss at anvil splits enchantments into two item entities at configured cost'], unlearned, active)
        },
    }],
    ['enchanting-offer-reroll', {
        stage: 'knowledge-reroll', sound: 'minecraft:block.enchantment_table.use', soundVolume: 1, soundPitch: 1.2, particle: 'flash',
        prepare: input => prepare(input), trigger: reroll,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.after.knowledge.seed === unlearned.before.knowledge.seed, 'Control sneak use preserves enchantment seed')
            context.expect(active.after.knowledge.seed !== active.before.knowledge.seed, 'Learned table interaction rerolls actual player enchantment seed')
            context.expect(count(active.before, 'lapis_lazuli') - count(active.after, 'lapis_lazuli') === 2, 'Reroll consumes two lapis')
            context.expect(active.before.knowledge.level - active.after.knowledge.level === 1, 'Reroll consumes one vanilla level')
            return outcome(['natural sneak use rerolls enchantment seed and consumes configured lapis and XP'], unlearned, active)
        },
    }],
    ['enchanting-curse-cleansing', {
        stage: 'knowledge-cleanse', sound: 'minecraft:block.grindstone.use', soundVolume: 0.8, soundPitch: 0.7, particle: 'smoke',
        prepare: input => prepare(input), trigger: cleanse,
        verify: ({ context, unlearned, active }) => {
            context.expect(item(unlearned.after, 'iron_sword').enchantments['minecraft:vanishing_curse'] === 1, 'Vanilla grindstone preserves curse')
            context.expect(!item(active.after, 'iron_sword').enchantments['minecraft:vanishing_curse'], 'Learned sneak grindstone result removes curse')
            context.expect(count(active.after, 'iron_sword') === 1, 'Cleansing returns exactly one sword')
            return outcome(['natural grindstone output click removes an otherwise retained curse'], unlearned, active)
        },
    }],
    ['enchanting-anvil-savant', {
        stage: 'knowledge-anvil', prepare: input => prepare(input), trigger: anvil,
        verify: ({ context, unlearned, active }) => {
            context.expect(active.preview.knowledge.repairCost < unlearned.preview.knowledge.repairCost, 'Learned anvil preview has lower actual cost')
            context.expect(active.before.knowledge.level - active.after.knowledge.level < unlearned.before.knowledge.level - unlearned.after.knowledge.level, 'Taking result actually spends fewer levels')
            context.expect(item(active.after, 'iron_sword').enchantments['minecraft:sharpness'] === 4, 'Discounted anvil still returns Sharpness IV sword')
            return outcome(['natural anvil combination previews and charges reduced cost for the same enchanted output'], unlearned, active)
        },
    }],
    ['enchanting-echo-of-knowledge', {
        stage: 'knowledge-echo', sound: 'minecraft:block.enchantment_table.use', soundVolume: 0.8, soundPitch: 1.5, particle: 'enchant',
        prepare: input => prepare(input, 'enchanted_book'), trigger: collectExperience,
        verify: ({ context, unlearned, active }) => {
            context.expect(item(unlearned.after, 'enchanted_book').enchantments['minecraft:sharpness'] === 1, 'Control orb pickup does not upgrade held book')
            context.expect(item(active.after, 'enchanted_book').enchantments['minecraft:sharpness'] === 2, 'Forty natural orb XP upgrades held Sharpness book once')
            return outcome(['natural orb pickup charges and upgrades actual held enchanted book'], unlearned, active)
        },
    }],
    ['enchanting-xp-return', {
        stage: 'knowledge-enchant', sound: 'minecraft:entity.experience_orb.pickup', soundVolume: 0.5, soundPitch: 1.6, particle: 'happy_villager',
        prepare: input => prepare(input), trigger: enchant,
        verify: ({ context, unlearned, active }) => {
            context.expect(Object.keys(item(active.after, 'iron_sword').enchantments).length > 0, 'Natural table enchanting produces a real enchanted sword')
            context.expect(active.after.knowledge.experience > unlearned.after.knowledge.experience, 'Learned enchant returns collectible vanilla XP compared with identical control enchant')
            return outcome(['natural table enchant returns real vanilla XP while producing the enchanted item'], unlearned, active)
        },
    }],
])
