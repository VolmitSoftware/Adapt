import { randomBytes } from 'node:crypto'
import { readFile, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import { fixtureJson } from './fixture-json.mjs'

const MODES = ['crystals', 'budget', 'recycling', 'mining', 'combat', 'alternating', 'enchanting', 'orbs', 'unity', 'furnace']
const RETAINED_FILE = '.adapt-xp-integrity-retained.json'

export function measureProgress(before, after, startedAt, actions) {
    if (before.player !== after.player || before.operator || after.operator || after.now <= startedAt || actions < 1) {
        throw new Error('Progress measurement requires one ordinary player, elapsed server time and real actions')
    }
    const elapsedMillis = after.now - startedAt
    const skills = Object.entries(after.lines).map(([skill, line]) => ({
        skill,
        beforeXp: before.lines[skill]?.totalXp ?? 0,
        afterXp: line.totalXp,
        gained: line.totalXp - (before.lines[skill]?.totalXp ?? 0),
        beforeLevel: before.lines[skill]?.level ?? 0,
        afterLevel: line.level,
    })).filter(line => line.gained > 0)
    return { elapsedMillis, actions, skills, masterXpGained: after.masterXp - before.masterXp,
        masterLevelsGained: after.masterLevel - before.masterLevel }
}

export function progressionRequirements(catalog) {
    const enabled = catalog.skills.filter(skill => skill.enabled)
    const skills = enabled.map(skill => {
        const adaptations = skill.adaptations.filter(adaptation => adaptation.enabled)
        const knowledge = adaptations.reduce((sum, adaptation) => sum + adaptation.knowledge, 0)
        let level = 0
        let earnedKnowledge = 0
        while (earnedKnowledge < knowledge && level < catalog.maximumSkillLevel) {
            earnedKnowledge += Math.floor(level / 13) + 1
            level++
        }
        return { skill: skill.skill, adaptationCount: adaptations.length,
            adaptationLevels: adaptations.reduce((sum, adaptation) => sum + adaptation.maximumLevel, 0),
            minimumMasterLevelForPower: Math.ceil(adaptations.reduce((sum, adaptation) => sum + adaptation.maximumLevel, 0) / catalog.powerPerLevel),
            knowledge, minimumSkillLevelForKnowledge: level, knowledgeFitsBeforeCap: earnedKnowledge >= knowledge,
            prerequisites: adaptations.filter(adaptation => adaptation.requirements.length).map(adaptation => ({ id: adaptation.id, requires: adaptation.requirements })) }
    })
    const requiredPower = skills.reduce((sum, skill) => sum + skill.adaptationLevels, 0)
    return { maximumSkillLevel: catalog.maximumSkillLevel, maximumSkillXp: catalog.maximumSkillXp,
        enabledSkills: enabled.length, allSkillsAtCapXp: enabled.length * catalog.maximumSkillXp,
        totalAdaptationLevels: requiredPower,
        masterLevelForSimultaneousMaximumAdaptations: Math.ceil(requiredPower / catalog.powerPerLevel),
        simultaneousMaximumFitsMasterCap: requiredPower <= Math.floor(catalog.maximumSkillLevel * catalog.powerPerLevel),
        skills }
}

function totals(snapshot, skill) {
    return (snapshot.earnings ?? []).filter(source => source.source.startsWith(`${skill}/`))
        .reduce((sum, source) => ({ requested: sum.requested + source.requested, eligible: sum.eligible + source.eligible,
            credited: sum.credited + source.credited, awards: sum.awards + source.awards }),
        { requested: 0, eligible: 0, credited: 0, awards: 0 })
}

function sampled(snapshot) {
    return { now: snapshot.now, masterXp: snapshot.masterXp, masterLevel: snapshot.masterLevel,
        lines: Object.fromEntries(Object.entries(snapshot.lines).map(([skill, line]) => [skill, {
            totalXp: line.totalXp, xp: line.xp, level: line.level, multiplier: line.multiplier, monotony: line.monotony,
        }])) }
}

function assertRetained(context, before, after, reason) {
    for (const [bucket, rate] of [['player', 'playerXpPerMinute'], ['absolute', 'absoluteXpPerMinute']]) {
        const previous = before.xpEarnings[bucket]
        const current = after.xpEarnings[bucket]
        const refill = Math.max(0, current.updatedAt - previous.updatedAt) * before.config.xpEarningLimits[rate] / 60000
        context.expect(current.used >= Math.max(0, previous.used - refill) - 0.000001,
            `${bucket} allowance does not reset during ${reason}`)
    }
    for (const [skill, line] of Object.entries(before.lines)) {
        context.expect((after.lines[skill]?.totalXp ?? 0) >= line.totalXp - 0.000001, `${skill} XP survives ${reason}`)
        const previous = totals(before, skill)
        const current = totals(after, skill)
        context.expect(current.credited >= previous.credited - 0.000001 && current.awards >= previous.awards,
            `${skill} earned-XP accounting survives ${reason}`)
        const refill = Math.max(0, (after.lines[skill]?.budget.updatedAt ?? line.budget.updatedAt) - line.budget.updatedAt)
            * before.config.xpEarningLimits.skillXpPerMinute / 60000
        context.expect((after.lines[skill]?.budget.skillUsed ?? 0) >= Math.max(0, line.budget.skillUsed - refill) - 0.000001,
            `${skill} allowance does not reset during ${reason}`)
    }
    const cells = before.xpNovelty?.spatialCells ?? {}
    context.expect(Object.keys(cells).length > 0, 'Persistence trial has actual spatial repetition history')
    for (const [key, cell] of Object.entries(cells)) {
        if (after.now - cell.touch < before.config.xpIntegrity.spatialCellTtlMillis) {
            context.expect(after.xpNovelty?.spatialCells?.[key]?.count >= cell.count,
                `Unexpired spatial repetition survives ${reason}`)
        }
    }
}

export default {
    name: 'adapt-xp-integrity',
    description: 'Measure ordinary gameplay progression and assert repetition controls, bounded boosts and persisted earnings on Paper or Folia.',
    async run(context) {
        context.bot.physicsEnabled = false
        const selected = context.options.command || 'all'
        const report = context.report.xpIntegrity = { workload: selected, trials: [],
            methodology: 'Fresh ordinary identities; supplied items, ore walls, mobs and furnace fuel; no granted Adapt XP. Timings exclude resource acquisition. Unity is an explicitly seeded adaptation comparison.' }
        const fixture = (actor, action) => fixtureJson(context,
            `/xpqa ${actor.bot.username} ${action}`, `XP ${action.split(' ')[0]}`, 20000)
        if (selected === 'verify-restart') {
            const retained = JSON.parse(await readFile(join(context.server.directory, RETAINED_FILE), 'utf8'))
            const actor = await context.connectActor(retained.player)
            try {
                const after = await fixture(actor, `resume ${retained.x} ${retained.mode}`)
                context.expect(after.processId !== retained.processId, 'Persistence verification runs in a different server process')
                for (const setting of ['skillXpPerMinute', 'playerXpPerMinute', 'absoluteXpPerMinute']) {
                    context.expect(after.config.xpEarningLimits[setting] === retained.config.xpEarningLimits[setting],
                        `${setting} stays fixed across the persistence trial`)
                }
                assertRetained(context, retained, after, 'process restart')
                report.restart = { before: retained, after }
            } finally {
                await fixture(actor, 'cleanup')
            }
            return
        }
        const modes = selected === 'all' ? MODES.filter(mode => mode !== 'budget') : selected.split(',')
        if (modes.some(mode => !MODES.includes(mode))) throw new Error('Unknown XP workload selection')
        let retained
        for (const mode of modes) {
            let actor = await context.connectActor(`AQAx${mode.slice(0, 3)}${randomBytes(3).toString('hex')}`)
            let prepared = false
            const evidence = { mode, samples: [], suppliedAdaptations: mode === 'unity' ? ['discovery-unity:7'] : [] }
            if (mode === 'budget') evidence.configurationPurpose = 'One-XP-per-minute refill preserves material allowance debt through process restart; this is not a default-rate progression benchmark.'
            report.trials.push(evidence)
            const snapshot = () => fixture(actor, 'snapshot')
            const sample = async () => {
                const state = await snapshot()
                evidence.samples.push(sampled(state))
                return state
            }
            const equip = async name => {
                await context.waitUntil(() => actor.bot.inventory.items().some(item => item.name === name),
                    { label: `${name} fixture supply`, timeoutMs: 5000 })
                await actor.bot.equip(actor.bot.inventory.items().find(item => item.name === name), 'hand')
            }
            let anchor
            const position = (x, y, z) => actor.bot.entity.position.clone().set(x, y, z)
            const table = async () => {
                await context.waitUntil(() => actor.bot.blockAt(position(anchor + 7, 100, 264))?.name === 'crafting_table',
                    { label: 'Crafting table is visible', timeoutMs: 5000 })
                return actor.bot.blockAt(position(anchor + 7, 100, 264))
            }
            const craft = async (item, count = 1) => {
                const craftingTable = await table()
                const recipe = actor.bot.recipesFor(actor.bot.registry.itemsByName[item].id, null, 1, craftingTable)[0]
                context.expect(Boolean(recipe), `${item} has a recipe using the actual inventory`)
                const inventory = () => actor.bot.inventory.items().map(entry => ({ item: entry.name, count: entry.count }))
                const attempt = { item, count, before: inventory(), recipe }
                evidence.craftAttempts ??= []
                evidence.craftAttempts.push(attempt)
                try {
                    for (let index = 0; index < count; index++) {
                        const window = await actor.bot.openBlock(craftingTable)
                        const ingredients = recipe.inShape ? recipe.inShape.flatMap((row, y) => row.map((ingredient, x) => ({ slot: 1 + x + y * 3, ingredient })))
                            : recipe.ingredients.map((ingredient, slot) => ({ slot: slot + 1, ingredient }))
                        for (const { slot, ingredient } of ingredients.filter(entry => entry.ingredient.id !== -1)) {
                            const source = window.findInventoryItem(ingredient.id, ingredient.metadata)
                            context.expect(Boolean(source), 'The real inventory contains the recipe ingredient')
                            const sourceSlot = source.slot
                            await click(window, sourceSlot)
                            await click(window, slot, 1)
                            if (window.selectedItem) await click(window, sourceSlot)
                        }
                        await context.waitUntil(() => window.slots[0]?.name === item,
                            { label: `The server publishes the ${item} recipe result`, timeoutMs: 5000 })
                        await click(window, 0, 0, 1)
                        actor.bot.closeWindow(window)
                        attempt.completed = index + 1
                    }
                } finally {
                    attempt.after = inventory()
                }
            }
            const click = async (window, slot, button = 0, mode = 0) => {
                const pending = actor.bot.clickWindow(slot, button, mode)
                await actor.bot.waitForTicks(2)
                await actor.bot._syncWindow(window)
                await pending
            }
            const crystalBatch = async () => {
                const window = await actor.bot.openBlock(await table())
                const existing = window.items().filter(item => item.name === 'end_crystal').reduce((sum, item) => sum + item.count, 0)
                for (const [slot, name] of [[1, 'glass'], [2, 'glass'], [3, 'glass'], [4, 'glass'], [5, 'ender_eye'],
                    [6, 'glass'], [7, 'glass'], [8, 'ghast_tear'], [9, 'glass']]) {
                    const source = window.findInventoryItem(actor.bot.registry.itemsByName[name].id, null)
                    context.expect(source?.count === 64, `The ${name} recipe stack contains 64 actual items`)
                    await click(window, source.slot)
                    await click(window, slot)
                }
                await context.waitUntil(() => window.slots[0]?.name === 'end_crystal',
                    { label: 'The server publishes an end crystal recipe result', timeoutMs: 5000 })
                await click(window, 0, 0, 1)
                const amount = window.items().filter(item => item.name === 'end_crystal').reduce((sum, item) => sum + item.count, 0) - existing
                context.expect(amount === 64, 'The real shift-click produces 64 end crystals', { amount })
                actor.bot.closeWindow(window)
                return amount
            }
            const supplyCrystals = async () => {
                for (const [material, count] of [['glass', 448], ['ender_eye', 64], ['ghast_tear', 64]]) {
                    context.bot.chat(`/give ${actor.bot.username} minecraft:${material} ${count}`)
                    await context.waitUntil(() => actor.bot.inventory.items().filter(item => item.name === material)
                        .reduce((sum, item) => sum + item.count, 0) >= count,
                    { label: `Supplied ${material} reaches the survival inventory`, timeoutMs: 5000 })
                }
            }
            const mine = async (y, z) => {
                await equip('diamond_pickaxe')
                const location = position(anchor + 11, y, z)
                await context.waitUntil(() => actor.bot.blockAt(location)?.name === 'diamond_ore',
                    { label: 'Supplied diamond ore reaches the client', timeoutMs: 5000 })
                await actor.bot.dig(actor.bot.blockAt(location))
                await context.waitUntil(() => actor.bot.blockAt(location)?.name === 'air',
                    { label: 'Player breaks diamond ore', timeoutMs: 5000 })
            }
            try {
                await context.step(`${mode}: prepare an ordinary player without granted progression`, async () => {
                    evidence.initial = await fixture(actor, `prepare ${['recycling', 'crystals', 'budget'].includes(mode) ? 'crafting' : mode}`)
                    prepared = true
                    anchor = evidence.initial.x
                    context.expect(!evidence.initial.operator, 'Measured actor is not an operator')
                    const learned = Object.values(evidence.initial.lines).flatMap(line => Object.entries(line.adaptations)
                        .filter(([, adaptation]) => adaptation.level > 0).map(([id]) => id))
                    context.expect(mode === 'unity' ? learned.includes('discovery-unity') : learned.length === 0,
                        'The trial records its exact seeded-adaptation condition', learned)
                    if (!report.requirements) report.requirements = progressionRequirements(await fixture(actor, 'catalog'))
                    if (mode === 'budget') context.expect(evidence.initial.config.xpEarningLimits.skillXpPerMinute === 1
                        && evidence.initial.config.xpEarningLimits.playerXpPerMinute === 1
                        && evidence.initial.config.xpEarningLimits.absoluteXpPerMinute === 1,
                    'Persistence stress uses an explicitly separate one-XP-per-minute refill configuration')
                    if (mode === 'crystals' || mode === 'budget') {
                        await supplyCrystals()
                    }
                    context.bot.chat('/gamemode spectator @s')
                    context.bot.chat(`/tp @s ${anchor + 8.5} 105 264.5`)
                    await context.waitUntil(() => Math.abs(context.bot.entity.position.x - anchor - 8.5) < 0.1,
                        { label: 'Viewer observer reaches the active trial arena', timeoutMs: 5000 })
                    await actor.bot.waitForTicks(4)
                })
                const before = await sample()
                evidence.timingOrigin = { now: before.now, definition: 'Server snapshot immediately before the first productive action; supplied resources are already present. First level-up times are the first snapshots observing committed levels.' }
                let actions = 0
                await context.step(`${mode}: exercise real player events and record elapsed progression`, async () => {
                    if (mode === 'crystals' || mode === 'budget') {
                        for (let batch = 0; batch < (mode === 'budget' ? 1 : 8); batch++) {
                            if (batch > 0) await supplyCrystals()
                            actions += await crystalBatch()
                            const crafted = await sample()
                            if ((crafted.lines.crafting?.totalXp ?? 0) >= 1300) break
                            await actor.bot.waitForTicks(61)
                        }
                    } else if (mode === 'recycling') {
                        for (let batch = 0; batch < 4; batch++) {
                            await craft('diamond')
                            await craft('diamond_block')
                            actions += 2
                            await sample()
                        }
                        const recycled = await snapshot()
                        context.expect(recycled.craftEvents > before.craftEvents, 'Real reversible crafts occurred')
                        context.expect((recycled.lines.crafting?.totalXp ?? 0) === (before.lines.crafting?.totalXp ?? 0),
                            'Resource-neutral block conversion produces no Crafting XP')
                    } else if (mode === 'mining' || mode === 'alternating') {
                        for (let batch = 0; batch < 2; batch++) {
                            if (batch > 0) await fixture(actor, 'ore DIAMOND_ORE')
                            for (let z = 263; z <= 265; z++) {
                                await mine(100, z)
                                actions++
                                if (mode === 'alternating') {
                                    const mined = await snapshot()
                                    await craft('oak_planks')
                                    const switched = await snapshot()
                                    const pressure = mined.lines.pickaxe.skillPressure.pressure
                                    const elapsed = Math.max(0, switched.lines.pickaxe.skillPressure.lastDecayAt
                                        - mined.lines.pickaxe.skillPressure.lastDecayAt)
                                    const recovery = mined.config.farmPrevention.skillRecoveryMillis
                                    const minimum = pressure * Math.exp(-elapsed / recovery)
                                    context.expect(switched.lines.pickaxe.skillPressure.pressure >= minimum - 0.000001,
                                        'A cheap crafting switch does not erase mining pressure', { pressure, minimum, after: switched.lines.pickaxe.skillPressure.pressure })
                                    actions++
                                }
                            }
                            await sample()
                        }
                    } else if (mode === 'combat') {
                        await equip('diamond_sword')
                        for (let index = 0; index < 20; index++) {
                            if (index > 0) await fixture(actor, 'cow')
                            await context.waitUntil(() => Object.values(actor.bot.entities).some(entity => entity.name === 'cow'),
                                { label: 'Combat target reaches the client', timeoutMs: 5000 })
                            const cow = actor.bot.nearestEntity(entity => entity.name === 'cow')
                            for (let hit = 0; hit < 8 && actor.bot.entities[cow.id]; hit++) {
                                await context.waitUntil(async () => {
                                    if (!actor.bot.entities[cow.id]) return true
                                    await actor.bot.lookAt(cow.position.offset(0, 0.7, 0), true)
                                    const reachable = actor.bot.entity.position.distanceTo(cow.position) <= 2.5
                                    actor.bot.setControlState('forward', !reachable)
                                    return reachable
                                }, { label: 'The player follows the target within melee reach', timeoutMs: 5000, intervalMs: 100 })
                                actor.bot.clearControlStates()
                                if (!actor.bot.entities[cow.id]) break
                                await actor.bot.lookAt(cow.position.offset(0, 0.7, 0), true)
                                actor.bot.attack(cow)
                                actions++
                                await actor.bot.waitForTicks(13)
                            }
                            context.expect(!actor.bot.entities[cow.id], 'The player kills the supplied cow')
                            await sample()
                        }
                    } else if (mode === 'enchanting') {
                        await context.waitUntil(() => actor.bot.blockAt(position(anchor + 7, 100, 264))?.name === 'enchanting_table',
                            { label: 'Enchanting table is visible', timeoutMs: 5000 })
                        for (let index = 0; index < 20; index++) {
                            if (actor.bot.inventory.items().filter(item => item.name === 'lapis_lazuli')
                                .reduce((sum, item) => sum + item.count, 0) < 3) {
                                context.bot.chat(`/give ${actor.bot.username} minecraft:lapis_lazuli 64`)
                                await context.waitUntil(() => actor.bot.inventory.items().some(item => item.name === 'lapis_lazuli' && item.count >= 3),
                                    { label: 'Supplied enchanting lapis reaches the inventory', timeoutMs: 5000 })
                            }
                            const window = await actor.bot.openEnchantmentTable(actor.bot.blockAt(position(anchor + 7, 100, 264)))
                            await window.putTargetItem(window.items().find(item => item.name === 'book'))
                            await window.putLapis(window.items().find(item => item.name === 'lapis_lazuli' && item.count >= 3))
                            await context.waitUntil(() => window.enchantments[2]?.level === 30,
                                { label: 'The actual table offers a thirty-level enchantment', timeoutMs: 5000 })
                            const enchanted = await window.enchant(2)
                            context.expect(enchanted?.name === 'enchanted_book', 'The client receives an enchanted book')
                            await window.takeTargetItem()
                            window.close()
                            actions++
                            const enchantedState = await sample()
                            context.bot.chat(`/clear ${actor.bot.username} minecraft:enchanted_book`)
                            await context.waitUntil(() => !actor.bot.inventory.items().some(item => item.name === 'enchanted_book'),
                                { label: 'Completed enchanted books leave the benchmark inventory', timeoutMs: 5000 })
                            if ((enchantedState.lines.enchanting?.totalXp ?? 0) >= 1300) break
                            await actor.bot.waitForTicks(106)
                        }
                    } else if (mode === 'orbs' || mode === 'unity') {
                        await craft('oak_planks')
                        const first = await snapshot()
                        await fixture(actor, 'orbs 80')
                        await context.waitUntil(async () => {
                            const state = await sample()
                            evidence.boostSnapshots ??= []
                            evidence.boostSnapshots.push({ now: state.now, lines: Object.fromEntries(Object.entries(state.lines)
                                .map(([skill, line]) => [skill, line.boosts])) })
                            return state.orbEvents >= first.orbEvents + 80
                        }, { label: 'Eighty separately spawned vanilla orbs are actually collected', timeoutMs: 30000, intervalMs: 1000 })
                        actions = 81
                        const state = await sample()
                        context.expect(evidence.boostSnapshots.some(entry => Object.values(entry.lines).some(boosts =>
                            boosts.some(boost => boost.source === 'vanilla-xp' && boost.goodFor >= entry.now))),
                        'Actual collected orbs produce at least one active vanilla XP boost')
                        context.expect(state.orbPoints >= first.orbPoints + 80, 'The real vanilla XP balance observes the orb stream')
                        for (const [skill, line] of Object.entries(state.lines)) {
                            const active = line.boosts.filter(boost => boost.goodFor >= state.now)
                            context.expect(active.every(boost => typeof boost.source === 'string' && boost.source.length > 0),
                                `${skill} vanilla boosts identify their source`, active)
                            context.expect(active.filter(boost => boost.source === 'vanilla-xp').every(boost => boost.multiplier <= 0.03),
                                `${skill} vanilla pickup bonus remains capped at three percent`, active)
                            context.expect(new Set(active.map(boost => boost.source)).size === active.length,
                                `${skill} refreshes each boost source instead of stacking orb events`, active)
                        }
                        if (mode === 'unity') {
                            context.expect(state.unity >= first.unity + 80, 'The learned Unity adaptation observes every collected orb')
                            context.expect(state.earnings.some(source => source.source.includes('unity') && source.credited > 0),
                                'Unity awards use the ordinary accounted gameplay pipeline')
                        }
                    } else if (mode === 'furnace') {
                        for (let index = 0; index < 7; index++) {
                            await context.sleep(10000)
                            await sample()
                        }
                        actions = 9
                        const state = await snapshot()
                        const earned = totals(state, 'crafting')
                        const initial = totals(before, 'crafting')
                        context.expect(earned.awards > initial.awards && earned.credited > initial.credited,
                            'Natural furnace smelting reaches the credited gameplay pipeline')
                        context.expect(earned.credited - initial.credited < earned.requested - initial.requested,
                            'Idle repeated furnace work is reduced by integrity controls', { before: initial, after: earned })
                        const limits = state.config.xpEarningLimits
                        context.expect(earned.credited - initial.credited <= (limits.skillBurstXp +
                            (state.now - before.now) * limits.skillXpPerMinute / 60000) * limits.maximumAllowanceBoost,
                            'Nine furnaces share one player skill allowance')
                    }
                })
                const earned = await sample()
                evidence.actionsEndedAt = earned.now
                evidence.measurement = measureProgress(before, earned, before.now, actions)
                let after = earned
                await context.waitUntil(async () => {
                    after = await sample()
                    return Object.entries(earned.lines).every(([skill, line]) =>
                        (after.lines[skill]?.xp ?? 0) >= line.totalXp - 0.000001)
                }, { label: 'Observed gameplay XP commits through the natural pool window', timeoutMs: 45000, intervalMs: 1000 })
                evidence.settledAfterMillis = after.now - before.now
                evidence.before = before
                evidence.after = after
                evidence.observedLevelUps = evidence.samples.flatMap(state => Object.entries(state.lines)
                    .filter(([skill, line]) => line.level > (before.lines[skill]?.level ?? 0))
                    .map(([skill, line]) => ({ skill, level: line.level, observedAfterMillis: state.now - before.now,
                        committedXp: line.xp }))).filter((entry, index, entries) =>
                    entries.findIndex(previous => previous.skill === entry.skill && previous.level === entry.level) === index)
                if (!['orbs', 'recycling'].includes(mode)) context.expect(evidence.measurement.skills.length > 0, `${mode} produces observable gameplay progression`)
                if (mode === 'crystals') context.expect(evidence.observedLevelUps.some(entry => entry.skill === 'crafting'),
                    'The real crystal workload reaches an observed committed Crafting level')
                if (mode === 'budget') context.expect(after.lines.crafting.budget.skillUsed > 100,
                    'The persisted allowance has material unrefilled debt', after.lines.crafting.budget)
                if (mode === 'crystals' || mode === 'budget' || mode === 'alternating') {
                    await context.step(`${mode}: retain XP and anti-farm accounting through reconnect`, async () => {
                        const persisted = await snapshot()
                        actor = await actor.reconnectAfter(() => actor.bot.quit('XP integrity persistence'), { timeoutMs: 30000 })
                        let restored
                        await context.waitUntil(async () => {
                            try { restored = await snapshot(); return true } catch { return false }
                        }, { label: 'Reconnected XP profile is ready', timeoutMs: 20000, intervalMs: 500 })
                        assertRetained(context, persisted, restored, 'reconnect')
                        evidence.reconnect = { before: persisted, after: restored }
                        const reloaded = await fixture(actor, 'reload')
                        assertRetained(context, restored, reloaded, 'configuration reload')
                        evidence.reload = { before: restored, after: reloaded }
                        retained = reloaded
                    })
                }
            } catch (failure) {
                evidence.failure = String(failure)
                if (prepared && !context.signal.aborted) evidence.failureState = await snapshot()
                throw failure
            } finally {
                actor.bot.clearControlStates()
                actor.bot.deactivateItem()
                if (actor.bot.currentWindow) actor.bot.closeWindow(actor.bot.currentWindow)
                if (prepared && !context.signal.aborted) await fixture(actor, 'cleanup')
            }
        }
        if (retained) {
            await writeFile(join(context.server.directory, RETAINED_FILE), `${JSON.stringify(retained)}\n`, 'utf8')
            report.restartCandidate = { player: retained.player, processId: retained.processId }
        }
    },
}
