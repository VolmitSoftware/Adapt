import { randomBytes } from 'node:crypto'
import { synchronizePlayerInput } from './player-input.mjs'
import { synchronizePlayerVelocity } from './player-velocity.mjs'

export default {
    name: 'adapt-blink-folia',
    description: 'Exercise Blink menu changes and reactive melee teleport through production commands on Folia.',
    async run(context) {
        const suffix = randomBytes(4).toString('hex')
        const actor = await context.connectActor(`AQAf${suffix}`)
        const opponent = await context.connectActor(`AQAg${suffix}`)
        const stops = [actor, opponent].flatMap(player => [synchronizePlayerInput(player.bot), synchronizePlayerVelocity(player.bot)])
        const evidence = context.report.blink = { player: actor.bot.username, opponent: opponent.bot.username }
        async function command(text) {
            await context.command(text)
            await context.bot.waitForTicks(10)
        }
        try {
            await command('/gamemode spectator @s')
            await command('/tp @s 0.5 105 0.5')
            await command('/fill -20 99 -4 30 99 4 stone')
            await command('/fill -20 100 -4 30 108 4 air')
            await command(`/tp ${actor.bot.username} 0.5 100 0.5 -90 0`)
            await command(`/tp ${opponent.bot.username} 2.5 100 0.5 90 0`)
            await command(`/gamemode survival ${actor.bot.username}`)
            await command(`/gamemode survival ${opponent.bot.username}`)
            await command(`/adapt claim-adaptation rift:rift-blink 1 force=true player=${actor.bot.username}`)
            await context.step('Folia level-one menu hides Reactive direction', async () => {
                await command(`/adapt gui target=adaptation:rift-blink player=${actor.bot.username} force=true`)
                await context.waitUntil(() => actor.bot.currentWindow, { label: 'Level-one Blink menu on Folia', timeoutMs: 5000 })
                await actor.bot.waitForTicks(20)
                const window = actor.bot.currentWindow
                const controls = window.slots.slice(window.inventoryStart - 9, window.inventoryStart)
                context.expect(!controls.some(item => item?.name === 'spyglass' || item?.name === 'ender_pearl'),
                    'Level-one Blink has no Reactive direction control')
                actor.bot.closeWindow(window)
            })
            await command(`/adapt claim-adaptation rift:rift-blink 2 force=true player=${actor.bot.username}`)
            await context.step('Folia player menu updates preference slots on the owning region', async () => {
                await command(`/adapt gui target=adaptation:rift-blink player=${actor.bot.username} force=true`)
                await context.waitUntil(() => actor.bot.currentWindow, { label: 'Blink menu on Folia', timeoutMs: 5000 })
                await actor.bot.waitForTicks(20)
                const window = actor.bot.currentWindow
                const first = window.inventoryStart - 9
                const controls = () => window.slots.slice(first, window.inventoryStart)
                const enabled = controls().find(item => item?.name === 'lime_stained_glass_pane')
                const activation = controls().find(item => item?.name === 'feather')
                context.expect(Boolean(enabled) && Boolean(activation), 'Level-two Blink has enabled and manual controls')
                context.expect(!controls().some(item => item?.name === 'spyglass' || item?.name === 'ender_pearl'),
                    'Level-two Manual mode hides Reactive direction')
                await actor.bot.clickWindow(enabled.slot, 0, 0)
                await context.waitUntil(() => window.slots[enabled.slot]?.name === 'red_stained_glass_pane',
                    { label: 'Folia enabled toggle turns red', timeoutMs: 5000 })
                context.expect(actor.bot.currentWindow === window, 'Folia toggle keeps the current inventory')
                await actor.bot.clickWindow(enabled.slot, 0, 0)
                await context.waitUntil(() => window.slots[enabled.slot]?.name === 'lime_stained_glass_pane',
                    { label: 'Folia enabled toggle turns green', timeoutMs: 5000 })
                await actor.bot.clickWindow(activation.slot, 0, 0)
                await context.waitUntil(() => controls().some(item => item?.name === 'shield') && controls().some(item => item?.name === 'spyglass'),
                    { label: 'Folia activation switches to Reactive', timeoutMs: 5000 })
                context.expect(actor.bot.currentWindow === window, 'Folia mode cycle keeps the current inventory')
                const direction = controls().find(item => item?.name === 'spyglass')
                await actor.bot.clickWindow(direction.slot, 0, 0)
                await context.waitUntil(() => controls().some(item => item?.name === 'ender_pearl'),
                    { label: 'Folia direction selects Away', timeoutMs: 5000 })
                await actor.bot.clickWindow(controls().find(item => item?.name === 'shield').slot, 0, 0)
                await context.waitUntil(() => controls().some(item => item?.name === 'feather')
                    && !controls().some(item => item?.name === 'spyglass' || item?.name === 'ender_pearl'),
                    { label: 'Folia Manual mode hides direction', timeoutMs: 5000 })
                context.expect(actor.bot.currentWindow === window, 'Folia hides the dependent control in the same inventory')
                await actor.bot.clickWindow(controls().find(item => item?.name === 'feather').slot, 0, 0)
                await context.waitUntil(() => controls().some(item => item?.name === 'ender_pearl'),
                    { label: 'Folia Reactive mode restores the saved Away choice', timeoutMs: 5000 })
                await actor.bot.clickWindow(controls().find(item => item?.name === 'milk_bucket').slot, 0, 0)
                await context.waitUntil(() => controls().some(item => item?.name === 'feather')
                    && !controls().some(item => item?.name === 'spyglass' || item?.name === 'ender_pearl'),
                    { label: 'Folia Reset hides direction', timeoutMs: 5000 })
                context.expect(actor.bot.currentWindow === window, 'Folia Reset preserves the current inventory')
                await actor.bot.clickWindow(controls().find(item => item?.name === 'feather').slot, 0, 0)
                await context.waitUntil(() => controls().some(item => item?.name === 'spyglass'),
                    { label: 'Folia Reset restored Look direction', timeoutMs: 5000 })
                evidence.menu = { windowId: window.id, enabledSlot: enabled.slot, activationSlot: activation.slot }
                actor.bot.closeWindow(window)
            })
            await context.step('Folia reactive melee teleports and retains the pearl health cost', async () => {
                await actor.bot.look(-Math.PI / 2, 0, true)
                await command(`/give ${opponent.bot.username} wooden_sword`)
                await opponent.bot.equip(opponent.bot.inventory.items().find(item => item.name === 'wooden_sword'), 'hand')
                await opponent.bot.waitForTicks(22)
                const target = opponent.bot.players[actor.bot.username]?.entity
                context.expect(Boolean(target), 'Opponent sees the player on Folia')
                const before = { position: actor.bot.entity.position.clone(), health: actor.bot.health }
                await opponent.bot.lookAt(target.position.offset(0, 1, 0), true)
                opponent.bot.attack(target)
                await context.waitUntil(() => actor.bot.entity.position.x > before.position.x + 5,
                    { label: 'Folia reactive teleport completes', timeoutMs: 5000 })
                await actor.bot.waitForTicks(6)
                context.expect(actor.bot.health < before.health && actor.bot.health > 0, 'Reactive teleport charges pearl damage on Folia')
                evidence.combat = { before, after: { position: actor.bot.entity.position.clone(), health: actor.bot.health } }
            })
        } finally {
            for (const player of [actor, opponent]) {
                player.bot.clearControlStates()
                player.bot.deactivateItem()
                if (player.bot.currentWindow) player.bot.closeWindow(player.bot.currentWindow)
            }
            for (const stop of stops) stop()
        }
    },
}
