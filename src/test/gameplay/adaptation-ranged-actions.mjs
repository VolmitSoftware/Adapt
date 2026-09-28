function itemCount(actor, name) {
    return actor.bot.inventory.slots.reduce((total, item) => total + (item?.name === name ? item.count : 0), 0)
}

async function prepareBow({ actor, equip }) {
    await equip('bow')
    await actor.bot.waitForTicks(5)
}

async function shoot(input, target, airborne = false) {
    const { context, actor, snapshot } = input
    const before = await snapshot()
    const seenArrows = new Set()
    const onSpawn = entity => { if (entity.name === 'arrow') seenArrows.add(entity.id) }
    actor.bot.on('entitySpawn', onSpawn)
    const origin = actor.bot.entity.position.clone()
    const arrowsBefore = itemCount(actor, 'arrow')
    let after
    try {
        await actor.bot.lookAt(target, true)
        actor.bot.activateItem()
        await actor.bot.waitForTicks(25)
        if (airborne) {
            actor.bot.setControlState('jump', true)
            await actor.bot.waitForTicks(4)
            context.expect(!actor.bot.entity.onGround, 'Lunge shot is released during a real jump')
        }
        actor.bot.deactivateItem()
        await context.waitUntil(async () => {
            after = await snapshot()
            return after.ranged?.lastLaunch?.sequence > (before.ranged?.lastLaunch?.sequence ?? 0)
        }, { label: 'natural player arrow launch observed', timeoutMs: 5000, intervalMs: 100 })
        await context.waitUntil(() => seenArrows.has(after.ranged.lastLaunch.entityId), {
            label: 'the recorded natural arrow spawns on the shooter client', timeoutMs: 3000,
        })
        actor.bot.setControlState('jump', false)
        await actor.bot.waitForTicks(8)
        after = await snapshot()
        context.expect(itemCount(actor, 'arrow') === arrowsBefore - 1, 'The survival shot consumes one real arrow')
        return {
            launch: after.ranged.lastLaunch,
            targets: after.ranged.targets,
            origin: { x: origin.x, y: origin.y, z: origin.z },
            location: after.location,
        }
    } finally {
        actor.bot.deactivateItem()
        actor.bot.setControlState('jump', false)
        actor.bot.off('entitySpawn', onSpawn)
    }
}

async function launchForward(input) {
    return shoot(input, input.actor.bot.entity.position.offset(0, 1.6, -15))
}

async function launchAirborne(input) {
    return shoot(input, input.actor.bot.entity.position.offset(0, 1.6, -15), true)
}

async function shootTargets(input) {
    const before = await input.snapshot()
    const shot = await shoot(input, input.actor.bot.entity.position.clone().set(8.5, 101.2, 0.5))
    return { ...shot, before: before.ranged.targets }
}

async function collectKill(input) {
    const { context, actor, snapshot } = input
    const before = await snapshot()
    const victim = before.ranged.targets.collection
    context.expect(Boolean(victim), 'Distant collection target exists')
    const shot = await shootTargets(input)
    await context.waitUntil(() => !actor.bot.entities[victim.entityId], {
        label: 'distant arrow kill removes the cow', timeoutMs: 5000,
    })
    await actor.bot.waitForTicks(8)
    context.expect(actor.bot.entity.position.distanceTo(actor.bot.entity.position.clone().set(8.5, 100, 0.5)) > 6,
        'Shooter stays outside vanilla drop pickup range')
    return { ...shot, beef: itemCount(actor, 'beef'), leather: itemCount(actor, 'leather') }
}

async function fetchItems(input) {
    const { actor } = input
    const shot = await shoot(input, actor.bot.entity.position.clone().set(8.5, 101, 0.5))
    await actor.bot.waitForTicks(12)
    return { ...shot, diamonds: itemCount(actor, 'diamond') }
}

async function prepareSword({ equip, actor }) {
    await equip('wooden_sword')
    await actor.bot.waitForTicks(25)
}

async function strikePlayer({ context, actor, opponent, snapshot }) {
    const target = actor.bot.entities[opponent.bot.entity.id]
    context.expect(Boolean(target), 'Melee target is visible')
    await actor.bot.lookAt(target.position.offset(0, 1, 0), true)
    if (actor.bot.entity.position.distanceTo(target.position) > 2.6) {
        actor.bot.setControlState('forward', true)
        try {
            await context.waitUntil(() => actor.bot.entity.position.distanceTo(target.position) < 2.4, {
                label: 'follow the knocked-back melee target', timeoutMs: 2500, intervalMs: 20,
            })
        } finally {
            actor.bot.setControlState('forward', false)
        }
        await actor.bot.waitForTicks(4)
    }
    context.expect(actor.bot.entity.onGround, 'Hunter damage comparison uses a grounded attack')
    const before = await snapshot(opponent)
    actor.bot.attack(target)
    let after
    await context.waitUntil(async () => {
        after = await snapshot(opponent)
        return after.health < before.health
    }, { label: 'natural hunter melee attack damages target', timeoutMs: 2500, intervalMs: 50 })
    context.expect(after.health > 0, 'Hunter damage test preserves the target')
    return { before: before.health, after: after.health, damage: before.health - after.health }
}

async function predatorRamp(input) {
    const hits = []
    for (let index = 0; index < 9; index++) {
        await input.actor.bot.waitForTicks(14)
        hits.push(await strikePlayer(input))
    }
    return { hits }
}

async function strikeGolem({ context, actor, snapshot }) {
    const before = (await snapshot()).ranged.targets.bigGame
    context.expect(Boolean(before), 'Big-game target exists')
    await context.waitUntil(() => Boolean(actor.bot.entities[before.entityId]), {
        label: 'golem is visible to the ordinary player', timeoutMs: 5000,
    })
    const target = actor.bot.entities[before.entityId]
    await actor.bot.lookAt(target.position.offset(0, 1.2, 0), true)
    context.expect(actor.bot.entity.position.distanceTo(target.position) < 3, 'Golem is inside melee reach')
    actor.bot.attack(target)
    let after
    await context.waitUntil(async () => {
        after = (await snapshot()).ranged.targets.bigGame
        return after && after.health < before.health
    }, { label: 'natural sword hit damages big game', timeoutMs: 3000, intervalMs: 50 })
    return { before: before.health, after: after.health, damage: before.health - after.health }
}

async function verifyDamage({ context, unlearned, active }) {
    context.expect(Math.abs(unlearned.before - active.before) < 0.01, 'Hunter damage trials begin at equal target health')
    context.expect(unlearned.damage > 0 && active.damage > unlearned.damage + 0.1, 'Learned hunter hit removes more actual target health')
    return { assertions: ['unlearned natural melee hit establishes control damage', 'learned hit removes more actual target health'], measurements: { unlearned, active } }
}

export const rangedBehaviorCases = new Map([
    ['ranged-force', {
        stage: 'ranged', negativeWindowTicks: 20,
        description: 'fully drawn bow launches a faster physical arrow',
        prepare: prepareBow, trigger: launchForward,
        sound: 'minecraft:entity.snowball.throw', soundVolume: 0.75, soundPitch: 1.2, particle: 'dust',
        async verify({ context, unlearned, active }) {
            const ratio = active.launch.speed / active.launch.initialSpeed
            context.expect(unlearned.launch.initialSpeed > 2.5 && active.launch.initialSpeed > 2.5, 'Both shots are fully drawn before adaptation effects')
            context.expect(Math.abs(unlearned.launch.speed / unlearned.launch.initialSpeed - 1) < 0.000001, 'Unlearned launch preserves its natural projectile speed')
            context.expect(Math.abs(ratio - 2.135) < 0.000001, 'Learned natural launch applies the configured 113.5% velocity bonus')
            return { assertions: ['both launches consume ammunition and spawn on the client', 'learned launch speed increases by the configured multiplier'], measurements: { unlearned, active, ratio } }
        },
    }],
    ['ranged-heavy-draw', {
        stage: 'ranged', negativeWindowTicks: 20,
        description: 'fully drawn bow launches a slower heavy arrow',
        prepare: prepareBow, trigger: launchForward,
        sound: 'minecraft:item.crossbow.shoot', soundVolume: 0.4, soundPitch: 0.6, particle: 'dust',
        async verify({ context, unlearned, active }) {
            const ratio = active.launch.speed / active.launch.initialSpeed
            context.expect(unlearned.launch.initialSpeed > 2.5 && active.launch.initialSpeed > 2.5, 'Both shots are fully drawn before adaptation effects')
            context.expect(Math.abs(unlearned.launch.speed / unlearned.launch.initialSpeed - 1) < 0.000001, 'Unlearned launch preserves its natural projectile speed')
            context.expect(Math.abs(ratio - 0.9) < 0.000001, 'Maximum-level heavy draw applies the configured 10% velocity penalty')
            return { assertions: ['both launches consume ammunition and spawn on the client', 'learned heavy arrow has the configured reduced launch velocity'], measurements: { unlearned, active, ratio } }
        },
    }],
    ['ranged-piercing', {
        stage: 'ranged-dual-target', negativeWindowTicks: 20,
        description: 'one bow arrow passes through the first cow and damages the second',
        prepare: prepareBow, trigger: shootTargets,
        sound: 'minecraft:entity.arrow.hit', soundVolume: 0.8, soundPitch: 1.24,
        particle: 'dust', particleColor: 16777215,
        async verify({ context, unlearned, active }) {
            context.expect((unlearned.targets.first?.health ?? 0) < unlearned.before.first.health, 'Control arrow hits the first cow')
            context.expect(unlearned.targets.second.health === unlearned.before.second.health, 'Control arrow does not damage the second cow')
            context.expect(active.launch.pierceLevel > 0, 'Learned natural arrow carries piercing')
            context.expect((active.targets.first?.health ?? 0) < active.before.first.health, 'Learned arrow hits the first cow')
            context.expect((active.targets.second?.health ?? 0) < active.before.second.health, 'The same learned arrow also damages the second cow')
            return { assertions: ['unlearned arrow hits only the front target', 'one learned arrow damages both aligned targets'], measurements: { unlearned, active } }
        },
    }],
    ['ranged-lunge-shot', {
        stage: 'ranged', negativeWindowTicks: 20,
        description: 'firing a bow during a real jump pushes the shooter backward',
        prepare: prepareBow, trigger: launchAirborne,
        sound: 'minecraft:item.armor.equip_turtle', soundVolume: 1, soundPitch: 0.75, particle: 'cloud',
        async verify({ context, unlearned, active }) {
            const control = unlearned.location.z - unlearned.origin.z
            const recoil = active.location.z - active.origin.z
            context.expect(Math.abs(control) < 0.2, 'Unlearned stationary jump shot has no horizontal recoil')
            context.expect(recoil > control + 0.5, 'Learned airborne shot moves the shooter away from the aim')
            return { assertions: ['both shots occur during real jumps', 'learned shot produces backward player displacement'], measurements: { unlearned, active, control, recoil } }
        },
    }],
    ['ranged-fetch-shot', {
        stage: 'ranged-fetch', negativeWindowTicks: 20,
        description: 'an arrow impact delivers distant dropped diamonds to its shooter',
        prepare: prepareBow, trigger: fetchItems,
        sound: 'minecraft:entity.item.pickup', soundVolume: 0.6, soundPitch: 1.5, particle: 'enchant',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.diamonds === 0, 'Control shot cannot collect distant drops')
            context.expect(active.diamonds === 3, 'Learned impact transfers all three actual diamonds')
            context.expect(Math.abs(active.location.x - active.origin.x) < 0.2, 'Shooter did not walk into item pickup range')
            return { assertions: ['unlearned distant impact leaves diamonds out of inventory', 'learned impact transfers three diamonds without approaching them'], measurements: { unlearned, active } }
        },
    }],
    ['hunter-drop-to-inventory', {
        stage: 'ranged-collection', negativeWindowTicks: 20,
        description: 'a distant natural bow kill delivers beef directly to its shooter',
        prepare: prepareBow, trigger: collectKill,
        sound: 'minecraft:block.amethyst_block.chime', soundVolume: 0.35, soundPitch: 1.8, particle: 'wax_on',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.beef === 0 && unlearned.leather === 0, 'Control distant kill leaves drops outside inventory')
            context.expect(active.beef >= 1 && active.beef <= 3, 'Learned distant kill transfers the cow beef drop')
            return { assertions: ['both targets die from real player arrows beyond pickup range', 'learned kill places actual beef directly in inventory'], measurements: { unlearned, active } }
        },
    }],
    ['hunter-big-game', {
        stage: 'hunter-big-game', negativeWindowTicks: 20,
        description: 'a grounded wooden-sword hit deals bonus damage to an iron golem',
        prepare: prepareSword, trigger: strikeGolem, verify: verifyDamage,
        sound: 'minecraft:entity.player.attack.strong', soundVolume: 0.5, soundPitch: 0.7,
        particle: 'crit', particleCount: 4, particleOffset: 0.25,
    }],
    ['hunter-adrenaline', {
        stage: 'hunter-low-health', negativeWindowTicks: 20,
        description: 'a grounded sword hit gains damage while the attacker has six health',
        prepare: prepareSword, trigger: strikePlayer, verify: verifyDamage,
        sound: 'minecraft:entity.player.attack.crit', soundVolume: 0.6, soundPitch: 0.7, particle: 'dust',
    }],
    ['hunter-predator-focus', {
        stage: 'hunter-predator', negativeWindowTicks: 20,
        description: 'nine real punches ramp damage against one target',
        trigger: predatorRamp,
        sound: 'minecraft:entity.player.attack.crit', soundVolume: 0.6, soundPitch: 1.5, particle: 'dust',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.hits.length === 9 && active.hits.length === 9, 'Both trials land nine actual punches')
            const baseline = unlearned.hits[0].damage
            context.expect(unlearned.hits.every(hit => Math.abs(hit.damage - baseline) < 0.02), 'Unlearned punches retain constant damage')
            for (let index = 0; index < active.hits.length; index++) {
                context.expect(Math.abs(active.hits[index].damage - baseline * (1 + index * 0.07)) < 0.03,
                    `Learned punch ${index + 1} applies its seven-percent ramp stack`)
            }
            return { assertions: ['unlearned repeated punches retain control damage', 'learned strikes gain seven percent per stack through the nine-stack cap'], measurements: { unlearned, active } }
        },
    }],
])
