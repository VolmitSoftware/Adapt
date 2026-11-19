import { compareVersions } from './version-profile.mjs'

const FLAG_FLYING = 2
const JUMP_TRIGGER_TICKS = 7
const GRANT_WAIT_TICKS = 8

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
 * A player's double jump: jump, then press jump again in mid-air. Where the server lets the client ask to fly only once
 * it is airborne (servers before 1.21.2), the ground press cannot count, so the player taps twice in mid-air, as on an
 * old client. Returns whether the player left the ground, whether the server let it ask to fly and how many times it asked.
 */
export async function doubleJump(bot) {
    const requestsBefore = bot.flightRequests ?? 0
    try {
        if (!serverSeesJumpKey()) return await midairDoubleTap(bot, requestsBefore)
        await tap(bot, 4)
        const airborne = !bot.entity.onGround
        await bot.waitForTicks(2)
        await tap(bot, 3)
        const mayFly = Boolean(bot.abilities?.mayFly)
        if (mayFly && bot.game?.gameMode !== 'creative') {
            await bot.waitForTicks(1)
            await tap(bot, 2)
        }
        return { airborne, mayFly, flightRequests: (bot.flightRequests ?? 0) - requestsBefore }
    } finally {
        bot.setControlState('jump', false)
    }
}

/**
 * Before 1.21.2: jump, wait for the server's may-fly grant (it arrives a few ticks after the server sees the player
 * airborne, later under load), then double tap quickly so the request goes out near the top of the jump, where a
 * player tapping twice in a row would send it.
 */
async function midairDoubleTap(bot, requestsBefore) {
    await tap(bot, 2)
    const airborne = !bot.entity.onGround
    for (let tick = 0; tick < GRANT_WAIT_TICKS && !bot.abilities?.mayFly && !bot.entity.onGround; tick++) {
        await bot.waitForTicks(1)
    }
    const mayFly = Boolean(bot.abilities?.mayFly)
    if (mayFly && bot.game?.gameMode !== 'creative') {
        await bot.waitForTicks(1)
        await tap(bot, 1)
        await bot.waitForTicks(1)
        await tap(bot, 1)
    }
    return { airborne, mayFly, flightRequests: (bot.flightRequests ?? 0) - requestsBefore }
}
