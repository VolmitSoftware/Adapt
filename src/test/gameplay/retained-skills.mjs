import { readFile } from 'node:fs/promises'
import { fixtureJson } from './fixture-json.mjs'
import { hasCommittedXp } from './all-skills.mjs'
import { assertSkillCoverage } from './skill-matrix.mjs'

export default {
    name: 'adapt-retained-skills',
    description: 'Reconnect the original ordinary player after a real server restart and verify every committed skill payout.',
    async run(context) {
        const previous = JSON.parse(await readFile(context.options.command, 'utf8'))
        context.expect(previous.status === 'passed' && previous.server.instance === context.report.server.instance,
            'Retention evidence must come from a successful skill run on this same isolated instance')
        context.expect(Number.isSafeInteger(previous.adapt.processId), 'Previous run records its real server process')
        const retained = previous.adapt.reconnect.skills.map(entry => ({ skill: entry.skill, player: previous.adapt.reconnect.player, after: entry.after }))
        assertSkillCoverage(retained.map(entry => entry.skill))
        const actor = await context.connectActor(previous.adapt.reconnect.player)
        let sequence = 0
        let state
        await context.waitUntil(async () => {
            const token = `r${++sequence}`
            state = await fixtureJson(context, `/adaptqa snapshot ${actor.bot.username} ${token}`, `SNAPSHOT ${token}`)
            return hasCommittedXp(state, retained)
        }, { label: 'all committed skill XP reloaded from disk', timeoutMs: 30000, intervalMs: 1000 })
        context.expect(state.processId !== previous.adapt.processId, 'A different real server process loaded the saved player')
        context.expect(hasCommittedXp(state, retained), 'Every natural skill payout survives a complete server restart')
        context.report.adapt = {
            processRestartTested: true,
            beforeProcess: previous.adapt.processId,
            afterProcess: state.processId,
            player: actor.bot.username,
            skills: retained.map(entry => ({ skill: entry.skill, before: entry.after, after: state.committedXp[entry.skill] })),
        }
    },
}
