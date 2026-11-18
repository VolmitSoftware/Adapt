function point(actor, x, y, z) {
    return actor.bot.entity.position.clone().set(x, y, z)
}

function count(actor, name) {
    return actor.bot.inventory.slots.reduce((sum, item) => sum + (item?.name === name ? item.count : 0), 0)
}

function report(assertions, unlearned, active) {
    return { assertions, measurements: { unlearned, active } }
}

function prepare(item) {
    return async ({ actor, equip }) => {
        actor.bot.clearControlStates()
        if (item) await equip(item)
        await actor.bot.waitForTicks(5)
    }
}

async function tapBlock(input, position, face = 1) {
    const { actor } = input
    const offset = face === 4 ? [0, 0.5, 0.5] : [0.5, 1, 0.5]
    await actor.bot.lookAt(position.offset(...offset), true)
    actor.bot._client.write('block_dig', { status: 0, location: position, face, sequence: 0 })
    await actor.bot.waitForTicks(2)
    actor.bot._client.write('block_dig', { status: 1, location: position, face, sequence: 1 })
}

async function target(input) {
    const state = (await input.snapshot()).utility.target
    input.context.expect(Boolean(state), 'Utility target exists')
    await input.context.waitUntil(() => Boolean(input.actor.bot.entities[state.entityId]), { label: 'utility target visible', timeoutMs: 5000 })
    return input.actor.bot.entities[state.entityId]
}

async function catReflexes(input) {
    const { actor, opponent, context, snapshot, equip } = input
    await equip('bow', opponent)
    const before = await snapshot()
    let after = before
    let shots = 0
    try {
        while (shots < 40) {
            const hits = after.utility.events.filter(event => event.type === 'projectile' && event.sprinting)
            if (hits.some(event => event.cancelled) || new Set(hits.map(event => event.projectileId)).size >= 20) break
            const direction = actor.bot.entity.position.z > 0.5 ? -1 : 1
            await actor.bot.lookAt(point(actor, 0.5, 101.6, actor.bot.entity.position.z + direction * 12), true)
            await actor.bot.waitForTicks(2)
            opponent.bot.activateItem()
            await actor.bot.waitForTicks(8)
            actor.bot.setControlState('sprint', true)
            actor.bot.setControlState('forward', true)
            await actor.bot.waitForTicks(6)
            const victim = opponent.bot.entities[actor.bot.entity.id]
            context.expect(Boolean(victim), 'Sprinting defender visible to real archer')
            await opponent.bot.lookAt(victim.position.offset(0, 1.2, 0), true)
            await opponent.bot.waitForTicks(2)
            opponent.bot.deactivateItem()
            await actor.bot.waitForTicks(9)
            actor.bot.clearControlStates()
            await actor.bot.waitForTicks(5)
            shots++
            after = await snapshot()
            context.expect(after.health > 0, 'Armored natural projectile target survives bounded dodge trials')
        }
        const eligibleHits = new Set(after.utility.events.filter(event => event.type === 'projectile' && event.sprinting).map(event => event.projectileId)).size
        return { before, after, shots, eligibleHits, noDodgeRisk: 0.65 ** eligibleHits }
    } finally {
        actor.bot.clearControlStates()
        opponent.bot.deactivateItem()
    }
}

async function wall(input) {
    const { actor, snapshot } = input
    const before = await snapshot()
    await actor.bot.lookAt(point(actor, 4, 101.6, 0.5), true)
    actor.bot.setControlState('jump', true)
    await actor.bot.waitForTicks(4)
    actor.bot.setControlState('jump', false)
    actor.bot.setControlState('sneak', true)
    await actor.bot.waitForTicks(6)
    const latched = await snapshot()
    actor.bot.setControlState('sneak', false)
    await actor.bot.waitForTicks(4)
    const kicked = await snapshot()
    await actor.bot.waitForTicks(25)
    return { before, latched, kicked, after: await snapshot() }
}

async function chalk(input) {
    const { actor, opponent, snapshot } = input
    const owned = new Set()
    const other = new Set()
    const collect = set => entity => { if (entity.name === 'block_display') set.add(entity.id) }
    const ownerListener = collect(owned)
    const otherListener = collect(other)
    actor.bot.on('entitySpawn', ownerListener)
    opponent.bot.on('entitySpawn', otherListener)
    try {
        await tapBlock(input, point(actor, 2, 99, -1))
        const endpoint = actor.bot.blockAt(point(actor, 2, 99, 2))
        await actor.bot.activateBlock(endpoint, point(actor, 0, 1, 0))
        await actor.bot.waitForTicks(25)
        const drawn = await snapshot()
        actor.bot.setControlState('sneak', true)
        await actor.bot.waitForTicks(3)
        await actor.bot.lookAt(actor.bot.entity.position.offset(0, 5, -8), true)
        actor.bot.activateItem()
        actor.bot.deactivateItem()
        await actor.bot.waitForTicks(5)
        return { drawn, cleared: await snapshot(), ownerDisplays: owned.size, observerDisplays: other.size }
    } finally {
        actor.bot.clearControlStates()
        actor.bot.off('entitySpawn', ownerListener)
        opponent.bot.off('entitySpawn', otherListener)
    }
}

async function elevator(input) {
    const { actor, snapshot, context } = input
    const lower = actor.bot.blockAt(point(actor, 2, 98, 0))
    const upper = actor.bot.blockAt(point(actor, 3, 102, 0))
    await actor.bot.activateBlock(lower, point(actor, 0, 1, 0))
    await actor.bot.waitForTicks(5)
    await actor.bot.activateBlock(upper, point(actor, -1, 0, 0))
    await actor.bot.waitForTicks(5)
    const placed = await snapshot()
    await actor.bot.lookAt(point(actor, 6, 101.6, 0.5), true)
    actor.bot.setControlState('forward', true)
    try {
        await context.waitUntil(() => actor.bot.entity.position.x >= 2.2, { label: 'walk onto lower elevator location', timeoutMs: 4000, intervalMs: 20 })
    } finally {
        actor.bot.setControlState('forward', false)
    }
    await actor.bot.waitForTicks(8)
    actor.bot.setControlState('jump', true)
    await actor.bot.waitForTicks(4)
    actor.bot.setControlState('jump', false)
    await actor.bot.waitForTicks(15)
    const ascended = await snapshot()
    actor.bot.setControlState('sneak', true)
    await actor.bot.waitForTicks(10)
    actor.bot.setControlState('sneak', false)
    return { placed, ascended, descended: await snapshot(), remaining: count(actor, 'note_block') }
}

async function wireless(input) {
    const { actor, snapshot } = input
    const powered = []
    const observe = (oldBlock, newBlock) => {
        if (newBlock?.position.equals(point(actor, 3, 100, 0))) powered.push(String(newBlock.getProperties().lit) === 'true')
    }
    actor.bot.on('blockUpdate', observe)
    try {
        actor.bot.setControlState('sneak', true)
        await actor.bot.waitForTicks(3)
        await tapBlock(input, point(actor, 3, 100, 0), 4)
        await actor.bot.waitForTicks(5)
        actor.bot.setControlState('sneak', false)
        await actor.bot.lookAt(actor.bot.entity.position.offset(0, 4, -8), true)
        actor.bot.activateItem()
        actor.bot.deactivateItem()
        await actor.bot.waitForTicks(15)
        return { powered, after: await snapshot(), torches: count(actor, 'redstone_torch') }
    } finally {
        actor.bot.clearControlStates()
        actor.bot.off('blockUpdate', observe)
    }
}

async function cutpurse(input) {
    const { actor, snapshot, context } = input
    const victim = await target(input)
    const before = await snapshot()
    let after = before
    let trials = 0
    actor.bot.setControlState('sneak', true)
    try {
        await actor.bot.waitForTicks(5)
        while (count(actor, 'emerald') === 0 && count(actor, 'iron_axe') === 0 && trials < 32) {
            await actor.bot.lookAt(victim.position.offset(0, 0.8, 0), true)
            actor.bot.attack(victim)
            await actor.bot.waitForTicks(12)
            trials++
            after = await snapshot()
            context.expect(after.utility.target.health > 0, 'Pickpocket target survives every natural fist hit')
        }
        return { before, after, trials, emeralds: count(actor, 'emerald'), axes: count(actor, 'iron_axe'), noEmeraldRiskAtLimit: 0.675 ** 32 }
    } finally {
        actor.bot.setControlState('sneak', false)
    }
}

async function createDecoy(input) {
    input.actor.bot.setControlState('sneak', true)
    await input.actor.bot.waitForTicks(3)
    input.actor.bot.setControlState('sneak', false)
    await input.actor.bot.waitForTicks(8)
}

async function shadow(input) {
    const { actor, opponent, snapshot } = input
    const avatars = new Set()
    const observe = entity => { if (entity.type === 'player' && entity.id !== actor.bot.entity.id && entity.id !== opponent.bot.entity.id) avatars.add(entity.id) }
    opponent.bot.on('entitySpawn', observe)
    try {
        await createDecoy(input)
        const active = await snapshot()
        await actor.bot.waitForTicks(155)
        return { active, expired: await snapshot(), avatars: avatars.size }
    } finally {
        opponent.bot.off('entitySpawn', observe)
    }
}

async function swap(input) {
    const { actor, snapshot } = input
    await createDecoy(input)
    const initial = await snapshot()
    input.context.expect(initial.utility.anchors.length === 1, 'Prerequisite spawns one real decoy anchor before the swap trial')
    await actor.bot.lookAt(actor.bot.entity.position.offset(0, 1.6, 12), true)
    actor.bot.setControlState('forward', true)
    await actor.bot.waitForTicks(20)
    actor.bot.setControlState('forward', false)
    await actor.bot.waitForTicks(3)
    const departed = await snapshot()
    actor.bot.setControlState('sneak', true)
    await actor.bot.waitForTicks(2)
    actor.bot.setControlState('sneak', false)
    await actor.bot.waitForTicks(2)
    actor.bot.setControlState('sneak', true)
    await actor.bot.waitForTicks(8)
    actor.bot.setControlState('sneak', false)
    return { initial, departed, after: await snapshot() }
}

async function veil(input) {
    const victim = await target(input)
    const before = await input.snapshot()
    await input.actor.bot.lookAt(victim.position.offset(0, 2.55, 0), true)
    let after
    await input.context.waitUntil(async () => {
        after = await input.snapshot()
        return after.utility.events.some(event => event.type === 'stare' && !event.cancelled)
            || after.utility.veilSuppressions > before.utility.veilSuppressions
    }, { label: 'natural Enderman direct gaze selection', timeoutMs: 6000, intervalMs: 100 })
    await input.actor.bot.waitForTicks(3)
    return { before, after: await input.snapshot() }
}

async function trap(input) {
    const { actor, snapshot } = input
    actor.bot.setControlState('sneak', true)
    await actor.bot.waitForTicks(15)
    actor.bot.setControlState('sneak', false)
    await actor.bot.waitForTicks(45)
    await actor.bot.lookAt(actor.bot.entity.position.offset(0, 1.6, 10), true)
    actor.bot.setControlState('forward', true)
    await actor.bot.waitForTicks(15)
    actor.bot.setControlState('forward', false)
    await actor.bot.waitForTicks(15)
    return { after: await snapshot() }
}

export const utilityBehaviorCases = new Map([
    ['agility-cat-reflexes', {
        stage: 'utility-cat', trigger: catReflexes, negativeWindowTicks: 10,
        description: 'bounded real arrows are cancelled only when the learned defender is sprinting',
        sound: 'minecraft:entity.player.attack.sweep', soundVolume: 0.5, soundPitch: 1.7,
        particle: 'crit', particleCount: 6, particleOffset: 0.2,
        async verify({ context, unlearned, active }) {
            const control = unlearned.after.utility.events.filter(event => event.type === 'projectile' && event.sprinting)
            const dodges = active.after.utility.events.filter(event => event.type === 'projectile' && event.cancelled && event.sprinting)
            context.expect(new Set(control.map(event => event.projectileId)).size >= 20 && control.every(event => !event.cancelled), 'Twenty unlearned real arrows remain uncancelled')
            context.expect(dodges.length > 0, `Learned sprinting must cancel an actual projectile hit; ${active.eligibleHits} eligible hits, no-dodge risk ${active.noDodgeRisk}`)
            return report(['twenty natural unlearned projectile hits cause damage', 'learned sprinting cancels actual incoming projectile damage'], unlearned, active)
        },
    }],
    ['agility-wall-jump', {
        stage: 'utility-wall', trigger: wall, negativeWindowTicks: 10,
        description: 'an airborne sneak latches to a real wall and releasing sneak kicks upward',
        sound: 'minecraft:item.armor.equip_leather', soundVolume: 0.9, soundPitch: 0.92, particle: 'cloud',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.latched.attributes['minecraft:gravity'] === unlearned.before.attributes['minecraft:gravity'], 'Unlearned wall contact keeps normal gravity')
            context.expect(active.latched.attributes['minecraft:gravity'] < active.before.attributes['minecraft:gravity'], 'Learned actual wall latch suppresses gravity')
            context.expect(active.kicked.location.y > unlearned.kicked.location.y + 0.5, 'Learned release gains actual height above the ordinary jump')
            context.expect(active.after.attributes['minecraft:gravity'] === active.before.attributes['minecraft:gravity'], 'Releasing the wall restores normal gravity')
            return report(['unlearned contact has no gravity modifier', 'learned wall latch changes gravity and natural release gains height', 'release restores gravity'], unlearned, active)
        },
    }],
    ['architect-chalk-line', {
        stage: 'utility-chalk', prepare: prepare('stick'), trigger: chalk, negativeWindowTicks: 10,
        description: 'natural wand clicks store endpoints, draw a private guide, and clear its saved plan',
        sound: 'minecraft:block.amethyst_block.chime', soundVolume: 0.45, soundPitch: 1.7,
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.drawn.utility.chalkPoints.length === 0 && unlearned.ownerDisplays === 0, 'Unlearned wand cannot store a plan or show guides')
            context.expect(active.drawn.utility.chalkPoints.length === 2 && active.ownerDisplays > 0 && active.observerDisplays === 0, 'Learned actual endpoints create private client-visible guide displays')
            context.expect(active.cleared.utility.chalkPoints.length === 0, 'Sneak use clears the actual saved wand plan')
            return report(['unlearned wand is denied', 'learned two-point guide is stored on the real item and displayed privately', 'sneak use clears the saved plan'], unlearned, active)
        },
    }],
    ['architect-elevator', {
        stage: 'utility-elevator', prepare: prepare('note_block'), trigger: elevator, negativeWindowTicks: 10,
        description: 'two naturally placed elevator items link and carry the player up and down',
        sound: 'minecraft:entity.enderman.teleport', soundVolume: 0.6, soundPitch: 1.2, particle: 'portal',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.remaining === 2 && unlearned.placed.utility.lowerElevator === 'AIR' && unlearned.placed.utility.upperElevator === 'AIR', 'Unlearned placement denies both prepared elevator blocks')
            context.expect(active.remaining === 0 && active.placed.utility.lowerElevator === 'NOTE_BLOCK' && active.placed.utility.upperElevator === 'NOTE_BLOCK', 'Learned natural placements create two real elevator blocks')
            context.expect(Math.abs(active.ascended.location.y - 103) < 0.15 && Math.abs(active.descended.location.y - 100) < 0.15, 'Actual jump travels upward and actual sneak returns down the linked elevators')
            return report(['unlearned placements preserve both items', 'learned placements consume and link two blocks', 'jump and sneak produce actual vertical round-trip travel'], unlearned, active)
        },
    }],
    ['architect-wireless-redstone', {
        stage: 'utility-wireless', prepare: prepare('redstone_torch'), trigger: wireless, negativeWindowTicks: 10,
        description: 'natural binding and remote use pulse a real lamp and restore its original state',
        sound: 'minecraft:block.note_block.pling', soundVolume: 0.5, soundPitch: 2, particle: 'electric_spark',
        async verify({ context, unlearned, active }) {
            context.expect(!unlearned.powered.includes(true) && !unlearned.after.utility.lamp, 'Unlearned remote does not power the real lamp')
            context.expect(active.powered.includes(true) && active.powered.includes(false) && !active.after.utility.lamp, 'Learned use lights then naturally restores the actual lamp')
            context.expect(active.torches === 1, 'Binding and pulse preserve one reusable remote')
            return report(['unlearned remote leaves lamp dark', 'learned natural bind/use sends real lit and unlit block updates'], unlearned, active)
        },
    }],
    ['stealth-cutpurse', {
        stage: 'utility-cutpurse', prerequisites: [{ name: 'stealth-silent-step', level: 1 }], trigger: cutpurse, negativeWindowTicks: 10,
        description: 'bounded undetected fist hits steal real loot from a surviving Vindicator',
        sound: 'minecraft:entity.item.pickup', soundVolume: 0.6, soundPitch: 1.5, particle: 'enchant',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.trials === 32 && unlearned.emeralds + unlearned.axes === 0, 'Thirty-two unlearned undetected hits grant no loot')
            context.expect(active.emeralds + active.axes > 0 && active.after.utility.target.health > 0, `Learned hits steal actual loot without killing the mob; no-emerald risk ${active.noEmeraldRiskAtLimit}`)
            return report(['unlearned surviving target yields no loot', 'learned natural undetected hits transfer loot from a living target'], unlearned, active)
        },
    }],
    ['stealth-shadow-decoy', {
        stage: 'utility-decoy', trigger: shadow, negativeWindowTicks: 10,
        description: 'releasing sneak creates a real anchor and client-visible player decoy, then expires',
        sound: 'minecraft:entity.enderman.teleport', soundVolume: 0.6, soundPitch: 1.7, particle: 'reverse_portal',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.active.utility.anchors.length === 0 && unlearned.avatars === 0, 'Unlearned sneak release creates no decoy')
            context.expect(active.active.utility.anchors.length === 1 && active.avatars > 0, 'Learned release creates a real anchor and fake-player entity visible to the observer')
            context.expect(active.active.effects.some(effect => effect.type === 'minecraft:invisibility'), 'Real decoy temporarily conceals its owner')
            context.expect(active.expired.utility.anchors.length === 0 && !active.expired.effects.some(effect => effect.type === 'minecraft:invisibility'), 'Natural expiry removes anchor and owner invisibility')
            return report(['unlearned release creates no decoy', 'learned release creates anchor and visible player decoy while concealing owner', 'natural expiry cleans up the decoy and concealment'], unlearned, active)
        },
    }],
    ['stealth-decoy-swap', {
        stage: 'utility-decoy', prerequisites: [{ name: 'stealth-shadow-decoy', level: 1 }],
        prepare: async ({ actor }) => { actor.bot.clearControlStates(); await actor.bot.waitForTicks(365) },
        trigger: swap, negativeWindowTicks: 10,
        description: 'double tapping sneak exchanges the actual player and prerequisite decoy positions',
        sound: 'minecraft:entity.enderman.teleport', soundVolume: 0.6, soundPitch: 1.5,
        particle: 'reverse_portal', particleCount: 14, particleOffset: 0.35,
        async verify({ context, unlearned, active }) {
            context.expect(Math.abs(unlearned.after.location.z - unlearned.departed.location.z) < 0.6, 'Without swap, double sneak leaves the player at the walked position')
            context.expect(Math.abs(active.after.location.z - active.initial.utility.anchors[0].z) < 0.6, 'Learned swap carries player to actual old decoy position')
            context.expect(Math.abs(active.after.utility.anchors[0].z - active.departed.location.z) < 0.6, 'Learned swap moves actual decoy to the player departure position')
            return report(['prerequisite decoy alone does not teleport its owner', 'learned double-sneak exchanges both real positions'], unlearned, active)
        },
    }],
    ['stealth-enderveil', {
        stage: 'utility-veil', trigger: veil, negativeWindowTicks: 10,
        description: 'looking into a real Enderman cancels its naturally raised attack intent',
        sound: 'minecraft:entity.enderman.stare', soundVolume: 0.35, soundPitch: 1.5,
        particle: 'reverse_portal', particleCount: 3, particleOffset: 0.2,
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.after.utility.events.some(event => event.type === 'stare' && !event.cancelled)
                && unlearned.after.utility.veilSuppressions === unlearned.before.utility.veilSuppressions,
                'Ordinary direct gaze permits the native stare event without crediting a suppression')
            context.expect(active.after.utility.events.some(event => event.type === 'stare' && event.cancelled)
                && active.after.utility.veilSuppressions > active.before.utility.veilSuppressions
                && !active.after.utility.target.hasTarget,
                'Learned native stare cancellation credits a real suppression and prevents target acquisition')
            return report(['unlearned direct gaze allows Enderman attack intent', 'learned direct gaze cancels the natural attack event and target acquisition'], unlearned, active)
        },
    }],
    ['stealth-trap-sense', {
        stage: 'utility-trap', trigger: trap, negativeWindowTicks: 12,
        description: 'real player footsteps reach a sculk sensor ordinarily and are suppressed at maximum level',
        sound: 'minecraft:block.sculk_sensor.clicking_stop', soundVolume: 0.25, soundPitch: 1.4,
        async verify({ context, unlearned, active }) {
            const footsteps = state => state.after.utility.events.filter(event => event.type === 'vibration' && event.gameEvent === 'minecraft:step')
            context.expect(footsteps(unlearned).some(event => !event.cancelled), 'Ordinary natural footsteps reach the actual sculk sensor')
            context.expect(footsteps(active).length > 0 && footsteps(active).every(event => event.cancelled), 'Every observed learned footstep vibration is cancelled at the real sensor')
            return report(['unlearned footsteps reach sculk', 'learned maximum-level movement suppresses actual received step events'], unlearned, active)
        },
    }],
])
