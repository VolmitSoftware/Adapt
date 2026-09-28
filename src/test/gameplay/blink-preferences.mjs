import { randomBytes } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import { fixtureJson } from './fixture-json.mjs'
import { decodeSoundPacket } from './feedback.mjs'
import { synchronizePlayerInput } from './player-input.mjs'
import { synchronizePlayerVelocity } from './player-velocity.mjs'

export default {
    name: 'adapt-blink-preferences',
    description: 'Exercise Blink player controls, menu slot changes, level gates, server locks, persistence and reactive combat.',
    async run(context) {
        if (context.options.command) {
            const previous = JSON.parse(await readFile(context.options.command, 'utf8'))
            context.expect(previous.status === 'passed', 'Persistence input was a passing gameplay run')
            const actor = await context.connectActor(previous.blink.player)
            await actor.bot.waitForTicks(20)
            const state = await fixtureJson(context, `/adaptqa blink ${actor.bot.username} snapshot`, 'BLINK snapshot')
            context.expect(state.processId !== previous.blink.processId, 'Preferences load in a different server process')
            for (const key of ['enabled', 'phasing', 'targeting', 'activation', 'reactive-direction']) {
                context.expect(state[key] === previous.blink.retained[key], `${key} survives complete server restart`)
            }
            context.report.blink = { beforeProcess: previous.blink.processId, afterProcess: state.processId, retained: state }
            return
        }
        const suffix = randomBytes(4).toString('hex')
        let actor = await context.connectActor(`AQAb${suffix}`)
        const opponent = await context.connectActor(`AQAc${suffix}`)
        const stops = [actor, opponent].flatMap(player => [synchronizePlayerInput(player.bot), synchronizePlayerVelocity(player.bot)])
        const evidence = context.report.blink = { player: actor.bot.username, trials: [], sounds: [], realClientSound: 'not verified' }
        let setup = false
        const action = (player, verb, value = '') => fixtureJson(context,
            `/adaptqa blink ${player.bot.username} ${verb}${value === '' ? '' : ` ${value}`}`, `BLINK ${verb}`)
        const snapshot = () => action(actor, 'snapshot')
        const control = (state, label) => state.slots.find(item => item.name.replace(/§./g, '') === label)
        const stateEventually = async (key, value) => {
            let state
            await context.waitUntil(async () => {
                state = await snapshot()
                return state[key] === value
            }, { label: `${key} becomes ${value}`, timeoutMs: 5000 })
            return state
        }
        async function clickFeedback(slot, label, name = 'ui.button.click', pitch = 1.2) {
            context.expect(actor.bot.entity.position.distanceTo(opponent.bot.entity.position) < 8,
                'The other player is within menu sound range')
            const received = []
            const nearby = []
            const onSound = packet => received.push(decodeSoundPacket(actor.bot.registry, packet))
            const onNearbySound = packet => nearby.push(decodeSoundPacket(opponent.bot.registry, packet))
            const matches = sound => sound.name === name && Math.abs(sound.pitch - pitch) < 0.001
                && Math.abs(sound.volume - 0.3) < 0.001
            actor.bot._client.on('sound_effect', onSound)
            opponent.bot._client.on('sound_effect', onNearbySound)
            try {
                await actor.bot.clickWindow(slot, 0, 0)
                await context.waitUntil(() => received.some(matches), { label: `${label} emits feedback`, timeoutMs: 3000 })
                await actor.bot.waitForTicks(4)
                context.expect(received.filter(matches).length === 1, `${label} emits one matching sound packet`, { received })
                context.expect(!nearby.some(matches), `${label} feedback is private to its viewer`, { nearby })
                evidence.sounds.push({ label, expected: { name, pitch, volume: 0.3 }, received, nearby })
            } finally {
                actor.bot._client.off('sound_effect', onSound)
                opponent.bot._client.off('sound_effect', onNearbySound)
            }
        }
        async function stage() {
            await context.command('/adaptqa stage rift-blink', /^ADAPT_QA STAGE rift-blink$/, 10000)
            await actor.bot.waitForTicks(5)
        }
        async function readyCombat(x = 0.5) {
            await action(actor, 'position', x)
            await action(opponent, 'position', x + 2)
            await actor.bot.look(-Math.PI / 2, 0, true)
            await actor.bot.waitForTicks(4)
        }
        async function strike() {
            const target = opponent.bot.players[actor.bot.username]?.entity
            context.expect(Boolean(target), 'Opponent sees the actual player to attack')
            await opponent.bot.lookAt(target.position.offset(0, 1, 0), true)
            opponent.bot.attack(target)
            await actor.bot.waitForTicks(5)
        }
        async function doubleJump() {
            const landings = []
            const moved = () => landings.push(actor.bot.entity.position.clone())
            actor.bot.on('forcedMove', moved)
            try {
                actor.bot.setControlState('jump', true)
                await actor.bot.waitForTicks(4)
                actor.bot.setControlState('jump', false)
                await actor.bot.waitForTicks(2)
                actor.bot.setControlState('jump', true)
                await actor.bot.waitForTicks(3)
                actor.bot.setControlState('jump', false)
                await actor.bot.waitForTicks(8)
                return landings
            } finally {
                actor.bot.off('forcedMove', moved)
                actor.bot.setControlState('jump', false)
            }
        }
        try {
            await context.command(`/adaptqa setup ${actor.bot.username} ${opponent.bot.username}`, /^ADAPT_QA SETUP /, 60000)
            setup = true
            await stage()
            await action(opponent, 'position', 2.5)
            await action(actor, 'level', 1)
            await action(opponent, 'level', 2)
            evidence.processId = (await snapshot()).processId
            await context.step('reactive mode requires level two and remains a player-local choice', async () => {
                const denied = await action(actor, 'activation', 'REACTIVE')
                context.expect(!denied.changed && denied.activation === 'MANUAL', 'Level one cannot enable Reactive')
                await action(actor, 'open')
                await context.waitUntil(() => actor.bot.currentWindow, { label: 'Level-one Blink menu opens', timeoutMs: 5000 })
                await actor.bot.waitForTicks(20)
                const levelOne = await snapshot()
                context.expect(!control(levelOne, 'Reactive direction'), 'Level-one menu hides Reactive direction')
                const levelOneWindow = actor.bot.currentWindow
                await clickFeedback(control(levelOne, 'Activation').slot, 'Level-locked mode', 'block.note_block.bass', 0.7)
                context.expect((await snapshot()).activation === 'MANUAL', 'Level-locked click leaves Manual selected')
                context.expect(actor.bot.currentWindow === levelOneWindow, 'Level-locked click keeps the menu open')
                if (actor.bot.currentWindow) actor.bot.closeWindow(actor.bot.currentWindow)
                await action(actor, 'level', 2)
                const accepted = await action(actor, 'activation', 'REACTIVE')
                context.expect(accepted.changed && accepted.activation === 'REACTIVE', 'Level two can enable Reactive')
                context.expect((await action(opponent, 'snapshot')).activation === 'MANUAL', 'Other player keeps Manual mode')
                const downgraded = await action(actor, 'level', 1)
                context.expect(downgraded.activation === 'MANUAL' && downgraded.savedActivation === 'REACTIVE', 'Downgrade suspends the saved Reactive choice')
                context.expect((await action(actor, 'level', 2)).activation === 'REACTIVE', 'Level recovery restores the saved choice')
                evidence.trials.push({ name: 'level-gate-and-isolation', denied, accepted, downgraded })
            })
            await context.step('server lock overrides and preserves the player choice', async () => {
                const locked = await action(actor, 'lock', 'true')
                context.expect(locked.activation === 'MANUAL' && locked.savedActivation === 'REACTIVE', 'Server default overrides the saved choice')
                context.expect(!(await action(actor, 'activation', 'REACTIVE')).changed, 'Locked preference rejects writes')
                await action(actor, 'open')
                await context.waitUntil(() => actor.bot.currentWindow, { label: 'Server-locked Blink menu opens', timeoutMs: 5000 })
                await actor.bot.waitForTicks(20)
                const lockedWindow = actor.bot.currentWindow
                await clickFeedback(control(await snapshot(), 'Activation').slot, 'Server-locked mode', 'block.note_block.bass', 0.7)
                context.expect((await snapshot()).activation === 'MANUAL', 'Server-locked click leaves the server default selected')
                context.expect(actor.bot.currentWindow === lockedWindow, 'Server-locked click keeps the menu open')
                actor.bot.closeWindow(actor.bot.currentWindow)
                context.expect((await action(actor, 'lock', 'false')).activation === 'REACTIVE', 'Unlock restores the saved choice')
                evidence.trials.push({ name: 'server-lock', locked })
            })
            await context.step('menu toggles update the existing inventory without reopening', async () => {
                await action(actor, 'activation', 'MANUAL')
                await action(actor, 'open')
                await context.waitUntil(() => actor.bot.currentWindow, { label: 'Blink level menu opens', timeoutMs: 5000 })
                await actor.bot.waitForTicks(20)
                const before = await snapshot()
                context.expect(!control(before, 'Reactive direction'), 'Level-two Manual menu hides Reactive direction')
                const stableControls = ['Adaptation enabled', 'Wall phasing', 'Landing preference', 'Activation']
                    .map(label => ({ label, slot: control(before, label).slot }))
                const assertStableSlots = state => {
                    for (const { label, slot } of stableControls) {
                        context.expect(control(state, label)?.slot === slot, `${label} stays in its original slot`)
                    }
                    context.expect(state.slots.find(item => item.material === 'MILK_BUCKET')?.slot
                        === before.slots.find(item => item.material === 'MILK_BUCKET')?.slot, 'Reset stays in its original slot')
                }
                const enabled = before.slots.find(item => item.name.replace(/§./g, '') === 'Adaptation enabled')
                context.expect(Boolean(enabled), 'Blink menu exposes Enabled control')
                const window = actor.bot.currentWindow
                let openings = 0
                let closings = 0
                const onOpen = () => openings++
                const onClose = () => closings++
                actor.bot.on('windowOpen', onOpen)
                actor.bot.on('windowClose', onClose)
                try {
                    await clickFeedback(enabled.slot, 'Disable Blink')
                    const disabled = await stateEventually('enabled', 'OFF')
                    context.expect(actor.bot.currentWindow === window && openings === 0 && closings === 0, 'Toggle preserves the exact open window')
                    context.expect(disabled.slots.find(item => item.slot === enabled.slot)?.material === 'RED_STAINED_GLASS_PANE', 'Disabled slot turns red')
                    context.expect((await action(opponent, 'snapshot')).enabled === 'ON', 'Other player remains enabled')
                    await clickFeedback(enabled.slot, 'Enable Blink')
                    const restored = await stateEventually('enabled', 'ON')
                    context.expect(restored.slots.find(item => item.slot === enabled.slot)?.material === 'LIME_STAINED_GLASS_PANE', 'Enabled slot turns green')
                    context.expect(actor.bot.currentWindow === window && openings === 0 && closings === 0, 'Second toggle also preserves the window')
                    const manualActivation = control(await snapshot(), 'Activation')
                    await clickFeedback(manualActivation.slot, 'Enable Reactive mode')
                    const reactive = await stateEventually('activation', 'REACTIVE')
                    context.expect(Boolean(control(reactive, 'Reactive direction')), 'Switching to Reactive reveals direction in the current inventory')
                    assertStableSlots(reactive)
                    context.expect(actor.bot.currentWindow === window && openings === 0 && closings === 0, 'Showing a dependent control preserves the window')
                    for (const control of [
                        { label: 'Wall phasing', key: 'phasing', value: 'AIM', material: 'ENDER_EYE' },
                        { label: 'Landing preference', key: 'targeting', value: 'VERTICALITY', material: 'LADDER' },
                        { label: 'Reactive direction', key: 'reactive-direction', value: 'AWAY_FROM_ATTACKER', material: 'ENDER_PEARL' },
                    ]) {
                        const item = (await snapshot()).slots.find(item => item.name.replace(/§./g, '') === control.label)
                        context.expect(Boolean(item), `${control.label} is shown in the bottom controls`)
                        await clickFeedback(item.slot, control.label)
                        const selected = await stateEventually(control.key, control.value)
                        context.expect(selected.slots.find(candidate => candidate.slot === item.slot)?.material === control.material,
                            `${control.label} cycles its item type`)
                        context.expect(actor.bot.currentWindow === window && openings === 0 && closings === 0, `${control.label} preserves the window`)
                    }
                    const reactiveActivation = control(await snapshot(), 'Activation')
                    await clickFeedback(reactiveActivation.slot, 'Return to Manual mode')
                    const hidden = await stateEventually('activation', 'MANUAL')
                    context.expect(!control(hidden, 'Reactive direction'), 'Switching back to Manual removes Reactive direction')
                    assertStableSlots(hidden)
                    context.expect(hidden['reactive-direction'] === 'AWAY_FROM_ATTACKER', 'Hiding direction preserves its saved choice')
                    context.expect(actor.bot.currentWindow === window && openings === 0 && closings === 0, 'Hiding a dependent control preserves the window')
                    await clickFeedback(control(hidden, 'Activation').slot, 'Restore Reactive mode')
                    const shownAgain = await stateEventually('activation', 'REACTIVE')
                    context.expect(control(shownAgain, 'Reactive direction')?.material === 'ENDER_PEARL', 'Restored direction control retains the saved Away mode')
                    assertStableSlots(shownAgain)
                    const reset = shownAgain.slots.find(item => item.material === 'MILK_BUCKET')
                    context.expect(Boolean(reset), 'The menu exposes preference reset')
                    await clickFeedback(reset.slot, 'Reset changed preferences', 'ui.button.click', 0.9)
                    const resetState = await stateEventually('phasing', 'SNEAK')
                    context.expect(resetState.targeting === 'DISTANCE' && resetState.activation === 'MANUAL'
                        && resetState['reactive-direction'] === 'LOOK' && resetState.enabled === 'ON', 'Reset restores server defaults for every preference')
                    context.expect(!control(resetState, 'Reactive direction'), 'Reset hides direction when activation returns to Manual')
                    assertStableSlots(resetState)
                    context.expect(actor.bot.currentWindow === window && openings === 0 && closings === 0, 'Reset updates controls without reopening')
                    await clickFeedback(resetState.slots.find(item => item.material === 'MILK_BUCKET').slot,
                        'Reset default preferences', 'ui.button.click', 0.9)
                    context.expect(actor.bot.currentWindow === window && openings === 0 && closings === 0, 'No-op Reset also keeps the menu open')
                    await context.command('/adaptqa effects false', /^ADAPT_QA EFFECTS false$/, 5000)
                    const mutedSounds = []
                    const onMutedSound = packet => mutedSounds.push(decodeSoundPacket(actor.bot.registry, packet))
                    actor.bot._client.on('sound_effect', onMutedSound)
                    try {
                        await actor.bot.clickWindow(control(await snapshot(), 'Adaptation enabled').slot, 0, 0)
                        await stateEventually('enabled', 'OFF')
                        await actor.bot.waitForTicks(8)
                        context.expect(!mutedSounds.some(sound => sound.name === 'ui.button.click'),
                            'Player effects setting suppresses click feedback without blocking the preference change', { mutedSounds })
                        evidence.sounds.push({ label: 'Effects disabled', received: mutedSounds })
                    } finally {
                        actor.bot._client.off('sound_effect', onMutedSound)
                        await context.command('/adaptqa effects true', /^ADAPT_QA EFFECTS true$/, 5000)
                        await action(actor, 'enabled', 'ON')
                    }
                    evidence.trials.push({ name: 'menu-slot-updates', windowId: window.id, openings, closings, before, disabled, restored, reactive, hidden, shownAgain, resetState })
                } finally {
                    actor.bot.off('windowOpen', onOpen)
                    actor.bot.off('windowClose', onClose)
                    if (actor.bot.currentWindow) actor.bot.closeWindow(actor.bot.currentWindow)
                }
                await action(actor, 'targeting', 'DISTANCE')
                await action(actor, 'activation', 'REACTIVE')
                await action(actor, 'reactive-direction', 'LOOK')
            })
            await context.step('aim phasing and vertical targeting change actual manual destinations', async () => {
                await stage()
                await action(actor, 'terrain')
                await action(actor, 'activation', 'MANUAL')
                await action(actor, 'phasing', 'SNEAK')
                await readyCombat()
                await doubleJump()
                const blocked = await snapshot()
                context.expect(blocked.x < 8, 'Default phasing stops at the wall without sneak')
                await actor.bot.waitForTicks(45)
                await readyCombat()
                await action(actor, 'phasing', 'AIM')
                await doubleJump()
                const distance = await snapshot()
                context.expect(distance.x > 15 && distance.y < 103, 'Aim phasing crosses the wall without sneak and prefers distance')
                await actor.bot.waitForTicks(45)
                await readyCombat()
                await action(actor, 'targeting', 'VERTICALITY')
                const landings = await doubleJump()
                const vertical = await snapshot()
                context.expect(landings.some(position => position.x >= 8 && position.x < 9 && position.y >= 104.8),
                    'Vertical preference chooses the nearer raised landing before momentum carries the player onward', { landings })
                evidence.trials.push({ name: 'phasing-and-verticality', blocked, distance, vertical, landings })
                await action(actor, 'targeting', 'DISTANCE')
                await action(actor, 'activation', 'REACTIVE')
                await actor.bot.waitForTicks(45)
                await stage()
            })
            await context.step('reactive mode replaces manual Blink and evades an actual melee attack', async () => {
                await readyCombat()
                await action(opponent, 'weapons')
                await opponent.bot.equip(opponent.bot.inventory.items().find(item => item.name === 'wooden_sword'), 'hand')
                const beforeJump = await snapshot()
                await doubleJump()
                const afterJump = await snapshot()
                context.expect(afterJump.teleports === beforeJump.teleports, 'Reactive mode disables the double-jump trigger')
                await readyCombat()
                const before = await snapshot()
                await strike()
                const after = await snapshot()
                context.expect(after.teleports === before.teleports + 1 && after.x > before.x + 5, 'Melee hit triggers a real forward teleport')
                context.expect(after.attack?.cause === 'ENTITY_ATTACK' && after.attack.cancelled, 'The evaded melee hit is canceled')
                context.expect(after.health < before.health, 'Successful reactive Blink still pays its pearl damage cost')
                evidence.trials.push({ name: 'reactive-melee', beforeJump, afterJump, before, after })
                await action(actor, 'activation', 'MANUAL')
                const cooling = await snapshot()
                await doubleJump()
                context.expect((await snapshot()).teleports === cooling.teleports, 'Switching to Manual retains the successful Blink cooldown')
                await action(actor, 'activation', 'REACTIVE')
            })
            await context.step('denied reactive teleports leave incoming damage intact', async () => {
                await actor.bot.waitForTicks(45)
                await readyCombat()
                await action(actor, 'deny', 'true')
                const before = await snapshot()
                await strike()
                const after = await snapshot()
                context.expect(after.teleports === before.teleports, 'Canceled Blink does not teleport')
                context.expect(after.attack && !after.attack.cancelled && after.health < before.health, 'Canceled Blink does not provide free damage immunity')
                await action(actor, 'deny', 'false')
                evidence.trials.push({ name: 'denied-teleport', before, after })
            })
            await context.step('reactive direction can retreat from the attacker instead of following the view', async () => {
                await actor.bot.waitForTicks(45)
                await readyCombat()
                await action(actor, 'reactive-direction', 'AWAY_FROM_ATTACKER')
                const before = await snapshot()
                await strike()
                const after = await snapshot()
                context.expect(after.teleports === before.teleports + 1 && after.x < before.x - 5,
                    'Away mode teleports opposite the attacker while the player still faces forward')
                context.expect(after.attack?.cancelled, 'Away mode evades the triggering hit')
                await action(actor, 'reactive-direction', 'LOOK')
                evidence.trials.push({ name: 'reactive-away', before, after })
            })
            await context.step('reactive Blink evades an actual fired projectile', async () => {
                await actor.bot.waitForTicks(45)
                await readyCombat()
                await action(opponent, 'position', -8.5)
                await opponent.bot.equip(opponent.bot.inventory.items().find(item => item.name === 'bow'), 'hand')
                await opponent.bot.lookAt(actor.bot.entity.position.offset(0, 1.2, 0), true)
                const before = await snapshot()
                opponent.bot.activateItem()
                await opponent.bot.waitForTicks(22)
                opponent.bot.deactivateItem()
                await context.waitUntil(async () => (await snapshot()).teleports > before.teleports,
                    { label: 'arrow impact triggers reactive teleport', timeoutMs: 5000 })
                const after = await snapshot()
                context.expect(after.attack?.cause === 'PROJECTILE' && after.attack.cancelled, 'The fired arrow damage is evaded')
                evidence.trials.push({ name: 'reactive-projectile', before, after })
            })
            await context.step('all player preferences persist through disconnect and reconnect', async () => {
                await action(actor, 'phasing', 'AIM')
                await action(actor, 'targeting', 'VERTICALITY')
                await action(actor, 'reactive-direction', 'AWAY_FROM_ATTACKER')
                const before = await snapshot()
                for (const stop of stops.splice(0, 2)) stop()
                actor = await actor.reconnectAfter(() => actor.bot.quit('Blink preference persistence'), { timeoutMs: 30000 })
                await actor.bot.waitForTicks(20)
                const after = await snapshot()
                for (const key of ['enabled', 'phasing', 'targeting', 'activation', 'reactive-direction']) {
                    context.expect(after[key] === before[key], `${key} survives reconnect`)
                }
                evidence.retained = after
            })
        } finally {
            for (const player of [actor, opponent]) {
                player.bot.clearControlStates()
                player.bot.deactivateItem()
                if (player.bot.currentWindow) player.bot.closeWindow(player.bot.currentWindow)
            }
            if (setup && !context.signal.aborted) {
                await action(actor, 'lock', 'false')
                await action(actor, 'deny', 'false')
                await context.command('/adaptqa cleanup', /^ADAPT_QA CLEANUP$/, 15000)
            }
            for (const stop of stops) stop()
        }
    },
}
