function tool(state, name = 'diamond_pickaxe') {
    return state.earth.tools.find(item => item.type === `minecraft:${name}`)
}
function count(actor, name) {
    return actor.bot.inventory.items().filter(item => item.name === name).reduce((sum, item) => sum + item.count, 0)
}
async function preparePick(input) {
    await input.equip('diamond_pickaxe')
}
const target = actor => actor.bot.blockAt(actor.bot.entity.position.offset(3, 0, 0).floored())

export function observeOreMarkers(actor, opponent, position) {
    const displays = new Set()
    const privateDisplays = new Set()
    const matches = entity => entity.name === 'block_display' && Boolean(entity.metadata?.[0] & 0x40)
        && ['x', 'y', 'z'].every(axis => Math.abs(entity.position[axis] - position[axis]) < 0.00001)
    const onActor = entity => { if (matches(entity)) displays.add(entity.id) }
    const onOpponent = entity => { if (matches(entity)) privateDisplays.add(entity.id) }
    for (const event of ['entitySpawn', 'entityUpdate']) {
        actor.bot.on(event, onActor)
        opponent.bot.on(event, onOpponent)
    }
    return { displays, privateDisplays, stop() {
        for (const event of ['entitySpawn', 'entityUpdate']) {
            actor.bot.off(event, onActor)
            opponent.bot.off(event, onOpponent)
        }
    } }
}

export const earthBehaviorCases = new Map([
    ['pickaxe-chisel', {
        stage: 'earth-chisel', negativeWindowTicks: 1, prepare: preparePick,
        sound: 'minecraft:block.metal.hit', soundVolume: 1.25, soundPitch: 1.7, particle: 'electric_spark', particleCount: 4,
        async trigger({ actor, snapshot }) {
            const before = tool(await snapshot())
            await actor.bot.activateBlock(target(actor))
            await actor.bot.waitForTicks(8)
            return { damage: tool(await snapshot()).damage - before.damage }
        },
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.damage === 0 && active.damage === 1, 'Only learned ore chiseling charges its exact one-point tool durability cost')
            return { assertions: ['unlearned ore interaction leaves the tool unchanged', 'learned natural ore interaction runs chiseling and charges one durability point'], measurements: { unlearned, active } }
        },
    }],
    ['pickaxe-gem-polish', {
        stage: 'earth-trophy', negativeWindowTicks: 1, prepare: preparePick,
        sound: 'minecraft:entity.experience_orb.pickup', soundVolume: 0.5, soundPitch: 1.4, particle: 'end_rod', particleCount: 1,
        async trigger({ actor, snapshot }) {
            await actor.bot.dig(target(actor), true)
            await actor.bot.look(-Math.PI / 2, 0, true)
            actor.bot.setControlState('forward', true)
            await actor.bot.waitForTicks(14)
            actor.bot.setControlState('forward', false)
            await actor.bot.waitForTicks(25)
            return { xp: (await snapshot()).earth.vanillaXp, trophies: count(actor, 'dragon_head') }
        },
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.xp === 0 && unlearned.trophies === 1, 'Unlearned natural trophy mining yields the head without bonus XP')
            context.expect(active.xp > 0 && active.xp <= 24 && active.trophies === 1, 'Learned natural trophy mining yields bounded vanilla XP without duplicating the head')
            return { assertions: ['unlearned trophy grants no vanilla XP', 'learned trophy grants positive bounded vanilla XP', 'both actual mining trials retain exactly one trophy'], measurements: { unlearned, active } }
        },
    }],
    ['excavation-burrow', {
        stage: 'earth-burrow', negativeWindowTicks: 1,
        prepare: input => input.equip('diamond_shovel'),
        sound: 'minecraft:block.rooted_dirt.break', soundVolume: 0.8, soundPitch: 0.6, particle: 'cloud', particleCount: 1,
        async trigger({ actor, snapshot }) {
            const origin = actor.bot.entity.position.clone()
            const before = await snapshot()
            actor.bot.setControlState('sneak', true)
            await actor.bot.waitForTicks(3)
            await actor.bot.activateBlock(actor.bot.blockAt(origin.offset(0, -1, 0).floored()))
            actor.bot.setControlState('sneak', false)
            await actor.bot.waitForTicks(55)
            const after = await snapshot()
            return { descent: origin.y - actor.bot.entity.position.y, foodCost: before.food - after.food, damage: tool(after, 'diamond_shovel').damage - tool(before, 'diamond_shovel').damage, floor: actor.bot.blockAt(origin.offset(0, -7, 0).floored()).name }
        },
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.descent < 1 && unlearned.damage <= 1, 'Unlearned shovel use does not excavate a vertical shaft')
            context.expect(active.descent >= 5 && active.damage === 6 && active.foodCost === 1 && active.floor === 'stone', 'Learned burrow removes six soft blocks, charges exact costs and stops at the solid floor')
            return { assertions: ['unlearned use does not burrow', 'learned use naturally excavates six dirt blocks and descends', 'exact durability and hunger costs are charged', 'solid floor remains intact'], measurements: { unlearned, active } }
        },
    }],
])

for (const [name, stage, spelunker] of [['pickaxe-quarry-sense', 'earth-quarry', false], ['excavation-spelunker', 'earth-spelunker', true]]) {
    earthBehaviorCases.set(name, {
        stage, negativeWindowTicks: 1,
        sound: spelunker ? 'minecraft:block.note_block.bass' : 'minecraft:block.respawn_anchor.charge', soundVolume: spelunker ? 0.5 : 0.9, soundPitch: spelunker ? 1 : 1.6,
        particle: spelunker ? 'smoke' : 'enchant', particleCount: spelunker ? 2 : 14,
        async prepare(input) {
            await input.equip(spelunker ? 'glow_berries' : 'diamond_pickaxe')
            if (spelunker) await input.equip('diamond_ore', input.actor, 'off-hand')
        },
        async trigger({ actor, opponent, snapshot }) {
            const before = await snapshot()
            const berries = count(actor, 'glow_berries')
            actor.bot.setControlState('sneak', true)
            await actor.bot.waitForTicks(3)
            if (!spelunker) await actor.bot.activateBlock(target(actor))
            await actor.bot.waitForTicks(50)
            const displays = Object.values(actor.bot.entities).filter(entity => entity.name === 'block_display').map(entity => ({ id: entity.id, glowing: Boolean(entity.metadata[0] & 0x40), distance: entity.position.distanceTo(actor.bot.entity.position.offset(3, 0, 1).floored()) }))
            const other = Object.values(opponent.bot.entities).filter(entity => entity.name === 'block_display').map(entity => entity.id)
            if (spelunker) {
                actor.bot.setControlState('sneak', false)
                await actor.bot.waitForTicks(3)
                actor.bot.setControlState('sneak', true)
                await actor.bot.waitForTicks(3)
            }
            actor.bot.setControlState('sneak', false)
            const after = await snapshot()
            await actor.bot.waitForTicks(220)
            return { displays, other, consumedBerries: berries - count(actor, 'glow_berries'), maxDamageCost: tool(before).maxDamage - tool(after).maxDamage, durabilityCost: tool(after).damage - tool(before).damage, remaining: displays.filter(display => actor.bot.entities[display.id]).length }
        },
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.displays.length === 0 && unlearned.consumedBerries === 0 && unlearned.maxDamageCost === 0 && unlearned.durabilityCost === 0, 'Unlearned scan produces no private ore markers or costs')
            context.expect(active.displays.some(display => display.glowing && display.distance < 0.1) && active.other.length === 0, 'Learned scan creates a glowing marker at the real ore visible only to its owner')
            context.expect(spelunker ? active.consumedBerries === 1 : active.durabilityCost === 2 && active.maxDamageCost === 0, 'Learned scan charges the correct berry or ordinary durability resource')
            context.expect(active.remaining === 0, 'Private ore markers expire naturally')
            return { assertions: ['unlearned scan has no markers or costs', 'learned marker matches the nearby ore location and glows', 'observer does not receive the private marker', 'successful scan charges its resource cost', 'markers expire naturally'], measurements: { unlearned, active } }
        },
    })
}

earthBehaviorCases.set('pickaxe-repair-rhythm', {
    stage: 'earth-repair', negativeWindowTicks: 1, prepare: preparePick,
    sound: 'minecraft:block.amethyst_block.chime', soundVolume: 0.25, soundPitch: 1.95, particle: 'wax_on', particleCount: 2,
    async trigger({ actor, snapshot }) {
        const before = tool(await snapshot()).damage
        for (let y = 102; y >= 100; y--) {
            for (let z = -2; z <= 2; z++) {
                const block = actor.bot.blockAt(actor.bot.entity.position.clone().set(3, y, z))
                await actor.bot.dig(block, true)
                await actor.bot.waitForTicks(2)
            }
        }
        return { damageAdded: tool(await snapshot()).damage - before, broken: 15 }
    },
    async verify({ context, unlearned, active }) {
        context.expect(unlearned.damageAdded === 15, 'Unlearned mining charges one durability per actual stone block')
        context.expect(active.damageAdded < unlearned.damageAdded, 'Learned natural mining repairs measurable tool durability')
        return { assertions: ['unlearned fifteen-block mining costs fifteen durability', 'learned mining produces actual repair procs and retains more durability'], measurements: { unlearned, active, trials: 15, noRepairProbabilityUpperBound: 0.00049 } }
    },
})

earthBehaviorCases.set('excavation-omnitool', {
    stage: 'earth-omni', negativeWindowTicks: 1, prepare: preparePick,
    sound: 'minecraft:item.armor.equip_elytra', soundVolume: 1, soundPitch: 0.77, particle: 'wax_on', particleCount: 10,
    async trigger({ actor, context, snapshot }) {
        const pick = actor.bot.inventory.items().find(item => item.name === 'diamond_pickaxe')
        const axe = actor.bot.inventory.items().find(item => item.name === 'diamond_axe')
        context.expect(Boolean(pick && axe), 'Tool merge begins with a separate ordinary pickaxe and axe')
        await actor.bot.clickWindow(pick.slot, 0, 0)
        await actor.bot.waitForTicks(3)
        await actor.bot.clickWindow(axe.slot, 0, 1)
        await actor.bot.waitForTicks(4)
        const empty = actor.bot.inventory.slots.findIndex((item, slot) => slot >= 9 && slot <= 44 && !item)
        await actor.bot.clickWindow(empty, 0, 0)
        await actor.bot.waitForTicks(3)
        const merged = await snapshot()
        const pickItem = actor.bot.inventory.items().find(item => item.name === 'diamond_pickaxe')
        context.expect(Boolean(pickItem), 'Merged primary pickaxe remains available')
        await actor.bot.equip(pickItem, 'hand')
        const block = target(actor)
        await actor.bot.lookAt(block.position.offset(0.5, 0.5, 0.5), true)
        await actor.bot.waitForTicks(3)
        actor.bot._client.write('block_dig', { status: 0, location: block.position, face: 4, sequence: 0 })
        await actor.bot.waitForTicks(5)
        actor.bot._client.write('block_dig', { status: 1, location: block.position, face: 4, sequence: 1 })
        return { merged: merged.earth.tools, held: actor.bot.heldItem?.name, after: (await snapshot()).earth.tools }
    },
    async verify({ context, unlearned, active }) {
        context.expect(unlearned.merged.length === 2 && unlearned.merged.every(item => item.components === 1) && unlearned.held === 'diamond_pickaxe', 'Unlearned inventory action does not merge or automatically switch tools')
        context.expect(active.merged.length === 1 && active.merged[0].components === 2, 'Learned inventory action merges exactly two real tools')
        context.expect(active.held === 'diamond_axe' && active.after[0].components === 2, 'Natural log damage switches to the merged axe while preserving both components')
        return { assertions: ['unlearned tools remain separate and do not switch', 'natural learned inventory gesture merges both tools', 'natural log mining selects the axe without losing components'], measurements: { unlearned, active } }
    },
})

for (const [name, stage, stat, material, probability] of [
    ['excavation-grave-digger', 'earth-graves', 'excavation.grave-digger.bones-unearthed', 'dirt', 0.043],
    ['excavation-treasure-hunter', 'earth-treasure', 'excavation.treasure-hunter.treasures-found', 'sand', 0.06],
    ['excavation-seismic-ping', 'earth-seismic', 'excavation.seismic-ping.pings-triggered', 'dirt', 0.51],
]) earthBehaviorCases.set(name, {
    stage, negativeWindowTicks: 1, prepare: input => input.equip('diamond_shovel'),
    ...(stage === 'earth-graves' ? { sound: 'minecraft:block.bone_block.break', soundVolume: 0.6, soundPitch: 1, particle: 'ash', particleCount: 8 }
        : stage === 'earth-treasure' ? { sound: 'minecraft:item.brush.brushing.sand.complete', soundVolume: 0.8, soundPitch: 1.2, particle: 'wax_on' }
            : { sound: 'minecraft:block.amethyst_block.chime', soundVolume: 0.9, soundPitchRange: [0.45, 1.95] }),
    async trigger({ actor, opponent, snapshot, context }) {
        const initial = await snapshot()
        const learned = initial.learned[name] > 0
        const maxTrials = learned && stage !== 'earth-seismic' ? 324 : 36
        let trials = 0
        const observer = observeOreMarkers(actor, opponent, { x: 4, y: 100, z: 1 })
        const { displays, privateDisplays } = observer
        let after = initial
        try {
            while (trials < maxTrials && (after.stats[stat] ?? 0) === (initial.stats[stat] ?? 0)) {
                if (trials > 0) {
                    await context.command(`/adaptqa earth-refill ${material}`, new RegExp(`^ADAPT_QA EARTH-REFILL ${material}$`), 5000)
                    await actor.bot.waitForTicks(3)
                }
                for (let y = 102; y >= 100; y--) {
                    for (let z = 0; z <= 2; z++) {
                        const block = actor.bot.blockAt(actor.bot.entity.position.clone().set(3, y, z))
                        context.expect(block?.name === material, 'Mining trial begins with a real eligible soil block')
                        await actor.bot.dig(block, true)
                        await actor.bot.waitForTicks(2)
                        trials++
                    }
                }
                await actor.bot.waitForTicks(8)
                after = await snapshot()
            }
            await actor.bot.waitForTicks(50)
            return { trials, procs: (after.stats[stat] ?? 0) - (initial.stats[stat] ?? 0), drops: after.earth.drops, displays: [...displays], privateDisplays: [...privateDisplays], remainingDisplays: [...displays].filter(id => actor.bot.entities[id]), noProcProbability: (1 - probability) ** maxTrials }
        } finally {
            observer.stop()
        }
    },
    async verify({ context, unlearned, active }) {
        context.expect(unlearned.procs === 0 && unlearned.displays.length === 0 && unlearned.drops.every(drop => drop.type === `minecraft:${material}`), 'Unlearned soil mining produces only vanilla soil drops and no ore marker')
        context.expect(active.procs > 0, 'Bounded natural learned mining produces a real adaptation proc')
        if (stage === 'earth-seismic') {
            context.expect(active.displays.length > 0 && active.privateDisplays.length === 0 && active.remainingDisplays.length === 0, 'Seismic marker is private and expires naturally')
        } else context.expect(active.drops.some(drop => drop.type !== `minecraft:${material}`), 'Actual dropped items include non-vanilla soil loot')
        return { assertions: ['unlearned natural mining produces no adaptation proc', 'bounded learned mining produces a measured proc', stage === 'earth-seismic' ? 'real private ore displays expire naturally' : 'actual world drops include the adaptation reward'], measurements: { unlearned, active, probabilityPerTrial: probability } }
    },
})
