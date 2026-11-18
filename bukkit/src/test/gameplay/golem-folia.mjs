import { randomBytes } from 'node:crypto'
import { fixtureJson } from './fixture-json.mjs'
import { synchronizePlayerInput } from './player-input.mjs'
import { synchronizePlayerVelocity } from './player-velocity.mjs'

export default {
    name: 'adapt-golem-folia',
    description: 'Plant across a chunk boundary and edit the same bounded work area on Folia owning regions.',
    async run(context) {
        context.bot.physicsEnabled = false
        const actor = await context.connectActor(`AQAfol${randomBytes(4).toString('hex')}`)
        const bot = actor.bot
        const stops = [synchronizePlayerInput(bot), synchronizePlayerVelocity(bot)]
        const evidence = context.report.golemFolia = {}
        const fixture = action => fixtureJson(context, `/golemqa ${bot.username} ${action}`, `FOLIA_GOLEM ${action}`, 20000)
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
        let prepared = false
        try {
            evidence.initial = await fixture('prepare')
            prepared = true
            context.bot.chat('/gamemode spectator @s')
            await context.waitUntil(() => context.bot.game.gameMode === 'spectator',
                { label: 'Observer is in spectator mode', timeoutMs: 5000 })
            context.bot.chat('/tp @s 14.5 104 -3.5')
            await context.waitUntil(() => Math.abs(context.bot.entity.position.x - 14.5) < 0.1
                && Math.abs(context.bot.entity.position.y - 104) < 0.1 && Math.abs(context.bot.entity.position.z + 3.5) < 0.1,
                { label: 'Observer reaches the Folia fixture', timeoutMs: 5000 })
            bot.setQuickBarSlot(1)
            await context.sleep(150)
            bot.setQuickBarSlot(0)
            await context.step('A sixteen-block Harvest box dispatches work across two Folia chunks', async () => {
                context.expect(evidence.initial.minChunkX === 0 && evidence.initial.maxChunkX === 1 && evidence.initial.volume === 4096,
                    'The actual node spans two chunks and the full sixteen-block cube')
                let state
                await context.waitUntil(async () => {
                    state = await fixture('snapshot')
                    return state.crops === 8 && state.chestSeeds === 0
                }, { label: 'Eight chest seeds become eight crops across Folia chunks', timeoutMs: 240000, intervalMs: 500 })
                context.expect(state.cargoSeeds === 0, 'No seed is duplicated into cargo')
                evidence.planting = state
            })
            await context.step('Native double-sneak and held-slot events shift all three axes on Folia', async () => {
                const entered = actor.waitForMessage(/Area shift mode:/i, 5000)
                await doubleSneak()
                await entered
                let previous = await fixture('snapshot')
                const shifts = []
                for (const axis of [0, 1, 2]) {
                    await bot.lookAt(bot.entity.position.offset(axis === 0 ? 10 : 0, axis === 1 ? 10 : 1.62, axis === 2 ? 10 : 0), true)
                    await bot.waitForTicks(2)
                    bot.setQuickBarSlot(1)
                    await context.waitUntil(() => bot.quickBarSlot === 0, { label: 'Folia shift preserves the Crook slot', timeoutMs: 5000 })
                    let next
                    await context.waitUntil(async () => {
                        next = await fixture('snapshot')
                        return next.min[axis] === previous.min[axis] + 1 && next.max[axis] === previous.max[axis] + 1
                    }, { label: `Folia shifts both faces on axis ${axis}`, timeoutMs: 5000 })
                    context.expect(next.volume === 4096 && next.containsNode, 'Folia keeps size and node inclusion intact')
                    shifts.push(next)
                    previous = next
                }
                const exited = actor.waitForMessage(/Area shift mode finished/i, 5000)
                await doubleSneak()
                await exited
                bot.setControlState('sneak', false)
                evidence.shifts = shifts
            })
            await context.step('Resizing invalidates discovery while subsequent chest supply still plants', async () => {
                const state = await fixture('snapshot')
                await context.waitUntil(() => Boolean(bot.entities[state.interactionId]), { label: 'Folia node hitbox reaches its owner', timeoutMs: 5000 })
                const opened = actor.waitForEvent('windowOpen', () => true, 5000)
                bot._client.write('use_entity', { target: state.interactionId, hand: 'main_hand', location: { x: 0, y: 0.2, z: 0 }, sneaking: false })
                await opened
                for (const [button, expected] of [[1, 3840], [0, 4096]]) {
                    const current = bot.currentWindow
                    const contents = JSON.stringify(current.slots.slice(0, 45))
                    await bot.clickWindow(11, button, 0)
                    await context.waitUntil(() => bot.currentWindow && (bot.currentWindow.id !== current.id
                        || JSON.stringify(bot.currentWindow.slots.slice(0, 45)) !== contents),
                        { label: 'Folia axis resize refreshes its menu', timeoutMs: 5000 })
                    await context.waitUntil(async () => (await fixture('snapshot')).volume === expected,
                        { label: 'Folia applies the exact one-block width change', timeoutMs: 5000 })
                }
                bot.closeWindow(bot.currentWindow)
                await fixture('refill')
                let stateAfter
                await context.waitUntil(async () => {
                    stateAfter = await fixture('snapshot')
                    return stateAfter.crops === 12 && stateAfter.chestSeeds === 0
                }, { label: 'Edited Folia node plants four supplied seeds', timeoutMs: 60000, intervalMs: 500 })
                context.expect(stateAfter.cargoSeeds === 0 && stateAfter.containsNode, 'Edited node consumes exactly four seeds and remains valid')
                context.expect(stateAfter.westCrops > 0 && stateAfter.eastCrops > 0,
                    'Production work plants on both sides of the chunk boundary')
                evidence.afterEdit = stateAfter
            })
        } finally {
            bot.clearControlStates()
            if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
            if (prepared && !context.signal.aborted) {
                await fixture('cleanup')
                await context.waitUntil(async () => (await fixture('settled')).settled,
                    { label: 'Folia node return settles before server cleanup', timeoutMs: 15000, intervalMs: 100 })
            }
            for (const stop of stops) stop()
        }
    },
}
