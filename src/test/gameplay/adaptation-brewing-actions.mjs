function potionContents(item, registry) {
    const data = item?.componentMap?.get('potion_contents')?.data
    return {
        name: item?.name,
        count: item?.count,
        potionId: data?.potionId,
        effects: (data?.customEffects ?? []).map(effect => ({
            name: registry.effects[effect.id]?.name,
            duration: effect.details.duration,
            amplifier: effect.details.amplifier,
        })),
    }
}

async function putInput(input, window, name, destination) {
    const source = window.slots.findIndex((item, index) => index >= window.inventoryStart && item?.name === name)
    input.context.expect(source >= window.inventoryStart, `${name} exists in player inventory`)
    await input.actor.bot.clickWindow(source, 0, 0)
    await input.actor.bot.clickWindow(destination, 0, 0)
    await input.context.waitUntil(() => window.slots[destination]?.name === name && !window.selectedItem, {
        label: `${name} placed into brewing slot ${destination}`, timeoutMs: 5000,
    })
}

async function brew(input, recipe) {
    const { context, actor, snapshot } = input
    const learned = (await snapshot()).learned[recipe.name] > 0
    const position = actor.bot.entity.position.clone().set(2, 100, 2)
    await context.waitUntil(() => actor.bot.blockAt(position)?.name === 'brewing_stand', {
        label: 'brewing stand available', timeoutMs: 5000,
    })
    await actor.bot.activateBlock(actor.bot.blockAt(position))
    await context.waitUntil(() => actor.bot.currentWindow?.type.includes('brewing'), {
        label: 'player opened brewing stand', timeoutMs: 5000,
    })
    const window = actor.bot.currentWindow
    try {
        await putInput(input, window, 'potion', 0)
        await putInput(input, window, 'blaze_powder', 4)
        const before = potionContents(window.slots[0], actor.bot.registry)
        context.expect(before.count === 1 && Number.isInteger(before.potionId) && before.effects.length === 0,
            'Brewing starts with one ordinary base potion', before)
        const started = Date.now()
        await putInput(input, window, recipe.ingredient, 3)
        if (recipe.custom && !learned) {
            await actor.bot.waitForTicks(360)
        } else {
            await context.waitUntil(() => {
                const output = potionContents(window.slots[0], actor.bot.registry)
                return recipe.custom ? output.effects.some(effect => effect.name === recipe.effect)
                    : output.potionId !== undefined && output.potionId !== before.potionId
            }, { label: `${recipe.name} produces an actual potion`, timeoutMs: 30000, intervalMs: 100 })
        }
        const elapsedMillis = Date.now() - started
        const output = potionContents(window.slots[0], actor.bot.registry)
        const ingredientRemaining = window.slots[3]?.count ?? 0
        await actor.bot.putAway(0)
        await context.waitUntil(() => window.slots[0] === null, { label: 'brewed potion withdrawn', timeoutMs: 5000 })
        actor.bot.closeWindow(window)
        await context.waitUntil(() => actor.bot.inventory.items().some(item => item.name === 'potion'), {
            label: 'potion returned to player inventory', timeoutMs: 5000,
        })
        const received = potionContents(actor.bot.inventory.items().find(item => item.name === 'potion'), actor.bot.registry)
        context.expect(JSON.stringify(received) === JSON.stringify(output), 'Withdrawn potion retains its brewed effects', { output, received })
        return { before, output, received, ingredientRemaining, elapsedMillis }
    } finally {
        if (actor.bot.currentWindow === window) actor.bot.closeWindow(window)
    }
}

function verifyCustom(recipe, { context, unlearned, active }) {
    context.expect(JSON.stringify(unlearned.before) === JSON.stringify(active.before),
        'Both custom brewing trials begin with the same base potion')
    context.expect(JSON.stringify(unlearned.before) === JSON.stringify(unlearned.output),
        'Unlearned recipe preserves the original potion throughout the complete brew window')
    context.expect(unlearned.ingredientRemaining === 1, 'Unlearned custom recipe does not consume its ingredient')
    context.expect(active.output.count === 1 && active.ingredientRemaining === 0,
        'Learned custom recipe creates one potion and consumes one ingredient')
    const effect = active.received.effects.find(candidate => candidate.name === recipe.effect)
    context.expect(active.received.effects.length === 1 && effect?.duration === recipe.duration && effect?.amplifier === recipe.amplifier,
        'Withdrawn custom potion has the declared effect, duration, and amplifier', { expected: recipe, actual: effect })
    return {
        assertions: ['unlearned brewing leaves both inputs intact after a complete brew window', 'natural learned brewing consumes its ingredient', 'withdrawn potion contains the exact recipe effect, duration, and amplifier'],
        measurements: { unlearned, active },
    }
}

function verifyVanilla({ context, unlearned, active }) {
    context.expect(unlearned.output.potionId !== unlearned.before.potionId && active.output.potionId !== active.before.potionId,
        'Both trials complete a real vanilla potion recipe')
    context.expect(unlearned.output.potionId === active.output.potionId, 'Both trials produce the same vanilla potion type')
    context.expect(unlearned.ingredientRemaining === 0 && active.ingredientRemaining === 0,
        'Both vanilla brewing trials consume sugar')
}

const customRecipes = [
    { name: 'brewing-absorption', ingredient: 'quartz', effect: 'Absorption', duration: 1200, amplifier: 0,
        sound: 'block.beacon.power_select', soundVolume: 0.4, soundPitch: 1.6, particle: 'end_rod' },
    { name: 'brewing-blindness', ingredient: 'ink_sac', effect: 'Blindness', duration: 600, amplifier: 0,
        sound: 'entity.elder_guardian.curse', soundVolume: 0.25, soundPitch: 1.8, particle: 'soul' },
    { name: 'brewing-darkness', ingredient: 'black_concrete', effect: 'Darkness', duration: 600, amplifier: 0,
        sound: 'block.sculk_catalyst.break', soundVolume: 0.5, soundPitch: 1, particle: 'sculk_soul' },
    { name: 'brewing-decay', ingredient: 'poisonous_potato', effect: 'Wither', duration: 320, amplifier: 0,
        sound: 'entity.wither.shoot', soundVolume: 0.3, soundPitch: 1.4, particle: 'ash' },
    { name: 'brewing-fatigue', ingredient: 'slime_ball', effect: 'MiningFatigue', duration: 1200, amplifier: 0,
        sound: 'block.note_block.didgeridoo', soundVolume: 0.4, soundPitch: 0.5, particle: 'smoke' },
    { name: 'brewing-haste', ingredient: 'amethyst_shard', effect: 'Haste', duration: 1200, amplifier: 0,
        sound: 'block.amethyst_block.chime', soundVolume: 0.6, soundPitch: 1.4, particle: 'end_rod' },
    { name: 'brewing-healthboost', ingredient: 'golden_apple', effect: 'HealthBoost', duration: 1200, amplifier: 0,
        sound: 'entity.player.levelup', soundVolume: 0.25, soundPitch: 1.8, particle: 'heart' },
    { name: 'brewing-hunger', ingredient: 'rotten_flesh', effect: 'Hunger', duration: 1200, amplifier: 0,
        sound: 'entity.player.burp', soundVolume: 0.35, soundPitch: 0.7, particle: 'sneeze' },
    { name: 'brewing-nausea', ingredient: 'brown_mushroom', effect: 'Nausea', duration: 600, amplifier: 0,
        sound: 'entity.enderman.teleport', soundVolume: 0.3, soundPitch: 0.7, particle: 'witch' },
    { name: 'brewing-resistance', ingredient: 'iron_ingot', effect: 'Resistance', duration: 1200, amplifier: 0,
        sound: 'block.anvil.land', soundVolume: 0.2, soundPitch: 1.9, particle: 'end_rod' },
    { name: 'brewing-saturation', ingredient: 'baked_potato', effect: 'Saturation', duration: 1, amplifier: 4,
        sound: 'entity.generic.eat', soundVolume: 0.3, soundPitch: 1.2, particle: 'campfire_cosy_smoke' },
]

export const brewingBehaviorCases = new Map(customRecipes.map(recipe => [recipe.name, {
    stage: recipe.name.replace('brewing-', 'brew-'),
    sound: `minecraft:${recipe.sound}`, soundVolume: recipe.soundVolume, soundPitch: recipe.soundPitch, particle: recipe.particle,
    negativeWindowTicks: 1,
    trigger: input => brew(input, { ...recipe, custom: true }),
    verify: input => verifyCustom(recipe, input),
}]))

brewingBehaviorCases.set('brewing-lingering', {
    stage: 'brew-lingering', negativeWindowTicks: 1,
    sound: 'minecraft:entity.ender_dragon.flap', soundVolume: 0.3, soundPitch: 1.2, particle: 'dragon_breath',
    trigger: input => brew(input, { name: 'brewing-lingering', ingredient: 'sugar', custom: false }),
    verify: input => {
        verifyVanilla(input)
        const effect = input.active.received.effects.find(candidate => candidate.name === 'Speed')
        input.context.expect(input.unlearned.received.effects.length === 0, 'Unlearned vanilla potion has no custom duration override')
        input.context.expect(effect?.duration === 6000 && effect?.amplifier === 0,
            'Learned Lingering produces a withdrawn speed potion with 6000 ticks of duration', effect)
        return { assertions: ['both trials naturally brew and withdraw vanilla speed potions', 'learned potion has the configured 6000-tick duration while unlearned potion has no override'], measurements: { unlearned: input.unlearned, active: input.active } }
    },
})

brewingBehaviorCases.set('brewing-super-heated', {
    stage: 'brew-super-heated', negativeWindowTicks: 1,
    sound: 'minecraft:block.lava.pop', soundVolume: 0.3, soundPitch: 0.8, particle: 'lava',
    trigger: input => brew(input, { name: 'brewing-super-heated', ingredient: 'sugar', custom: false }),
    verify: input => {
        verifyVanilla(input)
        input.context.expect(input.unlearned.elapsedMillis >= 15000, 'Unlearned vanilla brewing takes its normal brew duration')
        input.context.expect(input.active.elapsedMillis < input.unlearned.elapsedMillis * 0.8,
            'Learned stand above lava completes the same recipe at least 20 percent faster', { unlearned: input.unlearned.elapsedMillis, active: input.active.elapsedMillis })
        return { assertions: ['both trials naturally brew and withdraw the same vanilla potion', 'learned brewing above lava completes at least 20 percent faster'], measurements: { unlearned: input.unlearned, active: input.active } }
    },
})
