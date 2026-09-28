import { setTimeout as sleep } from 'node:timers/promises'

function itemCount(actor, name) {
    return actor.bot.inventory.slots.reduce((sum, item) => sum + (item?.name === name ? item.count : 0), 0)
}

async function visible(input, key = 'pet') {
    const state = (await input.snapshot()).taming[key]
    input.context.expect(Boolean(state), `Taming ${key} exists`)
    await input.context.waitUntil(() => Boolean(input.actor.bot.entities[state.entityId]), {
        label: `taming ${key} visible to player`, timeoutMs: 5000,
    })
    return input.actor.bot.entities[state.entityId]
}

async function passive({ actor, snapshot }) {
    await actor.bot.waitForTicks(250)
    return (await snapshot()).taming.pet
}

async function hitPet(input) {
    const pet = await visible(input)
    await input.actor.bot.waitForTicks(30)
    const before = (await input.snapshot()).taming.pet
    await input.actor.bot.lookAt(pet.position.offset(0, 0.6, 0), true)
    input.actor.bot.attack(pet)
    await input.actor.bot.waitForTicks(25)
    return { before, after: (await input.snapshot()).taming.pet }
}

async function recall(input) {
    const { actor, snapshot } = input
    const before = await snapshot()
    actor.bot.setControlState('sneak', true)
    try {
        await actor.bot.waitForTicks(3)
        await actor.bot.lookAt(actor.bot.entity.position.offset(0, 3, -10), true)
        actor.bot.activateItem()
        actor.bot.deactivateItem()
        await actor.bot.waitForTicks(25)
    } finally {
        actor.bot.setControlState('sneak', false)
    }
    return { before, after: await snapshot() }
}

async function fetch(input) {
    const { actor, snapshot } = input
    const origin = actor.bot.entity.position.clone()
    const pet = (await snapshot()).taming.pet
    let furthestPetX = pet.x
    const track = entity => {
        if (entity.id === pet.entityId) furthestPetX = Math.max(furthestPetX, entity.position.x)
    }
    actor.bot.on('entityMoved', track)
    try {
        for (let index = 0; index < 40; index++) {
            await actor.bot.waitForTicks(10)
            furthestPetX = Math.max(furthestPetX, (await snapshot()).taming.pet.x)
        }
    } finally {
        actor.bot.off('entityMoved', track)
    }
    return { diamonds: itemCount(actor, 'diamond'), furthestPetX, displacement: actor.bot.entity.position.distanceTo(origin) }
}

async function sharedPain({ context, actor, opponent, snapshot }) {
    const sword = opponent.bot.inventory.items().find(item => item.name === 'wooden_sword')
    context.expect(Boolean(sword), 'Opponent has a control sword')
    await opponent.bot.equip(sword, 'hand')
    await opponent.bot.waitForTicks(25)
    const victim = opponent.bot.entities[actor.bot.entity.id]
    context.expect(Boolean(victim), 'Owner is visible to attacking opponent')
    const before = await snapshot()
    await opponent.bot.lookAt(victim.position.offset(0, 1, 0), true)
    opponent.bot.attack(victim)
    await actor.bot.waitForTicks(10)
    const after = await snapshot()
    return { ownerDamage: before.health - after.health, petDamage: before.taming.pet.health - after.taming.pet.health }
}

async function alpha(input) {
    const { actor, snapshot } = input
    const target = await visible(input, 'target')
    const before = await snapshot()
    const bones = itemCount(actor, 'bone')
    actor.bot.setControlState('sneak', true)
    try {
        await actor.bot.waitForTicks(4)
        await actor.bot.lookAt(target.position.offset(0, 0.7, 0), true)
        actor.bot.attack(target)
        await actor.bot.waitForTicks(10)
    } finally {
        actor.bot.setControlState('sneak', false)
    }
    return { before, after: await snapshot(), bonesUsed: bones - itemCount(actor, 'bone') }
}

async function mount(input) {
    const horse = await visible(input)
    const before = (await input.snapshot()).taming.pet
    await input.actor.bot.lookAt(horse.position.offset(0, 1, 0), true)
    await input.actor.bot.waitForTicks(2)
    input.actor.bot.activateEntity(horse)
    await input.context.waitUntil(() => Boolean(input.actor.bot.vehicle), { label: 'natural horse mount', timeoutMs: 5000 })
    try {
        await sleep(1300)
        const result = (await input.snapshot()).taming.pet
        const target = input.actor.bot.entities[input.opponent.bot.entity.id]
        input.context.expect(Boolean(target), 'Mounted melee opponent is visible')
        const targetBefore = await input.snapshot(input.opponent)
        await input.actor.bot.lookAt(target.position.offset(0, 1, 0), true)
        input.actor.bot.attack(target)
        await sleep(400)
        const targetAfter = await input.snapshot(input.opponent)
        return { before, after: result, damage: targetBefore.health - targetAfter.health }
    } finally {
        if (input.actor.bot.vehicle) {
            input.actor.bot.setControlState('sneak', true)
            try {
                await input.context.waitUntil(async () => !(await input.snapshot()).taming.mounted, { label: 'natural sneak dismount', timeoutMs: 3000 })
            } finally {
                input.actor.bot.setControlState('sneak', false)
            }
        }
    }
}

async function tame(input) {
    const { actor, context, snapshot } = input
    const wolf = await visible(input)
    const before = (await snapshot()).taming.pet
    let after = before
    let attempts = 0
    while (!after.tamed && attempts < 32) {
        await actor.bot.lookAt(wolf.position.offset(0, 0.6, 0), true)
        actor.bot.activateEntity(wolf)
        await actor.bot.waitForTicks(5)
        after = (await snapshot()).taming.pet
        attempts++
    }
    context.expect(after.tamed, 'Natural bone feeding tames the wolf within 32 attempts')
    await actor.bot.waitForTicks(5)
    return { before, after: (await snapshot()).taming.pet, attempts }
}

async function petKill(input) {
    const { actor, context, snapshot } = input
    const victim = await visible(input, 'target')
    await actor.bot.lookAt(victim.position.offset(0, 0.7, 0), true)
    actor.bot.attack(victim)
    let after
    await context.waitUntil(async () => {
        after = await snapshot()
        return after.taming.petHit?.targetId === victim.id && (!after.taming.target || !after.taming.target.alive)
    }, { label: 'owned wolf AI naturally kills the punched cow', timeoutMs: 10000, intervalMs: 100 })
    await actor.bot.waitForTicks(6)
    return await snapshot()
}

function assertionResult(assertions, unlearned, active) {
    return { assertions, measurements: { unlearned, active } }
}

export const tamingBehaviorCases = new Map([
    ['tame-health', {
        stage: 'taming-passive', negativeWindowTicks: 20, trigger: passive,
        description: 'owned wolf receives its maximum-health multiplier during normal scheduler passes',
        sound: 'minecraft:block.note_block.chime', soundVolume: 0.4, soundPitch: 1.5, particle: 'happy_villager',
        async verify({ context, unlearned, active }) {
            const ratio = active.attributes['minecraft:max_health'] / unlearned.attributes['minecraft:max_health']
            context.expect(Math.abs(ratio - 4.07) < 0.01, 'Owned pet maximum health gains the configured 307% bonus')
            return assertionResult(['unlearned pet provides vanilla maximum-health control', 'learned scheduled bonus applies the configured maximum-health multiplier'], unlearned, active)
        },
    }],
    ['tame-damage', {
        stage: 'taming-passive', negativeWindowTicks: 20, trigger: passive,
        description: 'owned wolf receives its attack-damage multiplier during normal scheduler passes',
        sound: 'minecraft:entity.wolf.growl', soundVolume: 0.5, soundPitch: 0.8, particle: 'dust', particleColor: 0xB0202A,
        async verify({ context, unlearned, active }) {
            const ratio = active.attributes['minecraft:attack_damage'] / unlearned.attributes['minecraft:attack_damage']
            context.expect(Math.abs(ratio - 1.73) < 0.01, 'Owned pet attack damage gains the configured 73% bonus')
            return assertionResult(['unlearned pet provides vanilla attack-attribute control', 'learned scheduled bonus applies the configured damage multiplier'], unlearned, active)
        },
    }],
    ['tame-pack-leader-aura', {
        stage: 'taming-passive', negativeWindowTicks: 20, trigger: passive,
        description: 'nearby owned wolf gains movement speed and regeneration',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.effects['minecraft:regeneration'] === undefined, 'Unlearned wolf has no regeneration')
            context.expect(active.effects['minecraft:regeneration'] >= 0, 'Learned aura gives the actual wolf regeneration')
            context.expect(active.attributes['minecraft:movement_speed'] > unlearned.attributes['minecraft:movement_speed'], 'Learned aura increases pet movement attribute')
            return assertionResult(['unlearned pet has no regeneration', 'learned pet receives regeneration and increased movement speed'], unlearned, active)
        },
    }],
    ['tame-health-regeneration', {
        stage: 'taming-regeneration', negativeWindowTicks: 20, trigger: hitPet,
        description: 'a real hit to an injured owned wolf triggers healing',
        sound: 'minecraft:entity.wolf.pant', soundVolume: 0.5, soundPitch: 1.3, particle: 'heart',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.after.health < unlearned.before.health, 'Control punch hurts the injured wolf')
            context.expect(active.after.health > active.before.health, 'Learned regeneration heals more health than the punch removes')
            return assertionResult(['ordinary punch reduces unlearned pet health', 'the same hit triggers actual learned pet healing'], unlearned, active)
        },
    }],
    ['tame-beast-recall', {
        stage: 'taming-recall', negativeWindowTicks: 20, prepare: async ({ equip }) => equip('lead'), trigger: recall,
        description: 'sneak right-click with a lead recalls a distant owned wolf',
        sound: 'minecraft:item.lead.break', soundVolume: 0.6, soundPitch: 1.2, particle: 'reverse_portal',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.after.taming.pet.x > 8, 'Unlearned lead use leaves distant pet in place')
            context.expect(Math.hypot(active.after.taming.pet.x - active.after.location.x, active.after.taming.pet.z - active.after.location.z) < 3, 'Learned lead use moves actual pet beside owner')
            context.expect(active.after.food < active.before.food, 'Successful recall charges its hunger cost')
            return assertionResult(['unlearned lead use does not recall pet', 'learned pet teleports beside owner and charges hunger'], unlearned, active)
        },
    }],
    ['tame-fetch', {
        stage: 'taming-fetch', negativeWindowTicks: 20, trigger: fetch,
        description: 'idle wolf walks to distant diamonds and delivers them to the owner',
        sound: 'minecraft:entity.item.pickup', soundVolume: 0.5, soundPitch: 1.4, particle: 'happy_villager',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.diamonds === 0, 'Unlearned stationary owner cannot collect distant diamonds')
            context.expect(active.diamonds === 3, 'Learned wolf delivers all three real diamonds')
            context.expect(active.furthestPetX > 5 && active.displacement < 0.2, 'Wolf travels toward the item while its owner stays stationary')
            return assertionResult(['unlearned owner receives no remote items', 'learned wolf physically approaches the drop and returns three diamonds'], unlearned, active)
        },
    }],
    ['tame-shared-pain', {
        stage: 'taming-shared', negativeWindowTicks: 20, trigger: sharedPain,
        description: 'an opponent sword hit splits damage between owner and nearby pet',
        sound: 'minecraft:block.amethyst_cluster.hit', soundVolume: 0.55, soundPitch: 0.8, particle: 'dust', particleColor: 0xF2C14E,
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.ownerDamage > 0 && unlearned.petDamage === 0, 'Unlearned sword hit damages only owner')
            context.expect(active.ownerDamage > 0 && active.ownerDamage < unlearned.ownerDamage && active.petDamage > 0, 'Learned sword hit redirects actual damage into pet health')
            context.expect(Math.abs(active.ownerDamage + active.petDamage - unlearned.ownerDamage) < 0.05, 'Shared damage conserves incoming unarmored damage')
            return assertionResult(['unlearned hit damages only owner', 'learned hit splits conserved actual health loss between owner and pet'], unlearned, active)
        },
    }],
    ['tame-last-breath', {
        stage: 'taming-last-breath', negativeWindowTicks: 20, prepare: async ({ equip }) => equip('wooden_sword'), trigger: hitPet,
        description: 'a lethal real sword hit saves the owned wolf at one health',
        sound: 'minecraft:item.totem.use', soundVolume: 0.7, soundPitch: 1.2, particle: 'totem_of_undying',
        async verify({ context, unlearned, active }) {
            context.expect(!unlearned.after || !unlearned.after.alive, 'Control lethal sword hit kills the wolf')
            context.expect(active.after?.alive && active.after.health === 1, 'Learned lethal hit leaves the actual wolf alive at one health')
            return assertionResult(['unlearned lethal hit kills pet', 'learned lethal hit preserves the pet at exactly one health'], unlearned, active)
        },
    }],
    ['tame-alphas-command', {
        stage: 'taming-alpha', negativeWindowTicks: 20, prepare: async ({ equip }) => equip('bone'), trigger: alpha,
        description: 'sneak bone strike commands the sitting wolf to focus the target',
        sound: 'minecraft:entity.wolf.growl', soundVolume: 0.8, soundPitch: 0.9, particle: 'happy_villager',
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.bonesUsed === 0 && unlearned.after.taming.pet.sitting, 'Unlearned strike neither spends bone nor unsits wolf')
            context.expect(active.bonesUsed === 1 && !active.after.taming.pet.sitting, 'Learned command consumes one bone and unsits wolf')
            context.expect(active.after.taming.pet.targetId === active.after.taming.target.entityId, 'Commanded pet focuses the naturally struck target')
            return assertionResult(['unlearned bone strike leaves pet sitting and bone intact', 'learned strike consumes one bone and focuses the unsitting pet'], unlearned, active)
        },
    }],
    ['tame-mounted-tactics', {
        stage: 'taming-mounted', negativeWindowTicks: 20, trigger: mount,
        description: 'naturally mounting a saddled horse grants speed and jump bonuses',
        sound: 'minecraft:entity.horse.breathe', soundVolume: 0.4, soundPitch: 0.9,
        particle: 'crit', particleCount: 9, particleOffset: 0.35,
        async verify({ context, unlearned, active }) {
            for (const attribute of ['minecraft:movement_speed', 'minecraft:jump_strength']) {
                context.expect(Math.abs(unlearned.after.attributes[attribute] - unlearned.before.attributes[attribute]) < 0.00001,
                    `Control mount retains its original ${attribute}`)
                context.expect(active.after.attributes[attribute] > active.before.attributes[attribute],
                    `Learned rider boosts its actual mount ${attribute}`)
            }
            context.expect(unlearned.damage > 0 && active.damage > unlearned.damage, 'Learned mounted fist strike deals more actual damage to the opponent')
            return assertionResult(['both trials mount the actual saddled horse through player interaction', 'learned mount gains speed and jump attributes', 'learned mounted melee increases actual damage and emits its authored feedback'], unlearned, active)
        },
    }],
    ['tame-stable-hand', {
        stage: 'taming-stable', negativeWindowTicks: 20, prepare: async ({ equip }) => equip('bone'), trigger: tame,
        description: 'natural bone taming applies inherited animal attribute bonuses',
        sound: 'minecraft:entity.horse.breathe', soundVolume: 0.4, soundPitch: 1.2, particle: 'heart', particleCount: 1, particleOffset: 0,
        async verify({ context, unlearned, active }) {
            context.expect(active.after.attributes['minecraft:max_health'] > unlearned.after.attributes['minecraft:max_health'], 'Learned natural tame produces greater maximum health')
            context.expect(active.after.attributes['minecraft:movement_speed'] > unlearned.after.attributes['minecraft:movement_speed'], 'Learned natural tame produces greater movement speed')
            return assertionResult(['both wolves are tamed through bounded real bone feeding', 'learned tame applies greater health and speed attributes'], unlearned, active)
        },
    }],
    ['tame-battle-bond', {
        stage: 'taming-battle', negativeWindowTicks: 20, trigger: petKill,
        description: 'a natural owned-wolf kill buffs both the owner and its pet',
        sound: 'minecraft:block.beacon.power_select', soundVolume: 0.8, soundPitch: 1.25, particle: 'dust',
        async verify({ context, unlearned, active }) {
            const effect = (state, key) => state.effects.some(entry => entry.type === key)
            context.expect(!effect(unlearned, 'minecraft:strength'), 'Unlearned wolf kill gives owner no strength')
            context.expect(effect(active, 'minecraft:strength') && effect(active, 'minecraft:speed') && effect(active, 'minecraft:regeneration'), 'Learned wolf kill buffs actual owner with strength, speed and regeneration')
            context.expect(active.taming.pet.effects['minecraft:strength'] >= 0, 'The actual killing pet also receives strength')
            return assertionResult(['natural pet damage records identify both wolf kills', 'learned pet kill buffs owner and pet'], unlearned, active)
        },
    }],
])
