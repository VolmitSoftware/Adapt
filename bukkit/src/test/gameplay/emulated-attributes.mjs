// Servers older than an attribute have no value to report for it: Adapt's core AttributeEmulator stands in with an
// effect the vanilla client acts on (Haste or Mining Fatigue, Jump Boost, Speed, Slow Falling, Dolphin's Grace).
// readClientAttributes measures what the actor's client does with the effects it received and expresses it in the
// attribute's own units, so a contract comparing attribute values compares the player-visible result instead:
// mine time for the mining speeds, jump height for jump strength, fall acceleration for gravity, the sneaking,
// soul sand and swimming speed multipliers, and the absorption the player holds where no absorption cap exists.

const REFERENCE_BLOCK = 'obsidian'
const GRAVITY = 0.08
const SLOW_FALLING_GRAVITY = 0.01
const DRAG = 0.98
const SOUL_SAND_SPEED = 0.4

function effectLevel(bot, name) {
    const id = bot.registry.effectsByName[name]?.id
    if (id === undefined) throw new Error(`Client registry has no ${name} effect`)
    const effect = bot.entity.effects?.[id]
    return effect ? effect.amplifier + 1 : 0
}

// The client's block class comes from any block it has loaded; right after joining it may have none yet.
function referenceBlock(bot) {
    const loaded = bot.entity?.position ? bot.blockAt(bot.entity.position.floored()) : null
    const state = bot.registry.blocksByName[REFERENCE_BLOCK]?.defaultState
    if (state === undefined) throw new Error('Client registry has no mine time reference block')
    return loaded ? loaded.constructor.fromStateId(state, 0) : null
}

/** Client mine time in ms of the reference block by hand, with and without the effects the client holds; null before any block is loaded. */
export function mineTime(bot, inWater) {
    const block = referenceBlock(bot)
    if (!block) return null
    return {
        withEffects: block.digTime(null, false, inWater, false, [], bot.entity.effects ?? {}),
        withoutEffects: block.digTime(null, false, inWater, false, [], {}),
    }
}

/** Peak height in blocks of a standing jump with the given take-off velocity, under vanilla gravity and drag. */
export function jumpHeight(velocity) {
    let y = 0
    let motion = velocity
    while (motion > 0) {
        y += motion
        motion = (motion - GRAVITY) * DRAG
    }
    return y
}

function speedMultiplier(bot) {
    return (1 + 0.2 * effectLevel(bot, 'Speed')) * Math.max(0, 1 - 0.15 * effectLevel(bot, 'Slowness'))
}

const readers = {
    'minecraft:block_break_speed': bot => {
        const time = mineTime(bot, false)
        return time && { value: time.withoutEffects / time.withEffects, measured: { mineTimeMs: time.withEffects, baselineMineTimeMs: time.withoutEffects } }
    },
    'minecraft:submerged_mining_speed': bot => {
        const time = mineTime(bot, true)
        return time && { value: 0.2 * time.withoutEffects / time.withEffects, measured: { underwaterMineTimeMs: time.withEffects, baselineUnderwaterMineTimeMs: time.withoutEffects } }
    },
    'minecraft:jump_strength': bot => {
        const velocity = 0.42 + 0.1 * effectLevel(bot, 'JumpBoost')
        return { value: velocity, measured: { jumpHeight: jumpHeight(velocity) } }
    },
    'minecraft:gravity': bot => {
        const gravity = effectLevel(bot, 'SlowFalling') > 0 ? SLOW_FALLING_GRAVITY : GRAVITY
        return { value: gravity, measured: { fallAcceleration: gravity } }
    },
    'minecraft:sneaking_speed': bot => {
        const multiplier = speedMultiplier(bot)
        return { value: 0.3 * multiplier, measured: { sneakSpeedMultiplier: multiplier } }
    },
    'minecraft:movement_efficiency': bot => {
        const multiplier = speedMultiplier(bot)
        const efficiency = Math.min(1, Math.max(0, (SOUL_SAND_SPEED * multiplier - SOUL_SAND_SPEED) / (1 - SOUL_SAND_SPEED)))
        return { value: efficiency, measured: { soulSandSpeedMultiplier: SOUL_SAND_SPEED * multiplier } }
    },
    'minecraft:water_movement_efficiency': bot => {
        const grace = effectLevel(bot, 'DolphinsGrace') > 0
        return { value: grace ? 1 : 0, measured: { waterInertia: grace ? 0.96 : 0.8 } }
    },
    'minecraft:max_absorption': (bot, state) => {
        if (typeof state.absorption !== 'number') throw new Error('Snapshot reports no absorption')
        return { value: state.absorption, measured: { absorption: state.absorption } }
    },
}

/** Attributes whose emulation the actor's client can show by itself. */
export const CLIENT_MEASURED_ATTRIBUTES = Object.freeze(Object.keys(readers))

/**
 * Fills every client-measured attribute the server did not report into state.attributes from the actor's client,
 * and lists the measurements under state.clientMeasuredAttributes. Attributes the server has are left untouched.
 */
export function readClientAttributes(state, bot) {
    const measured = {}
    for (const [attribute, read] of Object.entries(readers)) {
        if (state.attributes?.[attribute] !== undefined) continue
        const reading = read(bot, state)
        if (!reading) continue
        state.attributes = { ...state.attributes, [attribute]: reading.value }
        measured[attribute] = reading.measured
    }
    if (Object.keys(measured).length) state.clientMeasuredAttributes = measured
    return state
}
