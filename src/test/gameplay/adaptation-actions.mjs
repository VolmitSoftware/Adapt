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
    ['agility-super-jump', {
        stage: 'agility',
        attribute: 'minecraft:jump_strength',
        sound: 'minecraft:item.armor.equip_leather', particle: 'cloud',
        soundVolume: 0.3, soundPitch: 0.35,
        trigger: sneak,
    }],
    ['stealth-speed', {
        stage: 'agility',
        attribute: 'minecraft:sneaking_speed',
        sound: 'minecraft:particle.soul_escape', particle: 'soul',
        soundVolume: 1.6, soundPitch: 0.9,
        trigger: sneak,
    }],
    ['stealth-vision', {
        stage: 'agility',
        effect: 'minecraft:night_vision',
        sound: 'minecraft:block.sculk_sensor.clicking', particle: 'end_rod',
        soundVolume: 0.4, soundPitch: 1.3,
        trigger: sneak,
    }],
    ['seaborne-oxygen', {
        stage: 'seaborne',
        attribute: 'minecraft:oxygen_bonus',
        sound: 'minecraft:block.conduit.activate', particle: 'dust',
        soundVolume: 0.35, soundPitch: 1.15,
        trigger: underwater,
    }],
    ['seaborne-turtles-mining-speed', {
        stage: 'seaborne',
        attribute: 'minecraft:submerged_mining_speed',
        sound: 'minecraft:block.amethyst_block.hit', particle: 'crit',
        soundVolume: 0.4, soundPitch: 1.5,
        trigger: underwater,
    }],
    ['seaborne-turtles-vision', {
        stage: 'seaborne',
        effect: 'minecraft:night_vision',
        sound: 'minecraft:block.conduit.activate', particle: 'glow',
        soundVolume: 0.35, soundPitch: 1.2,
        trigger: underwater,
    }],
    ['kinetics-moon-jump', {
        stage: 'agility',
        attribute: 'minecraft:jump_strength',
        sound: 'minecraft:entity.rabbit.jump', particle: 'cloud',
        soundVolume: 0.6, soundPitch: 0.7,
        trigger: sneakJump,
    }],
    ['hunter-strength', {
        stage: 'agility',
        attribute: 'minecraft:attack_damage',
        sound: 'minecraft:entity.zoglin.attack', particle: 'flame',
        soundVolume: 0.4, soundPitch: 1.4,
        trigger: receivePunch,
    }],
    ['hunter-jumpboost', {
        stage: 'agility',
        attribute: 'minecraft:jump_strength',
        sound: 'minecraft:block.note_block.pling', particle: 'cloud',
        soundVolume: 0.7, soundPitch: 1.2,
        trigger: receivePunch,
    }],
    ['hunter-luck', {
        stage: 'agility',
        attribute: 'minecraft:luck',
        sound: 'minecraft:block.amethyst_block.chime', particle: 'happy_villager',
        soundVolume: 0.4, soundPitch: 1.9,
        trigger: receivePunch,
    }],
    ['hunter-regen', {
        stage: 'agility',
        effect: 'minecraft:regeneration',
        sound: 'minecraft:block.amethyst_block.chime', particle: 'heart',
        soundVolume: 0.5, soundPitch: 1.6,
        trigger: receivePunch,
    }],
    ['hunter-invis', {
        stage: 'agility',
        effect: 'minecraft:invisibility',
        sound: 'minecraft:entity.illusioner.mirror_move', particle: 'portal',
        soundVolume: 0.7, soundPitch: 1.0,
        trigger: receivePunch,
    }],
    ['hunter-resistance', {
        stage: 'agility',
        effect: 'minecraft:resistance',
        sound: 'minecraft:item.shield.block', particle: 'electric_spark',
        soundVolume: 0.6, soundPitch: 0.9,
        trigger: receivePunch,
    }],
])
