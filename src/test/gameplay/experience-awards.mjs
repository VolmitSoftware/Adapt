import { randomBytes } from 'node:crypto'
import { fixtureJson } from './fixture-json.mjs'

const TIMEOUT = 10000

export default {
    name: 'adapt-experience-awards',
    description: 'Verify direct awards, React fast mining, golem essence claims and release publish one observable experience reward.',
    async run(context) {
        const suffix = randomBytes(3).toString('hex')
        const actor = await context.connectActor(`AQAexp${suffix}`)
        const other = await context.connectActor(`AQApeer${suffix}`)
        const bot = actor.bot
        const fixture = (action, extra = '') => fixtureJson(context,
            `/adaptqa experience ${bot.username} ${action}${extra ? ` ${extra}` : ''}`, `EXPERIENCE ${action}`, TIMEOUT)
        const evidence = context.report.experienceAwards = {}
        const snapshot = () => fixture('snapshot')
        const reward = async (before, expectedPoints, label) => {
            let after
            await context.waitUntil(async () => {
                after = await snapshot()
                return after.events === before.events + 1 && after.vanillaXp === before.vanillaXp + expectedPoints
                    && (expectedPoints <= 0 || after.discoveryXp > before.discoveryXp)
            }, { label, timeoutMs: TIMEOUT })
            context.expect(after.lastAmount === expectedPoints, 'The event exposes the adjusted reward', { before, after })
            context.expect(after.unity === before.unity + (expectedPoints > 0 ? 1 : 0), 'Unity reacts once to a positive reward', { before, after })
            context.expect(expectedPoints <= 0 || after.discoveryXp > before.discoveryXp, 'A positive reward grants Discovery XP', { before, after })
            await context.waitUntil(() => bot.experience.points === after.vanillaXp,
                { label: 'The client receives the awarded vanilla XP balance', timeoutMs: TIMEOUT })
            context.expect(after.orbs === 0, 'The direct award creates no experience orbs', after)
            return after
        }
        let setup = false
        try {
            await context.command(`/adaptqa setup ${bot.username} ${other.bot.username}`, /^ADAPT_QA SETUP /, 60000)
            setup = true
            await context.command('/adaptqa learn discovery-unity 7', /^ADAPT_QA LEARN /, TIMEOUT)
            await context.command('/adaptqa learn-add enchanting-echo-of-knowledge 5', /^ADAPT_QA LEARN-ADD /, TIMEOUT)
            evidence.initial = await fixture('prepare')
            await context.step('Direct reward publishes one event and charges the held enchanted book', async () => {
                const before = await snapshot()
                await fixture('award', '3')
                const after = await reward(before, 3, 'Direct award settles once')
                context.expect(after.bookCharge > before.bookCharge, 'Echo of Knowledge sees the direct award', { before, after })
                evidence.direct = { before, after }
            })
            await context.step('Event changes control the actual vanilla and adaptation reward', async () => {
                await fixture('override', '7')
                const before = await snapshot()
                await fixture('award', '25')
                const after = await reward(before, 7, 'Adjusted award settles once')
                context.expect(after.bookCharge > before.bookCharge, 'Echo sees the adjusted positive reward', { before, after })
                evidence.adjusted = { before, after }
                for (const amount of [0, -5]) {
                    await fixture('override', String(amount))
                    const blockedBefore = await snapshot()
                    await fixture('award', '25')
                    const blockedAfter = await snapshot()
                    context.expect(blockedAfter.events === blockedBefore.events + 1 && blockedAfter.lastAmount === amount,
                        'A suppressed reward still publishes exactly one adjusted event', { blockedBefore, blockedAfter })
                    for (const metric of ['vanillaXp', 'unity', 'discoveryXp', 'bookCharge', 'bookLevel']) {
                        context.expect(blockedAfter[metric] === blockedBefore[metric], `${metric} ignores a suppressed reward`, { blockedBefore, blockedAfter })
                    }
                    evidence[`suppressed${amount}`] = { before: blockedBefore, after: blockedAfter }
                }
                await fixture('natural')
                const ignoredBefore = await snapshot()
                await fixture('award', '0')
                await fixture('award', '-5')
                const ignoredAfter = await snapshot()
                context.expect(ignoredAfter.events === ignoredBefore.events && ignoredAfter.vanillaXp === ignoredBefore.vanillaXp,
                    'Nonpositive direct inputs produce no reward event', { ignoredBefore, ignoredAfter })
            })
            await context.step('React diamond mining awards XP once without an orb', async () => {
                bot.setQuickBarSlot(1)
                await fixture('ore')
                let ore
                await context.waitUntil(() => {
                    ore = bot.blockAt(bot.entity.position.floored().offset(3, 0, 2))
                    return ore?.name === 'diamond_ore'
                }, { label: 'Diamond ore reaches the mining client', timeoutMs: TIMEOUT })
                const before = await snapshot()
                await bot.dig(ore)
                let after
                await context.waitUntil(async () => {
                    after = await snapshot()
                    return after.events > before.events
                }, { label: 'React fast mining publishes the reward', timeoutMs: TIMEOUT })
                after = await reward(before, after.lastAmount, 'React mining reward settles once')
                context.expect(after.lastAmount >= 3 && after.lastAmount <= 7, 'Diamond ore grants its vanilla XP range', after)
                evidence.reactMining = { before, after }
            })
            await context.step('The real Gatherer essence menu claims one XP reward', async () => {
                bot.setQuickBarSlot(2)
                const before = await snapshot()
                await context.waitUntil(() => Boolean(bot.entities[before.golem]), { label: 'Owned golem reaches the client', timeoutMs: TIMEOUT })
                await bot.lookAt(bot.entities[before.golem].position.offset(0, 0.4, 0), true)
                const opened = actor.waitForEvent('windowOpen', () => true, TIMEOUT)
                bot._client.write('use_entity', { target: before.golem, hand: 'main_hand', location: { x: 0, y: 0.4, z: 0 }, sneaking: false })
                await opened
                await context.waitUntil(() => bot.currentWindow?.slots.slice(0, 45).some(item => item?.name === 'experience_bottle'),
                    { label: 'The essence claim appears in the real menu', timeoutMs: TIMEOUT })
                const slot = bot.currentWindow.slots.slice(0, 45).findIndex(item => item?.name === 'experience_bottle')
                await bot.clickWindow(slot, 0, 0)
                const after = await reward(before, 60, 'Golem essence claim settles once')
                context.expect(after.essence === 0, 'The claimed essence leaves the Gatherer', after)
                bot.closeWindow(bot.currentWindow)
                evidence.golemClaim = { before, after, slot }
            })
            await context.step('Releasing a golem returns essence through the same reward event', async () => {
                await fixture('refill')
                const before = await snapshot()
                await fixture('release')
                const after = await reward(before, 60, 'Golem release reward settles once')
                evidence.golemRelease = { before, after }
            })
        } finally {
            for (const player of [actor, other]) {
                player.bot.clearControlStates()
                if (player.bot.currentWindow) player.bot.closeWindow(player.bot.currentWindow)
            }
            if (setup && !context.signal.aborted) await context.command('/adaptqa cleanup', /^ADAPT_QA CLEANUP$/, 15000)
        }
    },
}
