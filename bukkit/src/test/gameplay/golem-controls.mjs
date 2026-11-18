import { randomBytes } from 'node:crypto'
import { fixtureJson } from './fixture-json.mjs'
import { synchronizePlayerInput } from './player-input.mjs'
import { synchronizePlayerVelocity } from './player-velocity.mjs'

const WINDOW_TIMEOUT = 5000
const SOURCE_X = 2
const DESTINATION_X = 4
const TYPE_MISMATCH_X = 6
const BLUE_PICKER_SLOT = 22

function nodeAt(state, x) {
    const node = state.nodes.find(entry => entry.x === x)
    if (!node) throw new Error(`Node at x=${x} is missing`)
    return node
}

function spans(node) {
    return node.max.map((maximum, axis) => maximum - node.min[axis] + 1)
}

function sameBounds(left, right) {
    return left.min.every((value, axis) => value === right.min[axis]) && left.max.every((value, axis) => value === right.max[axis])
}

function allValues(value) {
    if (value === null || value === undefined) return []
    return [value, ...(typeof value === 'object' ? Object.values(value).flatMap(allValues) : [])]
}

function itemText(item) {
    return JSON.stringify(item?.components ?? item?.nbt ?? item ?? '')
}

export default {
    name: 'adapt-golem-controls',
    description: 'Exercise node menus, bounded three-dimensional Crook shifting, private glass packets and rune status tooltips.',
    async run(context) {
        context.bot.physicsEnabled = false
        const suffix = randomBytes(4).toString('hex')
        const actor = await context.connectActor(`AQAa${suffix}`)
        const opponent = await context.connectActor(`AQAb${suffix}`)
        const bot = actor.bot
        const stops = [actor, opponent].flatMap(player => [synchronizePlayerInput(player.bot), synchronizePlayerVelocity(player.bot)])
        const evidence = context.report.golemControls = {}
        const fixture = (action, extra = '') => fixtureJson(context, `/adaptqa golem-controls ${bot.username} ${action}${extra ? ` ${extra}` : ''}`,
            `GOLEM_CONTROLS ${action}`)
        const snapshot = () => fixture('snapshot')
        const closeWindow = async () => {
            if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
            await context.waitUntil(() => !bot.currentWindow, { label: 'node menu closed', timeoutMs: WINDOW_TIMEOUT })
        }
        const openEntity = async id => {
            await context.waitUntil(() => Boolean(bot.entities[id]), { label: `interaction ${id} tracked`, timeoutMs: WINDOW_TIMEOUT })
            const entity = bot.entities[id]
            bot.setControlState('sneak', false)
            await bot.lookAt(entity.position.offset(0, 0.2, 0), true)
            const opened = actor.waitForEvent('windowOpen', () => true, WINDOW_TIMEOUT)
            bot._client.write('use_entity', { target: id, hand: 'main_hand', location: { x: 0, y: 0.2, z: 0 }, sneaking: false })
            await opened
            context.expect(Boolean(bot.currentWindow), 'Interaction opens a real inventory window')
        }
        const openNode = async x => openEntity(nodeAt(await snapshot(), x).interactionId)
        const click = async (slot, { button = 0, redraw = true } = {}) => {
            context.expect(Boolean(bot.currentWindow), `Window is open before clicking ${slot}`)
            const previous = bot.currentWindow
            const contents = JSON.stringify(previous.slots.slice(0, 45).map(itemText))
            await bot.clickWindow(slot, button, 0)
            if (redraw) {
                await context.waitUntil(() => Boolean(bot.currentWindow) && (bot.currentWindow.id !== previous.id
                    || JSON.stringify(bot.currentWindow.slots.slice(0, 45).map(itemText)) !== contents),
                    { label: `menu refresh after slot ${slot}`, timeoutMs: WINDOW_TIMEOUT })
            }
        }
        const doubleSneak = async () => {
            bot.setControlState('sneak', false)
            await context.sleep(80)
            bot.setControlState('sneak', true)
            await context.sleep(100)
            bot.setControlState('sneak', false)
            await context.sleep(100)
            bot.setControlState('sneak', true)
            await context.sleep(180)
        }
        const scroll = async (axis, step) => {
            const target = bot.entity.position.offset(axis === 0 ? 10 : 0, axis === 1 ? 10 : 1.62, axis === 2 ? 10 : 0)
            await bot.lookAt(target, true)
            await bot.waitForTicks(2)
            const prior = nodeAt(await snapshot(), SOURCE_X)
            const movable = prior.min[axis] + step <= 0 && prior.max[axis] + step >= 0
            const expected = prior.min[axis] + (movable ? step : 0)
            const current = bot.quickBarSlot
            bot.setQuickBarSlot((current + step + 9) % 9)
            await context.waitUntil(() => bot.quickBarSlot === current,
                { label: 'shift scroll preserves selected Crook slot', timeoutMs: WINDOW_TIMEOUT })
            let changed
            await context.waitUntil(async () => {
                changed = nodeAt(await snapshot(), SOURCE_X)
                return changed.min[axis] === expected
            }, { label: `Area shift applies on axis ${axis}`, timeoutMs: WINDOW_TIMEOUT })
            return changed
        }
        let setup = false
        try {
            await context.command(`/adaptqa setup ${bot.username} ${opponent.bot.username}`, /^ADAPT_QA SETUP /, 60000)
            setup = true
            await context.waitUntil(async () => (await fixture('ready')).ready,
                { label: 'Fresh controls arena chunk and node index are ready', timeoutMs: 15000, intervalMs: 100 })
            evidence.initial = await fixture('prepare', opponent.bot.username)
            bot.setQuickBarSlot(1)
            await context.sleep(150)
            bot.setQuickBarSlot(0)
            await context.sleep(200)
            await context.step('Crook reveals owned sixteen-block glass areas only to their owner', async () => {
                await context.waitUntil(async () => (await snapshot()).displays.length === 6,
                    { label: 'three owned boxes have outside and inside displays', timeoutMs: 8000 })
                const state = await snapshot()
                context.expect(spans(nodeAt(state, SOURCE_X)).every(size => size === 16), 'Source is exactly sixteen blocks on each axis')
                const outer = state.displays.find(display => display.scale.every(value => value === 16))
                const inner = state.displays.find(display => display.scale.every(value => value === -16))
                context.expect(Boolean(outer && inner), 'The box has stretched exterior and interior faces')
                for (const display of [outer, inner]) {
                    await context.waitUntil(() => Boolean(bot.entities[display.entityId]), { label: 'private glass spawn packet', timeoutMs: WINDOW_TIMEOUT })
                    context.expect(!opponent.bot.entities[display.entityId], 'Another nearby player receives no private glass spawn')
                    const values = allValues(bot.entities[display.entityId].metadata)
                    context.expect(values.some(value => value?.x === display.scale[0] && value?.y === display.scale[1] && value?.z === display.scale[2]),
                        'The client receives the stretched display scale')
                }
                evidence.glass = { outer, inner }
            })
            await context.step('Exact channel picker and filter samples preserve cursor items', async () => {
                await openNode(SOURCE_X)
                await click(2)
                await context.waitUntil(() => /Blue/.test(itemText(bot.currentWindow?.slots[BLUE_PICKER_SLOT])),
                    { label: 'Blue channel picker item is ready', timeoutMs: WINDOW_TIMEOUT })
                await click(BLUE_PICKER_SLOT)
                await context.waitUntil(async () => nodeAt(await snapshot(), SOURCE_X).channel === 'BLUE',
                    { label: 'Picking Blue sets Blue directly', timeoutMs: WINDOW_TIMEOUT })
                await context.waitUntil(() => /Blue/.test(itemText(bot.currentWindow?.slots[2])),
                    { label: 'Node menu returns after picking Blue', timeoutMs: WINDOW_TIMEOUT })
                await click(74, { redraw: false })
                await context.waitUntil(() => bot.currentWindow.selectedItem?.count === 12,
                    { label: 'Seed stack is ready on the cursor', timeoutMs: WINDOW_TIMEOUT })
                await click(18)
                await context.waitUntil(async () => nodeAt(await snapshot(), SOURCE_X).filterItems.includes('WHEAT_SEEDS'),
                    { label: 'A seed sample enters the filter', timeoutMs: WINDOW_TIMEOUT })
                context.expect(bot.currentWindow.selectedItem?.count === 12, 'All twelve original seeds remain on the cursor')
                await click(74, { redraw: false })
                await context.waitUntil(async () => (await snapshot()).inventory.filter(item => item.type === 'WHEAT_SEEDS')
                    .reduce((sum, item) => sum + item.amount, 0) === 12,
                    { label: 'Original seed stack returns to the inventory', timeoutMs: WINDOW_TIMEOUT })
                const state = await snapshot()
                context.expect(state.inventory.filter(item => item.type === 'WHEAT_SEEDS').reduce((sum, item) => sum + item.amount, 0) === 12,
                    'Copying a filter consumes no seeds')
                const copied = actor.waitForMessage(/Node settings copied/i, WINDOW_TIMEOUT)
                await click(1, { redraw: false })
                await copied
                await closeWindow()
                await context.waitUntil(async () => (await snapshot()).displays.some(display => display.block === 'BLUE_STAINED_GLASS'),
                    { label: 'Blue channel recolors glass', timeoutMs: 8000 })
                const glass = (await snapshot()).displays.find(display => display.block === 'BLUE_STAINED_GLASS')
                await context.waitUntil(() => Boolean(bot.entities[glass.entityId]), { label: 'Blue glass reaches the client', timeoutMs: WINDOW_TIMEOUT })
                context.expect(allValues(bot.entities[glass.entityId].metadata).some(value => Number.isInteger(value)
                    && bot.registry.blocksByStateId[value]?.name === 'blue_stained_glass'), 'The client receives Blue stained-glass block state')
                evidence.filter = state
            })
            await context.step('Common settings paste atomically and reject a different node type', async () => {
                await openNode(DESTINATION_X)
                await click(8)
                const original = nodeAt(await snapshot(), SOURCE_X)
                await context.waitUntil(async () => {
                    const pasted = nodeAt(await snapshot(), DESTINATION_X)
                    return sameBounds(pasted, original) && pasted.channel === 'BLUE' && pasted.filterItems.includes('WHEAT_SEEDS')
                }, { label: 'Destination receives the complete copied settings', timeoutMs: WINDOW_TIMEOUT })
                const copied = nodeAt(await snapshot(), DESTINATION_X)
                context.expect(sameBounds(copied, original), 'Paste copies all three-dimensional bounds')
                context.expect(copied.channel === 'BLUE' && copied.filterItems.includes('WHEAT_SEEDS'), 'Paste copies channel and filter samples')
                context.expect(copied.x === DESTINATION_X && copied.face === 'UP' && copied.owner === original.owner, 'Destination identity stays intact')
                await closeWindow()
                await context.command(`/tp ${bot.username} 3.5 100 0.5`, /Teleported/i, WINDOW_TIMEOUT)
                await openNode(TYPE_MISMATCH_X)
                const rejected = actor.waitForMessage(/nodes of this type|current unlocks/i, WINDOW_TIMEOUT)
                await click(8, { redraw: false })
                await rejected
                const other = nodeAt(await snapshot(), TYPE_MISMATCH_X)
                context.expect(other.channel === 'NONE' && other.filters === 0 && other.volume === 125, 'A different type remains completely unchanged')
                await closeWindow()
                await context.command(`/tp ${bot.username} 0.5 100 0.5`, /Teleported/i, WINDOW_TIMEOUT)
                evidence.paste = { copied, rejected: other }
            })
            await context.step('Sizing reaches sixteen without growing beyond the cap', async () => {
                await openNode(SOURCE_X)
                for (const slot of [11, 12, 14]) {
                    await click(slot, { redraw: false })
                    context.expect(spans(nodeAt(await snapshot(), SOURCE_X)).every(size => size === 16), 'A maximum-size axis does not grow')
                }
                await click(11, { button: 1 })
                await context.waitUntil(async () => spans(nodeAt(await snapshot(), SOURCE_X))[0] === 15,
                    { label: 'Right-click shrinks by one block', timeoutMs: WINDOW_TIMEOUT })
                await click(11)
                await context.waitUntil(async () => spans(nodeAt(await snapshot(), SOURCE_X))[0] === 16,
                    { label: 'Left-click restores sixteen blocks', timeoutMs: WINDOW_TIMEOUT })
                await closeWindow()
            })
            await context.step('Double-sneak shifts every axis, wraps the hotbar and keeps the node inside', async () => {
                const before = nodeAt(await snapshot(), SOURCE_X)
                const entered = actor.waitForMessage(/Area shift mode:/i, WINDOW_TIMEOUT)
                await doubleSneak()
                await entered
                const shifts = []
                for (const axis of [0, 1, 2]) {
                    const prior = nodeAt(await snapshot(), SOURCE_X)
                    const forward = await scroll(axis, 1)
                    context.expect(forward.min[axis] === prior.min[axis] + 1 && forward.max[axis] === prior.max[axis] + 1,
                        `Forward scroll moves positive axis ${axis}`)
                    context.expect(spans(forward).every(size => size === 16), 'Shifting preserves every dimension')
                    const backward = await scroll(axis, -1)
                    context.expect(sameBounds(backward, prior), `Reverse scroll wraps zero to eight and restores axis ${axis}`)
                    shifts.push({ axis, forward, backward })
                }
                for (let index = 0; index < 9; index++) await scroll(0, 1)
                const edge = nodeAt(await snapshot(), SOURCE_X)
                context.expect(edge.min[0] === 0 && edge.max[0] === 15 && edge.containsNode, 'The node can touch a face but cannot leave the box')
                const blocked = await scroll(0, 1)
                context.expect(sameBounds(blocked, edge), 'Another shift is rejected without changing either face')
                const finished = actor.waitForMessage(/Area shift mode finished/i, WINDOW_TIMEOUT)
                await doubleSneak()
                await finished
                bot.setControlState('sneak', false)
                bot.setQuickBarSlot(1)
                await context.sleep(150)
                context.expect((await snapshot()).heldSlot === 1, 'Ordinary scrolling switches hotbar slots outside shift mode')
                context.expect(sameBounds(nodeAt(await snapshot(), SOURCE_X), edge), 'Ordinary scrolling leaves the area unchanged')
                evidence.shifting = { before, shifts, edge }
            })
            await context.step('Putting the Crook away clears all private display entities', async () => {
                await context.waitUntil(async () => (await snapshot()).displays.length === 0,
                    { label: 'private boxes clear after switching to paper', timeoutMs: 8000 })
                for (const display of Object.values(evidence.glass)) {
                    await context.waitUntil(() => !bot.entities[display.entityId],
                        { label: 'Client removes its original private display', timeoutMs: WINDOW_TIMEOUT })
                }
                evidence.closed = await snapshot()
            })
            await context.step('Rune slots show active benefits and inactive reasons through packets', async () => {
                const state = await fixture('runes')
                bot.setQuickBarSlot(0)
                await context.sleep(200)
                await openEntity(state.golemEntityId)
                const runeItems = () => bot.currentWindow.slots.slice(9, 18).filter(Boolean).map(item => ({ name: item.name, text: itemText(item) }))
                await context.waitUntil(() => runeItems().some(item => /Might/.test(item.text)) && runeItems().some(item => /Ember/.test(item.text)),
                    { label: 'Stored rune slots arrive', timeoutMs: WINDOW_TIMEOUT })
                const items = runeItems()
                const might = items.find(item => /Might/.test(item.text))
                const ember = items.find(item => /Ember/.test(item.text))
                context.expect(Boolean(might && ember), 'Stored runes appear as real slot items')
                context.expect(/Active/.test(might.text) && /damage|attack/i.test(might.text), 'Active Might states its applied benefit')
                context.expect(/Inert|locked|cannot|blocked|needs/i.test(ember.text), 'Inactive Ember explains its reason')
                context.expect(!/Active/.test(ember.text), 'Inactive Ember does not claim to be active')
                await closeWindow()
                await fixture('masterwork')
                await openEntity(state.golemEntityId)
                await context.waitUntil(() => runeItems().some(item => /Swiftness/.test(item.text)),
                    { label: 'Masterwork rune slots arrive', timeoutMs: WINDOW_TIMEOUT })
                const masterworkItems = runeItems()
                context.expect(masterworkItems.some(item => /Swiftness/.test(item.text) && /Inert|tier|needs/i.test(item.text)),
                    'Masterwork retains the above-tier rune and explains why it is inactive')
                evidence.runes = { initial: items, masterwork: masterworkItems }
                await closeWindow()
            })
            await context.step('A Harvester plants bare farmland from its linked seed chest', async () => {
                const before = await fixture('planting')
                context.expect(before.planting.crops === 0 && before.planting.chestSeeds === 8 && before.planting.cargoSeeds === 0,
                    'The nine-tile field starts empty and its only eight seeds are in the linked chest')
                let after
                await context.waitUntil(async () => {
                    after = await snapshot()
                    return after.planting.crops === 8 && after.planting.chestSeeds === 0
                }, { label: 'Harvester plants eight crops using eight chest seeds', timeoutMs: 60000, intervalMs: 500 })
                context.expect(after.planting.cargoSeeds === 0, 'Planting leaves no duplicated or stranded seed in cargo')
                const crop = bot.entity.position.clone().set(0, 100, 6)
                await context.waitUntil(() => bot.blockAt(crop)?.name === 'wheat',
                    { label: 'The client receives a naturally planted wheat block', timeoutMs: WINDOW_TIMEOUT })
                evidence.planting = { before: before.planting, after: after.planting }
            })
        } finally {
            for (const player of [actor, opponent]) {
                player.bot.clearControlStates()
                player.bot.deactivateItem()
                if (player.bot.currentWindow) player.bot.closeWindow(player.bot.currentWindow)
            }
            if (setup && !context.signal.aborted) await context.command('/adaptqa cleanup', /^ADAPT_QA CLEANUP$/, 15000)
            for (const stop of stops) stop()
        }
    },
}
