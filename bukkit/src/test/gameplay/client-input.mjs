import { compareVersions } from './version-profile.mjs'

const FLAG_FLYING = 2
const JUMP_TRIGGER_TICKS = 7
const GRANT_WAIT_TICKS = 10
const LOOK_AROUND_RADIANS = 0.4

/** Servers before 1.21.2 never see the jump key: ViaProxy drops it from the 26.1 bots' player input packet. */
export function serverSeesJumpKey(version = process.env.ADAPT_QA_MINECRAFT_VERSION) {
    return !version || compareVersions(version, '1.21.2') >= 0
}

/**
 * The vanilla client's double tap of jump while it may fly (LocalPlayer.aiStep): a press starts a 7-tick window and a
 * second press inside it asks the server to start flying. mineflayer leaves this out; servers before 1.21.2 learn of
 * mid-air jumps only through that request.
 */
export function vanillaFlightToggle(bot) {
    let triggerTicks = 0
    let wasJumping = false
    bot.flightRequests = 0
    bot.on('physicsTick', () => {
        if (triggerTicks > 0) triggerTicks--
        const jumping = Boolean(bot.controlState?.jump)
        if (jumping && !wasJumping && bot.abilities?.mayFly) {
            if (triggerTicks === 0) {
                triggerTicks = JUMP_TRIGGER_TICKS
            } else {
                triggerTicks = 0
                if (!bot.entity?.isInWater) {
                    bot._client.write('abilities', { flags: bot.abilities.flying ? 0 : FLAG_FLYING })
                    bot.flightRequests++
                }
            }
        }
        wasJumping = jumping
    })
}

async function tap(bot, ticks) {
    bot.setControlState('jump', true)
    await bot.waitForTicks(ticks)
    bot.setControlState('jump', false)
}

/**
 * A player's double jump: jump, then press jump once more in mid-air, 4 ticks after the first press. Servers before
 * 1.21.2 see the second press as the vanilla client's flight request, which they let the client make while it still
 * stands, so the jump press opens the 7-tick double tap window. The server grants that on a move of a player whose
 * double jump adaptation listens; a bot that has not moved since it learned the adaptation looks around first, as a
 * player does. Returns whether the player left the ground, whether it could ask to fly before jumping and how many
 * times it asked.
 */
export async function doubleJump(bot) {
    const requestsBefore = bot.flightRequests ?? 0
    try {
        if (!serverSeesJumpKey()) await awaitGroundGrant(bot)
        const mayFly = Boolean(bot.abilities?.mayFly)
        await tap(bot, 2)
        const airborne = !bot.entity.onGround
        await bot.waitForTicks(2)
        await tap(bot, 2)
        return { airborne, mayFly, flightRequests: (bot.flightRequests ?? 0) - requestsBefore }
    } finally {
        bot.setControlState('jump', false)
    }
}

async function awaitGroundGrant(bot) {
    if (bot.abilities?.mayFly || bot.game?.gameMode === 'creative') return
    const { yaw, pitch } = bot.entity
    await bot.look(yaw + LOOK_AROUND_RADIANS, pitch, true)
    await bot.waitForTicks(2)
    await bot.look(yaw, pitch, true)
    for (let tick = 0; tick < GRANT_WAIT_TICKS && !bot.abilities?.mayFly; tick++) {
        await bot.waitForTicks(1)
    }
}
