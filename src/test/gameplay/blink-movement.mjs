import { randomBytes } from 'node:crypto'
import { fixtureJson } from './fixture-json.mjs'
import { synchronizePlayerInput } from './player-input.mjs'
import { synchronizePlayerVelocity } from './player-velocity.mjs'

export default {
    name: 'adapt-blink-movement',
    description: 'Verify view and movement Blink directions using actual player movement, jump input, combat, server locks and menu controls.',
    async run(context) {
        const suffix = randomBytes(4).toString('hex')
        const actor = await context.connectActor(`AQAm${suffix}`)
        const opponent = await context.connectActor(`AQAn${suffix}`)
        const stops = [actor, opponent].flatMap(player => [synchronizePlayerInput(player.bot), synchronizePlayerVelocity(player.bot)])
        const evidence = context.report.blinkMovement = { player: actor.bot.username, trials: [] }
        const action = (player, verb, value = '') => fixtureJson(context,
            `/adaptqa blink ${player.bot.username} ${verb}${value === '' ? '' : ` ${value}`}`, `BLINK ${verb}`)
        const snapshot = () => action(actor, 'snapshot')
        const control = (state, label) => state.slots.find(item => item.name.replace(/§./g, '').toLowerCase() === label.toLowerCase())
        let setup = false
        async function ready() {
            actor.bot.clearControlStates()
            await actor.bot.waitForTicks(45)
            await action(actor, 'position', 0.5)
            await action(opponent, 'position', 2.5)
            await actor.bot.look(-Math.PI / 2, 0, true)
            await actor.bot.waitForTicks(10)
        }
        async function manual(direction, motion, label) {
            await ready()
            await action(actor, 'activation', 'MANUAL')
            await action(actor, 'direction', direction)
            const before = await snapshot()
            actor.bot.setControlState(motion, true)
            try {
                await actor.bot.waitForTicks(5)
                actor.bot.setControlState('jump', true)
                await actor.bot.waitForTicks(4)
                actor.bot.setControlState('jump', false)
                await actor.bot.waitForTicks(2)
                actor.bot.setControlState('jump', true)
                await actor.bot.waitForTicks(3)
            } finally {
                actor.bot.clearControlStates()
            }
            await context.waitUntil(async () => (await snapshot()).teleports === before.teleports + 1,
                { label: `${label} completes one real Blink`, timeoutMs: 5000 })
            const after = await snapshot()
            context.expect(after.health < before.health, `${label} charges the normal pearl health cost`)
            context.expect(Math.hypot(after.destination.dx, after.destination.dy, after.destination.dz) < 35,
                `${label} stays within Blink range`, { destination: after.destination })
            evidence.trials.push({ label, motion, direction, before, after })
            return after
        }
        async function reactive(direction, motion, label) {
            await ready()
            await action(actor, 'activation', 'REACTIVE')
            await action(actor, 'reactive-direction', direction)
            const before = await snapshot()
            if (motion) actor.bot.setControlState(motion, true)
            try {
                await actor.bot.waitForTicks(4)
                const target = opponent.bot.players[actor.bot.username]?.entity
                context.expect(Boolean(target), 'Opponent sees the player to attack')
                await opponent.bot.lookAt(target.position.offset(0, 1, 0), true)
                opponent.bot.attack(target)
                await context.waitUntil(async () => (await snapshot()).teleports === before.teleports + 1,
                    { label: `${label} completes one reactive Blink`, timeoutMs: 5000 })
            } finally {
                actor.bot.clearControlStates()
            }
            const after = await snapshot()
            context.expect(after.attack?.cause === 'ENTITY_ATTACK' && after.attack.cancelled,
                `${label} evades the actual melee attack`)
            context.expect(after.health < before.health, `${label} charges the normal pearl health cost`)
            evidence.trials.push({ label, motion, direction, before, after })
            return after
        }
        try {
            await context.command(`/adaptqa setup ${actor.bot.username} ${opponent.bot.username}`, /^ADAPT_QA SETUP /, 60000)
            setup = true
            await context.command('/adaptqa stage rift-blink', /^ADAPT_QA STAGE rift-blink$/, 10000)
            await action(actor, 'level', 2)
            await action(opponent, 'level', 2)
            await action(opponent, 'weapons')
            await opponent.bot.equip(opponent.bot.inventory.items().find(item => item.name === 'wooden_sword'), 'hand')
            await context.step('direction controls cycle in place and follow activation visibility', async () => {
                await action(actor, 'level', 1)
                await action(actor, 'open')
                await context.waitUntil(() => actor.bot.currentWindow, { label: 'Blink menu opens', timeoutMs: 5000 })
                await actor.bot.waitForTicks(20)
                const window = actor.bot.currentWindow
                let openings = 0
                let closings = 0
                const opened = () => openings++
                const closed = () => closings++
                actor.bot.on('windowOpen', opened)
                actor.bot.on('windowClose', closed)
                try {
                    const before = await snapshot()
                    const direction = control(before, 'Blink direction')
                    context.expect(Boolean(direction), 'Manual direction is available at level one')
                    context.expect(!control(before, 'Reactive direction'), 'Level one hides Reactive direction')
                    await actor.bot.clickWindow(direction.slot, 0, 0)
                    await context.waitUntil(async () => (await snapshot()).direction === 'MOMENTUM',
                        { label: 'Manual direction cycles to Momentum', timeoutMs: 5000 })
                    const movement = await snapshot()
                    context.expect(control(movement, 'Blink direction').material === 'WIND_CHARGE', 'Movement selection changes the icon')
                    context.expect((await action(opponent, 'snapshot')).direction === 'LOOK', 'Direction remains local to its player')
                    await action(actor, 'level', 2)
                    await actor.bot.clickWindow(control(movement, 'Activation').slot, 0, 0)
                    await context.waitUntil(async () => Boolean(control(await snapshot(), 'Reactive direction')),
                        { label: 'Reactive direction appears', timeoutMs: 5000 })
                    const reactiveState = await snapshot()
                    context.expect(!control(reactiveState, 'Blink direction'), 'Reactive mode hides Manual direction')
                    await actor.bot.clickWindow(control(reactiveState, 'Reactive direction').slot, 0, 0)
                    await context.waitUntil(async () => (await snapshot())['reactive-direction'] === 'AWAY_FROM_ATTACKER',
                        { label: 'Reactive direction cycles to Away', timeoutMs: 5000 })
                    await actor.bot.clickWindow(control(reactiveState, 'Reactive direction').slot, 0, 0)
                    await context.waitUntil(async () => (await snapshot())['reactive-direction'] === 'MOMENTUM',
                        { label: 'Reactive direction cycles to Momentum', timeoutMs: 5000 })
                    await actor.bot.clickWindow(control(reactiveState, 'Activation').slot, 0, 0)
                    await context.waitUntil(async () => Boolean(control(await snapshot(), 'Blink direction')),
                        { label: 'Manual direction reappears', timeoutMs: 5000 })
                    const restored = await snapshot()
                    context.expect(restored.direction === 'MOMENTUM' && !control(restored, 'Reactive direction'),
                        'Manual mode retains its separate direction and hides Reactive direction')
                    context.expect(actor.bot.currentWindow === window && openings === 0 && closings === 0,
                        'All direction and activation clicks preserve the exact existing window')
                    evidence.menu = { windowId: window.id, openings, closings, before, movement, reactiveState, restored }
                } finally {
                    actor.bot.off('windowOpen', opened)
                    actor.bot.off('windowClose', closed)
                    if (actor.bot.currentWindow) actor.bot.closeWindow(actor.bot.currentWindow)
                }
            })
            await context.step('manual Blink follows lateral movement despite looking forward', async () => {
                const state = await manual('MOMENTUM', 'left', 'Manual lateral Momentum')
                context.expect(state.destination.dz < -4 && Math.abs(state.destination.dx) < 2,
                    'Lateral movement teleports north while the player looks east', { destination: state.destination })
            })
            await context.step('manual Blink follows backward movement', async () => {
                const state = await manual('MOMENTUM', 'back', 'Manual backward Momentum')
                context.expect(state.destination.dx < -4, 'Backward movement teleports opposite the view', { destination: state.destination })
            })
            await context.step('Look direction preserves view aiming while moving backward', async () => {
                const state = await manual('LOOK', 'back', 'Manual backward Look')
                context.expect(state.destination.dx > 5, 'Look direction teleports forward despite backward movement', { destination: state.destination })
            })
            await context.step('server lock overrides movement aiming without deleting the player choice', async () => {
                await action(actor, 'direction', 'MOMENTUM')
                const locked = await action(actor, 'direction-lock', 'true')
                context.expect(locked.direction === 'LOOK' && locked.savedDirection === 'MOMENTUM', 'Server Look overrides saved Momentum')
                context.expect(!(await action(actor, 'direction', 'MOMENTUM')).changed, 'Server lock rejects changing the direction')
                const state = await manual('MOMENTUM', 'back', 'Server-locked Look')
                context.expect(state.destination.dx > 5, 'Actual Blink obeys the server-locked Look direction', { destination: state.destination })
                const restored = await action(actor, 'direction-lock', 'false')
                context.expect(restored.direction === 'MOMENTUM', 'Unlock restores the saved movement direction')
            })
            await context.step('reactive Blink follows lateral movement', async () => {
                const state = await reactive('MOMENTUM', 'left', 'Reactive lateral Momentum')
                context.expect(state.destination.dz < -5 && Math.abs(state.destination.dx) < 2,
                    'Reactive Momentum follows movement instead of look or attacker direction', { destination: state.destination })
            })
            await context.step('stationary reactive Momentum falls back to look direction', async () => {
                const state = await reactive('MOMENTUM', null, 'Stationary reactive Momentum')
                context.expect(state.destination.dx > 5 && Math.abs(state.destination.dz) < 2,
                    'Stationary Momentum safely falls back to the current view', { destination: state.destination })
            })
            await context.step('reactive Look ignores lateral movement', async () => {
                const state = await reactive('LOOK', 'left', 'Reactive lateral Look')
                context.expect(state.destination.dx > 5 && Math.abs(state.destination.dz) < 2,
                    'Reactive Look continues aiming along the view', { destination: state.destination })
            })
        } finally {
            for (const player of [actor, opponent]) {
                player.bot.clearControlStates()
                player.bot.deactivateItem()
                if (player.bot.currentWindow) player.bot.closeWindow(player.bot.currentWindow)
            }
            if (setup && !context.signal.aborted) {
                await action(actor, 'direction-lock', 'false')
                await context.command('/adaptqa cleanup', /^ADAPT_QA CLEANUP$/, 15000)
            }
            for (const stop of stops) stop()
        }
    },
}
