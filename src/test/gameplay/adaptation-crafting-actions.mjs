function count(state, type) {
    return state.crafting.inventory.reduce((total, value) => total + (value.type === `minecraft:${type}` ? value.amount : 0), 0)
}

function report(assertions, unlearned, active) {
    return { assertions, measurements: { unlearned, active } }
}

async function prepare(input, held) {
    input.actor.bot.clearControlStates()
    input.actor.bot.deactivateItem()
    if (held) await input.equip(held)
    await input.actor.bot.waitForTicks(4)
}

async function click(input, slot, button = 0, mode = 0) {
    await input.actor.bot.clickWindow(slot, button, mode)
    await input.actor.bot.waitForTicks(2)
}

async function put(input, window, target, name, amount) {
    const stack = window.slots.slice(window.inventoryStart).find(value => value?.name === name && value.count >= amount)
    input.context.expect(Boolean(stack), `${amount} ${name} available for crafting slot ${target}`)
    const source = stack.slot
    const fullStack = stack.count === amount
    await click(input, source)
    if (fullStack) await click(input, target)
    else {
        for (let index = 0; index < amount; index++) await click(input, target, 1)
        await click(input, source)
    }
}

async function take(input, window, slot) {
    await click(input, slot)
    const empty = window.slots.findIndex((value, index) => index >= window.inventoryStart && index < window.inventoryEnd && !value)
    input.context.expect(empty >= 0, 'Crafted output has an empty survival inventory slot')
    await click(input, empty)
}

async function table(input) {
    const block = input.actor.bot.blockAt(input.actor.bot.entity.position.clone().set(2, 100, 2))
    await input.actor.bot.activateBlock(block)
    await input.context.waitUntil(() => input.actor.bot.currentWindow?.type === 'minecraft:crafting', {
        label: 'real crafting table opens', timeoutMs: 5000,
    })
    return input.actor.bot.currentWindow
}

async function craft(input, specification) {
    const before = await input.snapshot()
    const window = await table(input)
    try {
        for (const [slot, name, amount] of specification.grid) await put(input, window, slot, name, amount)
        for (let index = 0; index < (specification.trials ?? 1); index++) {
            await input.context.waitUntil(() => window.slots[0]?.name === specification.output, {
                label: `craft ${index + 1} has natural ${specification.output} recipe output`, timeoutMs: 4000,
            })
            if (specification.shift) await click(input, 0, 0, 1)
            else await take(input, window, 0)
        }
        await input.actor.bot.waitForTicks(6)
    } finally { input.actor.bot.closeWindow(window) }
    await input.actor.bot.waitForTicks(4)
    return { before, after: await input.snapshot(), trials: specification.trials ?? 1 }
}

async function backpack(input) {
    const crafted = await craft(input, { output: 'bundle', grid: [1, 2, 3, 4, 6, 7, 8, 9].map(slot => [slot, 'leather', 1]).concat([[5, 'chest', 1]]) })
    if (count(crafted.after, 'bundle') === 0) return { ...crafted, persisted: false }
    await input.equip('bundle')
    await input.actor.bot.look(0, Math.PI / 2, true)
    await input.actor.bot.waitForTicks(3)
    input.actor.bot.activateItem()
    await input.context.waitUntil(() => Boolean(input.actor.bot.currentWindow), { label: 'crafted backpack opens', timeoutMs: 4000 })
    let window = input.actor.bot.currentWindow
    const apple = window.slots.slice(window.inventoryStart).find(value => value?.name === 'apple')
    input.context.expect(Boolean(apple), 'Backpack deposit uses three actual apples')
    await click(input, apple.slot)
    await click(input, 0)
    input.actor.bot.closeWindow(window)
    await input.actor.bot.waitForTicks(8)
    input.actor.bot.activateItem()
    await input.context.waitUntil(() => Boolean(input.actor.bot.currentWindow), { label: 'backpack reopens after storing items', timeoutMs: 4000 })
    window = input.actor.bot.currentWindow
    const persisted = window.slots[0]?.name === 'apple' && window.slots[0].count === 3
    try { await take(input, window, 0) }
    finally { input.actor.bot.closeWindow(window) }
    await input.actor.bot.waitForTicks(4)
    return { ...crafted, persisted, after: await input.snapshot() }
}

async function leather(input) {
    const before = await input.snapshot()
    const campfire = input.actor.bot.blockAt(input.actor.bot.entity.position.clone().set(2, 100, 0))
    await input.actor.bot.activateBlock(campfire)
    await input.actor.bot.waitForTicks(110)
    return { before, after: await input.snapshot() }
}

async function deconstruct(input) {
    const before = await input.snapshot()
    const dropped = before.crafting.drops.find(value => value.type === 'minecraft:iron_chestplate')
    input.context.expect(Boolean(dropped), 'Deconstruction has a real dropped chestplate')
    await input.context.waitUntil(() => Boolean(input.actor.bot.entities[dropped.entityId]), {
        label: 'dropped salvage target becomes client visible', timeoutMs: 3000, intervalMs: 50,
    })
    input.actor.bot.setControlState('sneak', true)
    await input.actor.bot.waitForTicks(3)
    const aimed = await input.snapshot()
    const current = aimed.crafting.drops.find(value => value.entityId === dropped.entityId)
    input.context.expect(Boolean(current), 'Salvage target still exists after sneaking')
    const dx = current.x - aimed.location.x
    const dy = current.y + 0.125 - aimed.location.y - aimed.crafting.eyeHeight
    const dz = current.z - aimed.location.z
    await input.actor.bot.look(Math.atan2(-dx, -dz), Math.atan2(dy, Math.hypot(dx, dz)), true)
    await input.actor.bot.waitForTicks(3)
    const checked = await input.snapshot()
    input.context.expect(checked.crafting.rayItemId === dropped.entityId, `Actual server eye ray reaches dropped chestplate (hit ${checked.crafting.rayItemId ?? 'none'}, expected ${dropped.entityId})`)
    const eligible = checked.crafting.drops.find(value => value.entityId === dropped.entityId)
    input.context.expect(eligible.pickupEligible, 'Actual dropped salvage input passes item ownership and pickup guards')
    input.context.expect(eligible.salvage?.some(item => item.type === 'minecraft:iron_ingot' && item.amount === 4), 'Actual registered recipe offers four salvage ingots for the undamaged chestplate')
    const eyeHeight = checked.crafting.eyeHeight
    const floorDistance = eyeHeight / (eyeHeight - 0.125)
    const floorPoint = input.actor.bot.entity.position.clone().set(
        checked.location.x + (eligible.x - checked.location.x) * floorDistance,
        eligible.y,
        checked.location.z + (eligible.z - checked.location.z) * floorDistance,
    )
    const floorBlock = input.actor.bot.blockAt(floorPoint.offset(0, -0.01, 0))
    input.context.expect(floorBlock?.name === 'stone', 'Salvage ray continues to the real stone floor')
    input.actor.bot._client.write('block_place', {
        location: floorBlock.position, direction: 1, hand: 0,
        cursorX: floorPoint.x - floorBlock.position.x, cursorY: 1,
        cursorZ: floorPoint.z - floorBlock.position.z,
        insideBlock: false, sequence: 0, worldBorderHit: false,
    })
    await input.actor.bot.waitForTicks(8)
    input.actor.bot.setControlState('sneak', false)
    return { before, after: await input.snapshot() }
}

async function prepareDeconstruction(input) {
    await prepare(input, 'iron_chestplate')
    await input.actor.bot.look(-Math.PI / 2, -0.5, true)
    await input.actor.bot.waitForTicks(2)
    const chestplate = input.actor.bot.heldItem
    input.context.expect(chestplate?.name === 'iron_chestplate', 'Raw salvage input is held before the real player drop')
    await input.actor.bot.tossStack(chestplate)
    await input.equip('shears')
    await input.actor.bot.waitForTicks(15)
}

async function portable(input, stonecutter) {
    const before = await input.snapshot()
    if (stonecutter) input.actor.bot.setControlState('sneak', true)
    await input.actor.bot.look(0, Math.PI / 2, true)
    await input.actor.bot.waitForTicks(3)
    input.actor.bot.swingArm('right')
    await input.actor.bot.waitForTicks(8)
    const window = input.actor.bot.currentWindow
    const opened = window?.type ?? null
    const inventoryStart = window?.inventoryStart ?? 0
    if (window) input.actor.bot.closeWindow(window)
    input.actor.bot.setControlState('sneak', false)
    await input.actor.bot.waitForTicks(3)
    return { before, after: await input.snapshot(), opened, inventoryStart }
}

async function smartShape(input) {
    const before = await input.snapshot()
    const position = input.actor.bot.entity.position.clone().set(2, 100, 0)
    input.actor.bot.setControlState('sneak', true)
    await input.actor.bot.lookAt(position.offset(0.5, 0.5, 0.5), true)
    await input.actor.bot.waitForTicks(3)
    input.actor.bot._client.write('block_dig', { status: 0, location: position, face: 1, sequence: 0 })
    await input.actor.bot.waitForTicks(2)
    input.actor.bot._client.write('block_dig', { status: 1, location: position, face: 1, sequence: 0 })
    await input.actor.bot.waitForTicks(4)
    input.actor.bot.setControlState('sneak', false)
    return { before, after: await input.snapshot() }
}

async function placePlane(input) {
    const before = await input.snapshot()
    const source = input.actor.bot.blockAt(input.actor.bot.entity.position.clone().set(3, 101, 0))
    await input.actor.bot.lookAt(source.position.offset(0, 0.5, 0.5), true)
    await input.actor.bot.waitForTicks(3)
    input.actor.bot.setControlState('sneak', true)
    await input.actor.bot.waitForTicks(12)
    await input.actor.bot.placeBlock(source, input.actor.bot.entity.position.clone().set(-1, 0, 0))
    await input.actor.bot.waitForTicks(6)
    input.actor.bot.setControlState('sneak', false)
    return { before, after: await input.snapshot() }
}

export const craftingBehaviorCases = new Map([
    ['crafting-bulk-artisan', {
        stage: 'craft-bulk', sound: 'minecraft:block.barrel.close', soundVolume: 0.5, soundPitch: 1.4, particle: 'crit',
        prepare: input => prepare(input), trigger: input => craft(input, { output: 'oak_planks', grid: [[1, 'oak_log', 1]], shift: true }),
        verify: ({ context, unlearned, active }) => {
            context.expect(count(unlearned.after, 'oak_planks') === 4 && count(unlearned.after, 'oak_log') === 8, 'Control shift craft consumes only the grid log')
            context.expect(count(active.after, 'oak_planks') === 36 && count(active.after, 'oak_log') === 0, 'Learned bulk craft consumes eight inventory logs and creates all 36 planks')
            return report(['natural shift craft draws additional materials from inventory at exact recipe ratio'], unlearned, active)
        },
    }],
    ['crafting-thrifty-hands', {
        stage: 'craft-thrifty', sound: 'minecraft:entity.item.pickup', soundVolume: 0.4, soundPitch: 1.6, particle: 'crit',
        prepare: input => prepare(input), trigger: input => craft(input, { output: 'oak_planks', grid: [[1, 'oak_log', 20]], trials: 20 }),
        verify: ({ context, unlearned, active }) => {
            context.expect(count(unlearned.after, 'oak_planks') === 80 && count(unlearned.after, 'oak_log') === 0, 'Control crafts consume all 20 logs')
            context.expect(count(active.after, 'oak_planks') === 80 && count(active.after, 'oak_log') > 0, 'Twenty learned crafts refund at least one ingredient at 60 percent per craft')
            return report(['twenty natural crafts preserve exact output and return actual input materials; no-refund probability 0.4^20'], unlearned, active)
        },
    }],
    ['crafting-provisioner', {
        stage: 'craft-provision', sound: 'minecraft:entity.generic.eat', soundVolume: 0.4, soundPitch: 1.5, particle: 'crit',
        prepare: input => prepare(input), trigger: input => craft(input, { output: 'bread', grid: [[1, 'wheat', 12], [2, 'wheat', 12], [3, 'wheat', 12]], trials: 12 }),
        verify: ({ context, unlearned, active }) => {
            context.expect(count(unlearned.after, 'bread') === 12, 'Twelve control recipes yield twelve bread')
            context.expect(count(active.after, 'bread') > 12 && count(active.after, 'wheat') === 0, 'Learned recipes create bonus real food from the same ingredients')
            return report(['twelve natural recipes grant bonus food at 75 percent per craft; no-bonus probability 0.25^12'], unlearned, active)
        },
    }],
    ['crafting-masterwork', {
        stage: 'craft-masterwork', sound: 'minecraft:block.anvil.use', soundVolume: 0.5, soundPitch: 1.3, particle: 'crit',
        prepare: input => prepare(input), trigger: input => craft(input, { output: 'wooden_shovel', grid: [[2, 'oak_planks', 12], [5, 'stick', 12], [8, 'stick', 12]], trials: 12 }),
        verify: ({ context, unlearned, active }) => {
            const control = unlearned.after.crafting.inventory.filter(value => value.type === 'minecraft:wooden_shovel')
            const tools = active.after.crafting.inventory.filter(value => value.type === 'minecraft:wooden_shovel')
            context.expect(control.length === 12 && control.every(value => value.maxDamage === value.baseMaxDamage), 'All twelve control tools have vanilla durability')
            context.expect(tools.length === 12 && tools.some(value => value.maxDamage > value.baseMaxDamage), 'Learned crafting produces actual higher-durability tools')
            return report(['twelve natural tool crafts produce a durable Masterwork at 75 percent per craft; no-proc probability 0.25^12'], unlearned, active)
        },
    }],
    ['crafting-tinkerer', {
        stage: 'craft-tinkerer', sound: 'minecraft:block.anvil.use', soundVolume: 0.5, soundPitch: 1.1, particle: 'enchant', particleCount: 12, particleOffset: 0.4,
        prepare: input => prepare(input), trigger: input => craft(input, { output: 'iron_sword', grid: [[1, 'iron_sword', 1], [2, 'iron_sword', 1]] }),
        verify: ({ context, unlearned, active }) => {
            const plain = unlearned.after.crafting.inventory.find(value => value.type === 'minecraft:iron_sword')
            const repaired = active.after.crafting.inventory.find(value => value.type === 'minecraft:iron_sword')
            context.expect(Object.keys(plain.enchantments).length === 0, 'Vanilla grid repair removes enchantments')
            context.expect(repaired.enchantments['minecraft:sharpness'] === 3 && repaired.enchantments['minecraft:unbreaking'] === 2, 'Maximum-level Tinkerer preserves both actual input enchants')
            context.expect(repaired.damage < 200 && count(active.after, 'iron_sword') === 1, 'Repair produces one less-damaged sword')
            return report(['natural two-tool grid repair preserves both enchantments and restores durability'], unlearned, active)
        },
    }],
    ['crafting-deconstruction', {
        stage: 'craft-deconstruction', sound: 'minecraft:block.basalt.break', soundVolume: 1, soundPitch: 0.2, particle: 'item',
        prepare: prepareDeconstruction, trigger: deconstruct,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.after.crafting.drops.some(value => value.type === 'minecraft:iron_chestplate'), 'Control shears interaction leaves dropped armor intact')
            const salvage = [...active.after.crafting.inventory, ...active.after.crafting.drops].filter(value => value.type === 'minecraft:iron_ingot').reduce((total, value) => total + value.amount, 0)
            context.expect(!active.after.crafting.drops.some(value => value.type === 'minecraft:iron_chestplate') && salvage === 4, 'Learned salvage replaces one real chestplate with exactly four ingots')
            return report(['natural sneak interaction salvages dropped armor into half its recipe metal'], unlearned, active)
        },
    }],
    ['crafting-leather', {
        stage: 'craft-leather', sound: 'minecraft:item.armor.equip_leather', soundVolume: 0.6, soundPitch: 0.9, particle: 'flame',
        prepare: input => prepare(input, 'rotten_flesh'), trigger: leather,
        verify: ({ context, unlearned, active }) => {
            context.expect(count(unlearned.after, 'rotten_flesh') === 1, 'Unlearned campfire interaction preserves flesh')
            const cured = [...active.after.crafting.inventory, ...active.after.crafting.drops].filter(value => value.type === 'minecraft:leather').reduce((total, value) => total + value.amount, 0)
            context.expect(count(active.after, 'rotten_flesh') === 0 && cured === 1, 'Learned natural campfire cooking consumes flesh and yields one leather')
            return report(['natural campfire use and 100 cooking ticks convert flesh into leather'], unlearned, active)
        },
    }],
    ['crafting-backpacks', {
        stage: 'craft-backpack', sound: 'minecraft:item.bundle.insert', soundVolume: 0.8, soundPitch: 0.8, particle: 'portal',
        prepare: input => prepare(input), trigger: backpack,
        verify: ({ context, unlearned, active }) => {
            context.expect(count(unlearned.after, 'bundle') === 0 && count(unlearned.after, 'leather') === 8, 'Unlearned backpack recipe is denied without ingredient loss')
            context.expect(count(active.after, 'bundle') === 1 && active.persisted, 'Crafted backpack stores apples across close and reopen')
            context.expect(count(active.after, 'apple') === 3 && count(active.after, 'leather') === 0, 'Withdrawal returns exactly the deposited items and recipe consumes leather')
            return report(['natural backpack craft unlocks persistent deposit, reopen and withdrawal'], unlearned, active)
        },
    }],
    ['crafting-stations', {
        stage: 'craft-station', sound: 'minecraft:block.ender_chest.open', soundVolume: 1, soundPitch: 0.1, particle: 'reverse_portal',
        prepare: input => prepare(input, 'crafting_table'), trigger: input => portable(input, false),
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.opened === null, 'Control air click opens no crafting table')
            context.expect(active.opened === 'minecraft:crafting' && active.inventoryStart === 10, 'Learned held station opens full portable crafting grid')
            context.expect(active.before.food - active.after.food === 2 && count(active.after, 'crafting_table') === 1, 'Portable use pays hunger and retains station item')
            return report(['natural held-station air click opens workbench and pays configured hunger'], unlearned, active)
        },
    }],
    ['architect-stonecutter-savant', {
        stage: 'craft-stonecutter', sound: 'minecraft:block.grindstone.use', soundVolume: 0.8, soundPitch: 1.4, particle: 'enchant',
        prepare: input => prepare(input), trigger: input => portable(input, true),
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.opened === null, 'Control sneak punch opens no stonecutter')
            context.expect(active.opened === 'minecraft:stonecutter' && active.inventoryStart === 2, 'Learned empty-hand sneak punch opens portable stonecutter')
            context.expect(count(active.after, 'stonecutter') === 1, 'Portable stonecutter item remains in offhand')
            return report(['natural empty-hand sneak punch opens a server stonecutter menu while preserving carried item'], unlearned, active)
        },
    }],
    ['architect-smart-shape', {
        stage: 'craft-shape', sound: 'minecraft:item.axe.strip', soundVolume: 0.45, soundPitch: 1.8, particle: 'crit',
        prepare: input => prepare(input), trigger: smartShape,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.after.crafting.targetData === unlearned.before.crafting.targetData, 'Control sneak punch leaves log orientation unchanged')
            context.expect(active.after.crafting.targetData.startsWith('minecraft:oak_log[') && active.after.crafting.targetData !== active.before.crafting.targetData, 'Learned sneak punch rotates actual log axis without breaking it')
            return report(['natural empty-hand sneak block click rotates placed block data'], unlearned, active)
        },
    }],
    ['architect-placement', {
        stage: 'craft-placement', sound: 'minecraft:block.amethyst_block.place', soundVolume: 0.5, soundPitch: 1.65,
        particle: 'dust', particleColor: 0x55FFFF, particleCount: 1, particleOffset: 0, particleY: 101.5,
        prepare: input => prepare(input, 'oak_planks'), trigger: placePlane,
        verify: ({ context, unlearned, active }) => {
            context.expect(unlearned.after.crafting.placement.filter(value => value === 'minecraft:oak_planks').length === 1, 'Control sneak placement creates one block')
            context.expect(active.after.crafting.placement.every(value => value === 'minecraft:oak_planks'), 'Learned sneak placement copies the nine-block facing plane')
            context.expect(count(active.before, 'oak_planks') - count(active.after, 'oak_planks') === 9, 'Plane placement consumes exactly nine blocks')
            return report(['natural sneak placement fills nine adjacent positions and consumes the exact block count'], unlearned, active)
        },
    }],
])
