import { randomBytes } from 'node:crypto'
import { fixtureJson } from './fixture-json.mjs'

const TIMEOUT = 6000
const TARGETS = ['core', 'node']
const ACCEPTED = ['canonical', 'decorated']
const REJECTED = ['vanilla', 'wrong_variant', 'wrong_material', 'missing']

function text(value) {
    return JSON.stringify(value, (_, entry) => typeof entry === 'bigint' ? String(entry) : entry) ?? ''
}

function isOutput(item, target) {
    return target === 'core' ? item?.kind === 'core' && item.role === 'guardian'
        : item?.kind === 'node' && item.node === 'deposit'
}

function inputs(state) {
    return [...state.inventory, ...(state.crafting.matrix ?? []), ...(state.crafter ?? []), ...(state.cursor ? [state.cursor] : [])]
}

function bag(items) {
    const amounts = new Map()
    for (const item of items) {
        const { amount, ...identity } = item
        const key = text(identity)
        amounts.set(key, (amounts.get(key) ?? 0) + amount)
    }
    return text([...amounts].sort(([left], [right]) => left.localeCompare(right)))
}

function outputAmount(state, target, automated) {
    return (automated ? state.output : state.inventory).filter(item => isOutput(item, target))
        .reduce((amount, item) => amount + item.amount, 0)
}

function assertConsumption(context, before, after, target, automated) {
    const blank = target === 'core' ? 'blank_core' : 'blank_node'
    const remaining = inputs(after).filter(item => item.kind === blank).reduce((amount, item) => amount + item.amount, 0)
    context.expect(remaining === 1, 'Exactly one of the two tagged blank ingredients is consumed')
    const plain = inputs(after).filter(item => !item.kind)
    context.expect(plain.length === 0, 'The sword, shield and ingots, or the chest, are consumed exactly once')
    context.expect(outputAmount(after, target, automated) === 1, 'Exactly one correct tagged result is delivered')
    context.expect(inputs(before).filter(item => item.kind === blank).reduce((amount, item) => amount + item.amount, 0) === 2,
        'The case starts with exactly two tagged blank ingredients')
}

export default {
    name: 'adapt-custom-recipe-ingredients',
    description: 'Use native recipe-book placement and a redstone Crafter with canonical, decorated and invalid custom ingredients.',
    async run(context) {
        context.bot.physicsEnabled = false
        const suffix = randomBytes(4).toString('hex')
        const actor = await context.connectActor(`AQArec${suffix}`)
        const witness = await context.connectActor(`AQAref${suffix}`)
        const bot = actor.bot
        const recipes = new Map()
        const ghosts = []
        const receiveRecipes = packet => {
            for (const entry of packet.entries) {
                const recipe = entry.recipe
                const result = text(recipe.display?.data?.result).toLowerCase()
                const target = result.includes('guardian') ? 'core' : result.includes('deposit') ? 'node' : null
                if (target) recipes.set(target, recipe)
            }
        }
        const receiveGhost = packet => ghosts.push(packet)
        bot._client.on('recipe_book_add', receiveRecipes)
        bot._client.on('craft_recipe_response', receiveGhost)
        const evidence = context.report.customRecipes = { cases: [] }
        const fixture = (action, detail = '') => fixtureJson(context,
            `/adaptqa custom-recipes ${bot.username} ${action}${detail ? ` ${detail}` : ''}`, `CUSTOM_RECIPES ${action}`)
        const position = (x, y, z) => new bot.entity.position.constructor(x, y, z)
        const openTable = async () => {
            await context.waitUntil(() => bot.blockAt(position(0, 100, 0))?.name === 'crafting_table',
                { label: 'Crafting table reaches the client', timeoutMs: TIMEOUT })
            const opened = actor.waitForEvent('windowOpen', () => true, TIMEOUT)
            await bot.activateBlock(bot.blockAt(position(0, 100, 0)))
            await opened
            context.expect(bot.currentWindow.type.includes('crafting'), 'A real crafting-table inventory is open')
        }
        const prepare = async (mode, target, variant) => {
            if (bot.currentWindow) {
                bot.closeWindow(bot.currentWindow)
                await context.waitUntil(() => !bot.currentWindow, { label: 'Previous crafting window closes', timeoutMs: TIMEOUT })
            }
            const state = await fixture('prepare', `${mode} ${target} ${variant}`)
            await context.waitUntil(() => recipes.has(target), { label: `${target} recipe display is discovered`, timeoutMs: TIMEOUT })
            return state
        }
        const bookCase = async (target, variant, accepted) => {
            const initial = await prepare('book', target, variant)
            await openTable()
            const before = await fixture('snapshot')
            const recipe = recipes.get(target)
            const blank = target === 'core' ? 'blank_core' : 'blank_node'
            context.expect(recipe.display.type === 'crafting_shaped', 'The server sends a shaped custom recipe display')
            context.expect(recipe.display.data.ingredients.some(slot => {
                const encoded = text(slot)
                return encoded.includes('golem_item') && encoded.includes(blank)
            }), 'The recipe book receives the tagged blank ingredient instead of a plain material')
            const previousGhosts = ghosts.length
            bot._client.write('craft_recipe_request', { windowId: bot.currentWindow.id, recipeId: recipe.displayId, makeAll: false })
            let placed
            if (accepted) {
                await context.waitUntil(async () => {
                    placed = await fixture('snapshot')
                    return isOutput(placed.crafting.result, target)
                }, { label: `Recipe book places ${variant} ${target} ingredients and shows the result`, timeoutMs: TIMEOUT })
                context.expect(placed.crafting.matrix.some(item => item.kind === blank), 'Native recipe-book placement moves the tagged input into the grid')
                await context.waitUntil(() => {
                    const encoded = text(bot.currentWindow?.slots[0])
                    return encoded.includes('golem_item') && encoded.toLowerCase().includes(target === 'core' ? 'guardian' : 'deposit')
                }, { label: 'The client receives the correct tagged crafting result', timeoutMs: TIMEOUT })
                await bot.clickWindow(0, 0, 1)
                let after
                await context.waitUntil(async () => {
                    after = await fixture('snapshot')
                    return outputAmount(after, target, false) === 1
                }, { label: 'The crafted result reaches the inventory', timeoutMs: TIMEOUT })
                assertConsumption(context, before, after, target, false)
                evidence.cases.push({ mode: 'book', target, variant, accepted, initial, placed, after, displayId: recipe.displayId })
            } else {
                await context.waitUntil(() => ghosts.length > previousGhosts,
                    { label: 'The server responds with a missing-ingredients recipe ghost', timeoutMs: TIMEOUT })
                const after = await fixture('snapshot')
                context.expect(!after.crafting.result, 'Invalid or missing custom ingredients produce no result')
                context.expect(outputAmount(after, target, false) === 0, 'No result reaches the inventory')
                context.expect(bag(inputs(before)) === bag(inputs(after)), 'A rejected recipe-book request consumes no ingredients')
                evidence.cases.push({ mode: 'book', target, variant, accepted, before, after, displayId: recipe.displayId })
            }
            bot.closeWindow(bot.currentWindow)
            await context.waitUntil(() => !bot.currentWindow, { label: 'Crafting window closes after the case', timeoutMs: TIMEOUT })
        }
        const crafterCase = async (target, variant, accepted) => {
            const before = await prepare('crafter', target, variant)
            await context.waitUntil(() => bot.blockAt(position(3, 100, 0))?.name === 'lever'
                && bot.blockAt(position(4, 100, 0))?.name === 'crafter',
            { label: 'Unpowered lever and Crafter reach the client', timeoutMs: TIMEOUT })
            await bot.activateBlock(bot.blockAt(position(3, 100, 0)))
            await context.waitUntil(async () => (await fixture('snapshot')).powered,
                { label: 'Native lever interaction powers the Crafter', timeoutMs: TIMEOUT })
            let after
            if (accepted) {
                await context.waitUntil(async () => {
                    after = await fixture('snapshot')
                    return outputAmount(after, target, true) === 1
                }, { label: 'The redstone Crafter inserts the tagged result into its output chest', timeoutMs: TIMEOUT })
                assertConsumption(context, before, after, target, true)
            } else {
                await bot.waitForTicks(12)
                after = await fixture('snapshot')
                context.expect(after.output.length === 0, 'The Crafter emits no output for an invalid custom ingredient')
                context.expect(bag(inputs(before)) === bag(inputs(after)), 'A rejected Crafter pulse consumes no inputs')
            }
            evidence.cases.push({ mode: 'crafter', target, variant, accepted, before, after })
        }
        let setup = false
        try {
            await context.command(`/adaptqa setup ${bot.username} ${witness.bot.username}`, /^ADAPT_QA SETUP /, 60000)
            setup = true
            await context.waitUntil(async () => (await fixture('ready')).ready,
                { label: 'Custom recipes and actor runtime are ready', timeoutMs: 15000, intervalMs: 100 })
            for (const target of TARGETS) {
                await context.step(`Recipe book accepts genuine ${target} ingredients and rejects substitutes`, async () => {
                    for (const variant of ACCEPTED) await bookCase(target, variant, true)
                    for (const variant of REJECTED) await bookCase(target, variant, false)
                })
            }
            for (const target of TARGETS) {
                await context.step(`A redstone Crafter enforces the ${target} custom ingredient identity`, async () => {
                    for (const variant of ACCEPTED) await crafterCase(target, variant, true)
                    for (const variant of REJECTED.filter(value => value !== 'missing')) await crafterCase(target, variant, false)
                })
            }
        } finally {
            bot._client.off('recipe_book_add', receiveRecipes)
            bot._client.off('craft_recipe_response', receiveGhost)
            if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
            if (setup && !context.signal.aborted) {
                await fixture('cleanup')
                await context.command('/adaptqa cleanup', /^ADAPT_QA CLEANUP/, 15000)
            }
        }
    },
}
