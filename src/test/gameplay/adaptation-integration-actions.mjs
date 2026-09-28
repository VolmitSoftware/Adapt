function plain(value) {
    if (value == null) return ''
    if (typeof value === 'string') {
        if (value.startsWith('{') || value.startsWith('[')) {
            try { return plain(JSON.parse(value)) } catch { return value }
        }
        return value.replace(/\u00a7./g, '')
    }
    if (Array.isArray(value)) return value.map(plain).join('')
    if (typeof value !== 'object') return ''
    if (value.type && 'value' in value) return plain(value.value)
    if ('text' in value || 'extra' in value) return plain(value.text) + plain(value.extra)
    return Object.values(value).map(plain).join('')
}

function overlay(actor, target) {
    const index = actor.bot.registry.entitiesByName.text_display.metadataKeys.indexOf('text')
    return Object.values(actor.bot.entities)
        .filter(entity => entity.name === 'text_display'
            && Math.abs(entity.position.x - target.position.x) < 1.5
            && Math.abs(entity.position.z - target.position.z) < 1.5)
        .map(entity => ({ entityId: entity.id, text: plain(entity.metadata[index]) }))
}

async function insight(input) {
    const { actor, opponent, snapshot, context } = input
    const before = await snapshot()
    context.expect(before.integration.gloss?.plugin === 'Gloss' && before.integration.gloss.enabled,
        'The real enabled Gloss plugin publishes its API')
    const targetId = before.integration.target.entityId
    await context.waitUntil(() => Boolean(actor.bot.entities[targetId]), { label: 'real inspection cow visible', timeoutMs: 5000 })
    const target = actor.bot.entities[targetId]
    await actor.bot.lookAt(target.position.offset(0, 0.9, 0), true)
    await actor.bot.waitForTicks(55)
    const shown = overlay(actor, target)
    const observer = overlay(opponent, target)
    const after = await snapshot()
    await actor.bot.lookAt(actor.bot.entity.position.offset(0, 4, -10), true)
    await actor.bot.waitForTicks(55)
    return { before, after, shown, observer, lookedAway: overlay(actor, target) }
}

async function fell(input) {
    const { actor, context, snapshot } = input
    const before = await snapshot()
    context.expect(before.integration.iris?.plugin === 'Iris' && before.integration.iris.enabled,
        'The real enabled Iris plugin publishes its feller API')
    context.expect(before.integration.logs === 4 && before.integration.markedLogs.every(Boolean),
        'The actual Iris service recognizes all four initial tree logs')
    context.expect(!before.integration.standaloneAllowed, 'The ordinary player cannot invoke standalone Iris felling')
    const location = actor.bot.entity.position.clone().set(3, 100, 0)
    await context.waitUntil(() => actor.bot.blockAt(location)?.name === 'oak_log', { label: 'Iris tree reaches the client', timeoutMs: 10000 })
    actor.bot.setControlState('sneak', true)
    try {
        await actor.bot.waitForTicks(3)
        await actor.bot.dig(actor.bot.blockAt(location))
        await actor.bot.waitForTicks(120)
        return { before, after: await snapshot() }
    } finally {
        actor.bot.setControlState('sneak', false)
    }
}

export const integrationBehaviorCases = new Map([
    ['discovery-insight', {
        stage: 'integration-insight', trigger: insight, negativeWindowTicks: 12,
        description: 'looking at a mob adds live attributes to a real private Gloss overlay and clears them when looking away',
        async verify({ context, unlearned, active }) {
            const text = entries => entries.map(entry => entry.text).join('\n')
            const control = text(unlearned.shown)
            const details = text(active.shown)
            context.expect(/Insight Cow/.test(control) && !/Speed|Jump/.test(control), 'Real Gloss baseline shows its normal named-mob overlay without Insight attributes')
            context.expect(/Insight Cow/.test(details) && /Speed/.test(details) && /Jump/.test(details), 'Learned natural gaze adds actual movement and jump attributes to Gloss text-display metadata')
            context.expect(!/Speed|Jump/.test(text(active.observer)), 'Unlearned observer does not receive private Insight details')
            context.expect(!/Speed|Jump/.test(text(active.lookedAway)), 'Looking away removes the actual Insight detail text')
            return { assertions: ['real Gloss API and ordinary baseline overlays exist', 'learned gaze adds live attributes to real private text displays', 'looking away clears those details'], measurements: { unlearned, active } }
        },
    }],
    ['axe-iris-feller', {
        stage: 'integration-iris', prepare: async ({ equip }) => equip('iron_axe'), trigger: fell, negativeWindowTicks: 12,
        description: 'one natural sneak axe break invokes the real Iris feller and pays per-log hunger while recovering the whole tree',
        sound: 'minecraft:block.enchantment_table.use', soundVolume: 0.55, soundPitch: 1.35, particle: 'end_rod', particleCount: 8, particleOffset: 0.25,
        async verify({ context, unlearned, active }) {
            context.expect(unlearned.after.integration.logs === 3 && unlearned.after.integration.recoveredLogs - unlearned.before.integration.recoveredLogs === 1,
                'Without Adapt learning or Iris standalone permission, one ordinary break removes and recovers exactly one log')
            context.expect(active.after.integration.logs === 0 && active.after.integration.recoveredLogs - active.before.integration.recoveredLogs === 4,
                'The real learned integration erodes and recovers all four actual logs')
            context.expect(unlearned.before.food === unlearned.after.food && active.before.food - active.after.food === 8,
                'Only the learned Iris run charges exactly two hunger per actual removed log')
            context.expect(active.after.integration.toolDamage >= 0 && active.after.integration.toolDamage <= 4,
                'Real axe durability remains within the per-log preservation bounds')
            return { assertions: ['real Iris service validates initial tree provenance', 'unlearned ordinary player breaks only one log', 'learned natural break fells and recovers all four logs while charging eight food points'], measurements: { unlearned, active } }
        },
    }],
])
