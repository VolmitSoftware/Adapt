import { execFile } from 'node:child_process'
import { readFile } from 'node:fs/promises'
import { join } from 'node:path'
import { promisify } from 'node:util'

const execute = promisify(execFile)

export default {
  name: 'adapt-advancement-sql',
  description: 'Verify advancement SQL table isolation and player progress through reconnects.',
  async run(context) {
    context.expect(context.report.server.instance.startsWith('adapt-sql-'), 'Disposable SQL instance required')
    const directory = context.report.server.directory
    const query = async statement => {
      const result = await execute('mysql', ['--no-defaults', '--protocol=SOCKET',
        `--socket=/tmp/${context.report.server.instance}.sock`, '--user=root',
        '--database=adapt_validation', '--batch', '--skip-column-names', '--execute', statement],
      { timeout: 5000 })
      return result.stdout.trim().split('\n').filter(Boolean)
    }
    const expected = ['ADAPT_ADVANCEMENT_MIGRATIONS', 'ADAPT_ADVANCEMENT_PLAYERS', 'ADAPT_ADVANCEMENT_PROGRESS',
      'ADAPT_ADVANCEMENT_TEAMS', 'ADAPT_ADVANCEMENT_UNREDEEMED', 'ADAPT_DATA', 'ADAPT_DATA_FENCE']
    const evidence = context.report.advancementSql = {}
    let actor
    try {
      await context.step('Keep advancement tables separate from other plugin data', async () => {
        evidence.tables = await query('SHOW TABLES')
        context.expect(expected.every(table => evidence.tables.includes(table)), 'All seven Adapt tables exist', evidence)
        for (const table of ['Teams', 'Players', 'Advancements', 'Unredeemed']) {
          const rows = await query(`SELECT Owner FROM \`${table}\``)
          context.expect(rows.length === 1 && rows[0] === 'other-plugin', `${table} remains owned by another plugin`)
        }
      })
      await context.step('Load advancement roots and retain progress after reconnect', async () => {
        actor = await context.connectActor('AdaptSQLPlayer')
        const uuid = actor.bot.player.uuid
        context.expect(/^[a-f0-9-]{36}$/.test(uuid), 'Player has a valid UUID')
        const snapshot = async () => ({
          team: await query(`SELECT TeamID FROM ADAPT_ADVANCEMENT_PLAYERS WHERE UUID='${uuid}'`),
          progress: await query(`SELECT Namespace, \`Key\`, Progression FROM ADAPT_ADVANCEMENT_PROGRESS WHERE TeamID=(SELECT TeamID FROM ADAPT_ADVANCEMENT_PLAYERS WHERE UUID='${uuid}') ORDER BY Namespace, \`Key\``),
        })
        await context.waitUntil(async () => {
          evidence.before = await snapshot()
          return evidence.before.team.length === 1 && evidence.before.progress.length > 0
        }, { label: 'SQL stores player and skill-root advancement progress', timeoutMs: 30000, intervalMs: 250 })
        context.bot.chat(`/adapt gui player=${actor.bot.username}`)
        await context.waitUntil(() => Boolean(actor.bot.currentWindow), { label: 'Adapt menu opens with SQL profile ready', timeoutMs: 10000 })
        actor.bot.closeWindow(actor.bot.currentWindow)
        actor = await actor.reconnectAfter(() => actor.bot.quit('SQL persistence check'), { timeoutMs: 30000 })
        await context.waitUntil(async () => {
          evidence.after = await snapshot()
          return JSON.stringify(evidence.after) === JSON.stringify(evidence.before)
        }, { label: 'Reconnect retains advancement team and progress', timeoutMs: 30000, intervalMs: 250 })
        context.bot.chat(`/adapt gui player=${actor.bot.username}`)
        await context.waitUntil(() => Boolean(actor.bot.currentWindow), { label: 'Adapt menu opens after reconnect', timeoutMs: 10000 })
        actor.bot.closeWindow(actor.bot.currentWindow)
      })
      const log = await readFile(join(directory, 'logs', 'latest.log'), 'utf8')
      evidence.errors = log.split('\n').filter(line => /SQLException|NoSuchMethodError|Advancements will be disabled|Failed to grant advancement|Error occurred while (?:enabling|disabling) Adapt/.test(line))
      context.expect(evidence.errors.length === 0, 'Runtime log has no advancement SQL or linkage errors', evidence.errors)
    } finally {
      actor?.bot.clearControlStates()
    }
  },
}
