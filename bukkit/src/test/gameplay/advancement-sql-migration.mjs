import { execFile } from 'node:child_process'
import { createHash } from 'node:crypto'
import { readFile, writeFile } from 'node:fs/promises'
import { basename, join } from 'node:path'
import { promisify } from 'node:util'

const execute = promisify(execFile)
const namespace = 'adapt_skill_swords'
const importedKey = 'challenge_sword_1k'
const mergedName = 'AdaptSQLMerged'
const legacyNames = ['AdaptSQLLegacy', 'AdaptSQLMate']

function uuid(name) {
  const bytes = createHash('md5').update(`OfflinePlayer:${name}`).digest()
  bytes[6] = (bytes[6] & 15) | 48
  bytes[8] = (bytes[8] & 63) | 128
  const hex = bytes.toString('hex')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

function database(directory) {
  const instance = basename(directory)
  if (!/^adapt-sql-migration-[a-z0-9-]+$/.test(instance)) throw new Error('Disposable SQL migration instance required')
  return async statement => {
    const result = await execute('mysql', ['--no-defaults', '--protocol=SOCKET',
      `--socket=/tmp/${instance}.sock`, '--user=root', '--database=adapt_validation',
      '--batch', '--skip-column-names', '--execute', statement], { timeout: 10000 })
    return result.stdout.trim().split('\n').filter(Boolean)
  }
}

async function sourceRows(query) {
  return {
    teams: await query('SELECT ID FROM Teams ORDER BY ID'),
    players: await query('SELECT HEX(UUID),HEX(Name),TeamID FROM Players ORDER BY UUID'),
    progress: await query('SELECT HEX(Namespace),HEX(`Key`),TeamID,Progression FROM Advancements ORDER BY Namespace,`Key`,TeamID'),
    rewards: await query('SELECT HEX(Namespace),HEX(`Key`),TeamID,GiveRewards FROM Unredeemed ORDER BY Namespace,`Key`,TeamID'),
  }
}

function statePath(directory) {
  return join(directory, 'sql-validation', 'migration-fixture.json')
}

function assert(condition, label) {
  if (!condition) throw new Error(label)
}

export async function seedMigration(directory) {
  const query = database(directory)
  await query(`
    CREATE TABLE Teams(ID INT NOT NULL PRIMARY KEY AUTO_INCREMENT) ENGINE=InnoDB;
    CREATE TABLE Players(UUID VARCHAR(36) NOT NULL PRIMARY KEY,Name VARCHAR(16) NOT NULL,TeamID INT NOT NULL,
      FOREIGN KEY(TeamID) REFERENCES Teams(ID) ON DELETE CASCADE ON UPDATE CASCADE) ENGINE=InnoDB;
    CREATE TABLE Advancements(Namespace VARCHAR(127) NOT NULL,\`Key\` VARCHAR(127) NOT NULL,TeamID INT NOT NULL,
      Progression INT NOT NULL DEFAULT 0,PRIMARY KEY(Namespace,\`Key\`,TeamID),
      FOREIGN KEY(TeamID) REFERENCES Teams(ID) ON DELETE CASCADE ON UPDATE CASCADE) ENGINE=InnoDB;
    CREATE TABLE Unredeemed(Namespace VARCHAR(127) NOT NULL,\`Key\` VARCHAR(127) NOT NULL,TeamID INT NOT NULL,
      GiveRewards INT NOT NULL,PRIMARY KEY(Namespace,\`Key\`,TeamID),
      FOREIGN KEY(Namespace,\`Key\`,TeamID) REFERENCES Advancements(Namespace,\`Key\`,TeamID) ON DELETE CASCADE ON UPDATE CASCADE) ENGINE=InnoDB;
    CREATE TABLE ADAPT_ADVANCEMENT_TEAMS(ID INT NOT NULL PRIMARY KEY AUTO_INCREMENT) ENGINE=InnoDB;
    CREATE TABLE ADAPT_ADVANCEMENT_PLAYERS(UUID VARCHAR(36) NOT NULL PRIMARY KEY,Name VARCHAR(16) NOT NULL,TeamID INT NOT NULL,
      FOREIGN KEY(TeamID) REFERENCES ADAPT_ADVANCEMENT_TEAMS(ID) ON DELETE CASCADE ON UPDATE CASCADE) ENGINE=InnoDB;
    CREATE TABLE ADAPT_ADVANCEMENT_PROGRESS(Namespace VARCHAR(127) NOT NULL,\`Key\` VARCHAR(127) NOT NULL,TeamID INT NOT NULL,
      Progression INT NOT NULL DEFAULT 0,PRIMARY KEY(Namespace,\`Key\`,TeamID),
      FOREIGN KEY(TeamID) REFERENCES ADAPT_ADVANCEMENT_TEAMS(ID) ON DELETE CASCADE ON UPDATE CASCADE) ENGINE=InnoDB;
    CREATE TABLE ADAPT_ADVANCEMENT_UNREDEEMED(Namespace VARCHAR(127) NOT NULL,\`Key\` VARCHAR(127) NOT NULL,TeamID INT NOT NULL,
      GiveRewards INT NOT NULL,PRIMARY KEY(Namespace,\`Key\`,TeamID),
      FOREIGN KEY(Namespace,\`Key\`,TeamID) REFERENCES ADAPT_ADVANCEMENT_PROGRESS(Namespace,\`Key\`,TeamID) ON DELETE CASCADE ON UPDATE CASCADE) ENGINE=InnoDB;
    INSERT INTO Teams VALUES(1),(2);
    INSERT INTO Players VALUES('${uuid(mergedName)}','PreviousName',1),
      ('${uuid(legacyNames[0])}','${legacyNames[0]}',1),('${uuid(legacyNames[1])}','${legacyNames[1]}',1),
      ('${uuid('ForeignSQLUser')}','ForeignSQLUser',2);
    INSERT INTO Advancements VALUES('${namespace}','skill_swords',1,1),('${namespace}','challenge_sword_100',1,1),
      ('${namespace}','${importedKey}',1,1),('foreign_skill_swords','foreign_key',1,72),
      ('adaptXskill_swords','lookalike_key',1,81),('foreign_skill_swords','foreign_key',2,93);
    INSERT INTO Unredeemed VALUES('${namespace}','challenge_sword_100',1,1),('${namespace}','${importedKey}',1,1),
      ('foreign_skill_swords','foreign_key',1,1),('foreign_skill_swords','foreign_key',2,1);
    INSERT INTO ADAPT_ADVANCEMENT_TEAMS VALUES(1),(2);
    INSERT INTO ADAPT_ADVANCEMENT_PLAYERS VALUES('${uuid(mergedName)}','${mergedName}',1),
      ('${uuid('CanonicalGuard')}','CanonicalGuard',2);
    INSERT INTO ADAPT_ADVANCEMENT_PROGRESS VALUES('${namespace}','challenge_sword_100',1,0),
      ('${namespace}','canonical_sentinel',2,17);
    INSERT INTO ADAPT_ADVANCEMENT_UNREDEEMED VALUES('${namespace}','challenge_sword_100',1,0);
  `)
  await writeFile(statePath(directory), JSON.stringify({ source: await sourceRows(query) }, null, 2))
}

export async function verifyMigrationBeforeJoin(directory, phase = 'imported') {
  const query = database(directory)
  const state = JSON.parse(await readFile(statePath(directory), 'utf8'))
  const source = await sourceRows(query)
  assert(JSON.stringify(source) === JSON.stringify(state.source), 'Original SQL rows are byte-for-row unchanged')
  assert((await query("SELECT Completed FROM ADAPT_ADVANCEMENT_MIGRATIONS WHERE ID='generic-advancements'")).join() === '1', 'Import completion committed before login')
  const members = await query(`SELECT UUID,TeamID FROM ADAPT_ADVANCEMENT_PLAYERS WHERE UUID IN ('${uuid(legacyNames[0])}','${uuid(legacyNames[1])}') ORDER BY UUID`)
  assert(members.length === 2, 'Both legacy teammates imported before login')
  const teams = members.map(row => Number(row.split('\t')[1]))
  assert(teams[0] === teams[1] && teams[0] > 2, 'Legacy teammates share a fresh team rather than a colliding ID')
  assert((await query(`SELECT Name,TeamID FROM ADAPT_ADVANCEMENT_PLAYERS WHERE UUID='${uuid(mergedName)}'`)).join() === `${mergedName}\t1`, 'Canonical player name and team preserved')
  assert((await query("SELECT Progression FROM ADAPT_ADVANCEMENT_PROGRESS WHERE `Key`='canonical_sentinel' AND TeamID=2")).join() === '17', 'Unrelated canonical team preserved')
  assert((await query(`SELECT UUID FROM ADAPT_ADVANCEMENT_PLAYERS WHERE UUID='${uuid('ForeignSQLUser')}'`)).length === 0, 'Foreign-only player excluded')
  assert((await query("SELECT Namespace FROM ADAPT_ADVANCEMENT_PROGRESS WHERE Namespace IN ('foreign_skill_swords','adaptXskill_swords')")).length === 0, 'Foreign and lookalike namespaces excluded')
  const progress = await query(`SELECT TeamID,Progression FROM ADAPT_ADVANCEMENT_PROGRESS WHERE Namespace='${namespace}' AND \`Key\`='${importedKey}' ORDER BY TeamID`)
  if (phase === 'imported') {
    assert(JSON.stringify(progress) === JSON.stringify([`1\t1`, `${teams[0]}\t1`]), 'Registered achievement imported into existing and fresh teams')
    assert((await query(`SELECT GiveRewards FROM ADAPT_ADVANCEMENT_UNREDEEMED WHERE Namespace='${namespace}' AND \`Key\`='${importedKey}' ORDER BY TeamID`)).join() === '1,1', 'Pending rewards imported alongside new progress')
    assert((await query("SELECT Progression FROM ADAPT_ADVANCEMENT_PROGRESS WHERE `Key`='challenge_sword_100' AND TeamID=1")).join() === '0', 'Canonical progress wins over old completion')
    assert((await query("SELECT GiveRewards FROM ADAPT_ADVANCEMENT_UNREDEEMED WHERE `Key`='challenge_sword_100' AND TeamID=1")).join() === '0', 'Canonical pending reward wins')
  } else {
    assert(progress.length === 0, 'Deleted imported progress remains absent after cold restart')
    assert((await query(`SELECT TeamID FROM ADAPT_ADVANCEMENT_UNREDEEMED WHERE Namespace='${namespace}' AND \`Key\`='${importedKey}'`)).length === 0, 'Deleted progress does not regain pending rewards after cold restart')
    assert((await query(`SELECT Progression FROM ADAPT_ADVANCEMENT_PROGRESS WHERE Namespace='${namespace}' AND \`Key\`='challenge_sword_100' AND TeamID=${teams[0]}`)).join() === '1', 'Progress survives consumption of its separate pending reward')
    assert((await query(`SELECT GiveRewards FROM ADAPT_ADVANCEMENT_UNREDEEMED WHERE Namespace='${namespace}' AND \`Key\`='challenge_sword_100' AND TeamID=${teams[0]}`)).length === 0, 'Consumed reward stays absent beside retained progress')
  }
  const log = await readFile(join(directory, 'logs', 'latest.log'), 'utf8')
  assert(!/UUID of player|logged in with entity id/.test(log), 'Startup SQL assertions precede every bot login')
  state[phase] = { teams, progress, checkedBeforeLogin: true }
  await writeFile(statePath(directory), JSON.stringify(state, null, 2))
  return state[phase]
}

export async function consumeRewardAndRemoveProgress(directory) {
  const query = database(directory)
  const state = JSON.parse(await readFile(statePath(directory), 'utf8'))
  const team = state.imported.teams[0]
  assert(Number.isSafeInteger(team) && team > 2, 'Fresh imported team recorded before consuming reward')
  assert((await query(`SELECT GiveRewards FROM ADAPT_ADVANCEMENT_UNREDEEMED WHERE Namespace='${namespace}' AND \`Key\`='challenge_sword_100' AND TeamID=${team}`)).join() === '1', 'Pending reward exists before consumption')
  await query(`DELETE FROM ADAPT_ADVANCEMENT_UNREDEEMED WHERE Namespace='${namespace}' AND \`Key\`='challenge_sword_100' AND TeamID=${team}`)
  await query(`DELETE FROM ADAPT_ADVANCEMENT_PROGRESS WHERE Namespace='${namespace}' AND \`Key\`='${importedKey}'`)
}

export default {
  name: 'adapt-advancement-sql-migration',
  description: 'Verify isolated SQL import, client advancement progress, and restart without replay.',
  async run(context) {
    const directory = context.report.server.directory
    const query = database(directory)
    const phase = process.env.ADAPT_SQL_MIGRATION_PHASE || 'imported'
    context.expect(['imported', 'restarted'].includes(phase), 'Known SQL migration phase')
    const state = JSON.parse(await readFile(statePath(directory), 'utf8'))
    context.expect(state[phase]?.checkedBeforeLogin, 'External startup verification completed before bot login')
    const evidence = context.report.sqlMigration = { phase, startup: state[phase] }
    let actor
    const observe = bot => {
      const observed = []
      bot._client.on('advancements', packet => {
        for (const entry of packet.progressMapping || []) {
          if (entry.key !== `${namespace}:${importedKey}`) continue
          observed.push({ key: entry.key, criteria: entry.value.map(criterion => ({
            name: criterion.criterionIdentifier, completed: criterion.criterionProgress != null,
          })) })
        }
      })
      return observed
    }
    const progress = () => query(`SELECT TeamID,Progression FROM ADAPT_ADVANCEMENT_PROGRESS WHERE Namespace='${namespace}' AND \`Key\`='${importedKey}' ORDER BY TeamID`)
    const openMenu = async label => {
      await context.waitUntil(() => {
        if (actor.bot.currentWindow) return true
        context.bot.chat(`/adapt gui player=${mergedName}`)
        return false
      }, { label, timeoutMs: 15000, intervalMs: 1000 })
      actor.bot.closeWindow(actor.bot.currentWindow)
    }
    try {
      await context.step('Load imported player progress and open the skills menu', async () => {
        actor = await context.connectActor(mergedName)
        const packets = observe(actor.bot)
        if (phase === 'imported') {
          await context.waitUntil(() => packets.some(packet => packet.criteria.length > 0 && packet.criteria.every(criterion => criterion.completed)),
            { label: 'Client receives completed criteria for the registered imported advancement', timeoutMs: 30000 })
          evidence.advancementPackets = packets
        }
        await openMenu('Imported player can open Adapt menu')
        evidence.beforeReconnect = await progress()
        actor = await actor.reconnectAfter(() => actor.bot.quit('SQL migration reconnect'), { timeoutMs: 30000 })
        await openMenu('Adapt menu opens after reconnect')
        await context.sleep(5000)
        evidence.afterReconnect = await progress()
        context.expect(JSON.stringify(evidence.beforeReconnect) === JSON.stringify(evidence.afterReconnect), 'Reconnect preserves imported progress state')
        if (phase === 'restarted') context.expect(evidence.afterReconnect.length === 0, 'Deleted progress stays absent after login and reconnect')
      })
      await context.step('Preserve all original SQL records', async () => {
        context.expect(JSON.stringify(await sourceRows(query)) === JSON.stringify(state.source), 'All original rows remain byte-for-row unchanged')
        if (phase === 'restarted') {
          context.expect((await query(`SELECT TeamID FROM ADAPT_ADVANCEMENT_UNREDEEMED WHERE Namespace='${namespace}' AND \`Key\`='${importedKey}'`)).length === 0, 'Deleted progress does not regain a pending reward')
          context.expect((await query(`SELECT GiveRewards FROM ADAPT_ADVANCEMENT_UNREDEEMED WHERE Namespace='${namespace}' AND \`Key\`='challenge_sword_100' AND TeamID=${state.imported.teams[0]}`)).length === 0, 'Consumed reward is not restored beside retained progress')
        }
      })
      const log = await readFile(join(directory, 'logs', 'latest.log'), 'utf8')
      evidence.errors = log.split('\n').filter(line => /SQLException|NoSuchMethodError|Advancements will be disabled|Failed to grant advancement|Error occurred while (?:enabling|disabling) Adapt/.test(line))
      context.expect(evidence.errors.length === 0, 'Runtime has no advancement SQL or linkage errors', evidence.errors)
    } finally {
      actor?.bot.clearControlStates()
    }
  },
}
