import { versionEntry } from './version-catalog.mjs'

async function sneak({ actor }) {
    actor.bot.setControlState('sneak', false)
    await actor.bot.waitForTicks(3)
    actor.bot.setControlState('sneak', true)
    await actor.bot.waitForTicks(5)
}

async function sneakJump(input) {
    await sneak(input)
    input.actor.bot.setControlState('jump', true)
    await input.actor.bot.waitForTicks(3)
    input.actor.bot.setControlState('jump', false)
}

async function underwater({ context, actor }) {
    await context.waitUntil(() => actor.bot.entity.position.y < 100.1, {
        label: 'actor entered the underwater fixture', timeoutMs: 5000,
    })
    await actor.bot.waitForTicks(5)
}

const AIR_WINDOW_TICKS = 100

async function airLoss(input) {
    await underwater(input)
    const before = await input.snapshot()
    await input.actor.bot.waitForTicks(AIR_WINDOW_TICKS)
    const after = await input.snapshot()
    return { before: before.remainingAir, after: after.remainingAir, lost: before.remainingAir - after.remainingAir }
}

async function receivePunch({ context, actor, opponent }) {
    await context.waitUntil(() => Boolean(opponent.bot.entities[actor.bot.entity.id]), {
        label: 'adaptation actor visible to opponent', timeoutMs: 5000,
    })
    const target = opponent.bot.entities[actor.bot.entity.id]
    context.expect(opponent.bot.entity.position.distanceTo(target.position) < 3,
        'Opponent is within melee reach')
    await opponent.bot.lookAt(target.position.offset(0, 1, 0), true)
    opponent.bot.attack(target)
    await actor.bot.waitForTicks(4)
}

export const behaviorCases = new Map([
    ['adapt:agility/super_jump', {
        stage: 'agility',
        attribute: 'minecraft:jump_strength',
        sound: 'minecraft:item.armor.equip_leather', particle: 'cloud',
        soundVolume: 0.3, soundPitch: 0.35,
        trigger: sneak,
    }],
    ['adapt:stealth/speed', {
        stage: 'agility',
        attribute: 'minecraft:sneaking_speed',
        sound: 'minecraft:particle.soul_escape', particle: 'soul',
        soundVolume: 1.6, soundPitch: 0.9,
        trigger: sneak,
    }],
    ['adapt:stealth/vision', {
        stage: 'agility',
        effect: 'minecraft:night_vision',
        sound: 'minecraft:block.sculk_sensor.clicking', particle: 'end_rod',
        soundVolume: 0.4, soundPitch: 1.3,
        trigger: sneak,
    }],
    ['adapt:seaborne/oxygen', versionEntry('adapt:seaborne/oxygen')?.status === 'emulated' ? {
        stage: 'seaborne',
        description: 'holding breath underwater loses less air (oxygen bonus emulated: air loss undone per tick)',
        sound: 'minecraft:block.conduit.activate', particle: 'dust',
        soundVolume: 0.35, soundPitch: 1.15,
        trigger: airLoss,
        verify({ context, unlearned, active }) {
            context.expect(unlearned.lost >= AIR_WINDOW_TICKS * 0.8, 'Unlearned breath loses about one air per underwater tick')
            context.expect(active.lost < unlearned.lost * 0.75, 'Learned breath loses clearly less air over the same underwater time')
            return { assertions: ['unlearned underwater air loss is ordinary', 'learned underwater air loss is reduced'], measurements: { unlearned, active } }
        },
    } : {
        stage: 'seaborne',
        attribute: 'minecraft:oxygen_bonus',
        sound: 'minecraft:block.conduit.activate', particle: 'dust',
        soundVolume: 0.35, soundPitch: 1.15,
        trigger: underwater,
    }],
    ['adapt:seaborne/turtles_mining_speed', {
        stage: 'seaborne',
        attribute: 'minecraft:submerged_mining_speed',
        sound: 'minecraft:block.amethyst_block.hit', particle: 'crit',
        soundVolume: 0.4, soundPitch: 1.5,
        trigger: underwater,
    }],
    ['adapt:kinetics/moon_jump', {
        stage: 'agility',
        attribute: 'minecraft:jump_strength',
        sound: 'minecraft:entity.rabbit.jump', particle: 'cloud',
        soundVolume: 0.6, soundPitch: 0.7,
        trigger: sneakJump,
    }],
    ['adapt:hunter/strength', {
        stage: 'agility',
        attribute: 'minecraft:attack_damage',
        sound: 'minecraft:entity.zoglin.attack', particle: 'flame',
        soundVolume: 0.4, soundPitch: 1.4,
        trigger: receivePunch,
    }],
    ['adapt:hunter/jumpboost', {
        stage: 'agility',
        attribute: 'minecraft:jump_strength',
        sound: 'minecraft:block.note_block.pling', particle: 'cloud',
        soundVolume: 0.7, soundPitch: 1.2,
        trigger: receivePunch,
    }],
    ['adapt:hunter/luck', {
        stage: 'agility',
        attribute: 'minecraft:luck',
        sound: 'minecraft:block.amethyst_block.chime', particle: 'happy_villager',
        soundVolume: 0.4, soundPitch: 1.9,
        trigger: receivePunch,
    }],
    ['adapt:hunter/regen', {
        stage: 'agility',
        effect: 'minecraft:regeneration',
        sound: 'minecraft:block.amethyst_block.chime', particle: 'heart',
        soundVolume: 0.5, soundPitch: 1.6,
        trigger: receivePunch,
    }],
    ['adapt:hunter/invis', {
        stage: 'agility',
        effect: 'minecraft:invisibility',
        sound: 'minecraft:entity.illusioner.mirror_move', particle: 'portal',
        soundVolume: 0.7, soundPitch: 1.0,
        trigger: receivePunch,
    }],
    ['adapt:hunter/resistance', {
        stage: 'agility',
        effect: 'minecraft:resistance',
        sound: 'minecraft:item.shield.block', particle: 'electric_spark',
        soundVolume: 0.6, soundPitch: 0.9,
        trigger: receivePunch,
    }],
])
