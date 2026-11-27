import { readFileSync, readdirSync, existsSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

const groups = [
  { name: 'skills', plan: 'all-skills,restart,retained-skills=@all-skills' },
  { name: 'targeting', plan: 'damage-targeting=false,feedback-calibration' },
  { name: 'preferences', plan: 'player-preferences,player-settings-lock,preference-policy-reload,catalog-preference-behavior' },
  { name: 'blink', plan: 'blink-movement,blink-preferences,restart,blink-preferences=@blink-preferences' },
  { name: 'temporal', plan: 'temporal-overlap-conservation,skill-interactions,death-save-persistence,restart,death-save-persistence=verify-restart' },
  { name: 'items', plan: 'items-conservation,custom-recipe-ingredients,experience-awards,demolition-transform-conservation,demolition-transform-profile' },
  { name: 'mining', plan: 'mining-cross-skill-conservation,shared-pain-blocked-hits' },
  { name: 'golems', plan: 'golem-controls,golem-lifecycle,lumber-replant' },
  { name: 'xp', plan: 'xp-integrity' },
  { name: 'contracts', plan: 'adaptation-smoke' },
  { name: 'appearance', plan: 'appearance', platforms: ['paper', 'folia'] },
  { name: 'gloss', plan: 'adaptation-smoke', cases: 'adapt:discovery/insight', platforms: ['paper'] },
  { name: 'folia', plan: 'blink-folia,golem-folia,temporal-overlap-folia,scaffolder-retirement-folia', platforms: ['folia'] },
]
const matrix = JSON.parse(readFileSync(new URL('./adaptation-matrix.json', import.meta.url), 'utf8')).adaptations
const timings = new Map(readFileSync(new URL('./suite-timings.tsv', import.meta.url), 'utf8').trim().split('\n').slice(1)
  .map(line => line.split('\t')).map(([kind, name, seconds]) => [`${kind}:${name}`, Number(seconds)]))
const seconds = key => timings.get(key) ?? timings.get('overhead:unit')

function platformGroups(platform) {
  return groups.filter(group => !group.platforms || group.platforms.includes(platform))
}

function usage() {
  const lines = [
    'Usage: bash bukkit/src/test/gameplay/platform-suite.sh <paper|folia|fabric|forge|neoforge> <minecraft version> [--shards N] [--only item,item,...]',
    'Runs every platform-neutral gameplay scenario group through platform-run.sh (one burner instance per group, as before)',
    'and prints one pass/fail line per scenario step, per-shard exit codes and the outcome of every adaptation contract.',
    'Exit status is non-zero when any step, contract or instance fails, or when a contract is missing or reported twice.',
    '--shards N (default 1): spread the groups over N workers that run concurrently, each one instance at a time. The contracts',
    '  group is split by skill (a skill\'s contracts stay together, in catalog order) and balanced by suite-timings.tsv.',
    '  Every slice runs its own level assignment and activation contracts; slices treat Iris/Gloss exactly like the full run.',
    '--only (or a third positional argument): group names and/or adaptation names. Adaptation names run as targeted contracts (Iris installed when',
    '  axe-iris-feller is named, Gloss on Paper when discovery-insight is named); "contracts" runs the full contract set.',
    'Groups (scenario plans, see platform-run.sh --help):',
    ...groups.map(group => `  ${group.name}${group.platforms ? ` (${group.platforms.join(' and ')} only)` : ''}: ${group.plan}${group.cases ? ` (${group.cases})` : ''}`),
    'Not included: all-adaptations (needs the native client report), iris-preference-latch (Iris), advancement-sql*',
    '(MySQL), sixth-sense-generated (native generated structures, Paper only),',
    'void-skin-absorption-baseline (reproduces a fixed bug on the old runtime).',
    'Same environment overrides as platform-run.sh; set ADAPT_QA_PREFIX to name instances. Unit logs and the plan are kept in',
    'build/gameplay/reports/suite-<platform>-<mc>-<epoch>-<pid>/.',
  ]
  console.log(lines.join('\n'))
}

function plan(platform, shardCount, only) {
  const available = platformGroups(platform)
  const names = new Set(matrix.map(entry => entry.name))
  const requested = only ? only.split(',').filter(Boolean) : available.map(group => group.name)
  const unknown = requested.filter(item => !available.some(group => group.name === item) && !names.has(item))
  if (unknown.length) throw new Error(`Unknown group or adaptation for ${platform}: ${unknown.join(',')}`)
  const selectedGroups = available.filter(group => requested.includes(group.name))
  const fullContracts = selectedGroups.some(group => group.name === 'contracts')
  const targeted = matrix.filter(entry => requested.includes(entry.name)).map(entry => entry.name)
  if (fullContracts && targeted.length) throw new Error('Name either the contracts group or single adaptations, not both')
  const contractNames = fullContracts ? matrix.map(entry => entry.name) : targeted
  const shards = Array.from({ length: shardCount }, () => ({ load: 0, groups: [], skills: [] }))
  const lightest = () => shards.reduce((best, shard) => shard.load < best.load ? shard : best)
  const items = selectedGroups.filter(group => group.name !== 'contracts')
    .map(group => ({ group, cost: seconds(`group:${group.name}`) + seconds('overhead:unit') }))
  const skills = [...new Set(matrix.filter(entry => contractNames.includes(entry.name)).map(entry => entry.skill))]
  items.push(...skills.map(skill => ({ skill, cost: seconds(`skill:${skill}`) * matrix.filter(entry => entry.skill === skill
    && contractNames.includes(entry.name)).length / matrix.filter(entry => entry.skill === skill).length })))
  items.sort((left, right) => right.cost - left.cost || (left.group?.name ?? left.skill).localeCompare(right.group?.name ?? right.skill))
  for (const item of items) {
    const shard = lightest()
    if (item.group) shard.groups.push(item.group)
    else {
      if (!shard.skills.length) shard.load += seconds('overhead:unit')
      shard.skills.push(item.skill)
    }
    shard.load += item.cost
  }
  const units = []
  shards.forEach((shard, index) => {
    for (const group of available) {
      if (shard.groups.includes(group)) units.push({ shard: index + 1, group: group.name, plan: group.plan, cases: group.cases ?? '', slice: 0, label: group.cases ?? '' })
      if (group.name === 'contracts' && shard.skills.length) {
        const cases = matrix.filter(entry => shard.skills.includes(entry.skill) && contractNames.includes(entry.name)).map(entry => entry.name)
        const whole = fullContracts && shardCount === 1
        units.push({ shard: index + 1, group: 'contracts', plan: group.plan, cases: whole ? '' : cases.join(','), slice: fullContracts && !whole ? 1 : 0,
          label: whole ? '' : `${cases.length} contracts: ${shard.skills.join(',')}` })
      }
    }
  })
  const planned = units.filter(unit => unit.group === 'contracts').flatMap(unit => unit.cases ? unit.cases.split(',') : matrix.map(entry => entry.name))
  const duplicated = planned.filter((name, index) => planned.indexOf(name) !== index)
  const missing = contractNames.filter(name => !planned.includes(name))
  if (duplicated.length || missing.length || planned.length !== contractNames.length) {
    throw new Error(`Contract plan is not an exact partition: missing ${missing.join(',') || 'none'}, duplicated ${duplicated.join(',') || 'none'}`)
  }
  return { units, contracts: contractNames, estimates: shards.map(shard => Math.round(shard.load)) }
}

function unitResult(directory, index) {
  const log = readFileSync(join(directory, `${index}.log`), 'utf8')
  const exit = Number(readFileSync(join(directory, `${index}.exit`), 'utf8').trim())
  const steps = [...log.matchAll(/^STEP (\S+) (pass|fail)$/gm)].map(match => ({ name: match[1], result: match[2] }))
  const notes = log.split('\n').filter(line => /^(RETRY|CASES|\[FAIL\]|\[N\/A\]) /.test(line))
  const evidence = [...log.matchAll(/^Gameplay evidence: (.+)$/gm)].at(-1)?.[1]
  const cases = []
  const stderr = evidence && join(evidence, 'adaptation-smoke.stderr.log')
  if (stderr && existsSync(stderr)) {
    for (const match of readFileSync(stderr, 'utf8').matchAll(/^\[(PASS|FAIL|N\/A)\] ([a-z0-9-]+)/gm)) {
      cases.push({ name: match[2], result: { PASS: 'pass', FAIL: 'fail', 'N/A': 'n/a' }[match[1]] })
    }
  }
  return { exit, steps, notes, evidence, cases, tail: log.trim().split('\n').slice(-5) }
}

function readPlan(directory) {
  return readFileSync(join(directory, 'plan.tsv'), 'utf8').trim().split('\n').map(line => {
    const [shard, group, plan, cases, slice, label] = line.split('\t')
    return { shard: Number(shard), group, plan, cases: cases === '-' ? '' : cases, slice: Number(slice), label: label === '-' ? '' : label }
  })
}

const tag = (unit, sharded) => sharded ? `${unit.group}[s${unit.shard}]` : unit.group

function block(directory, index) {
  const units = readPlan(directory)
  const unit = units[index]
  const sharded = units.some(other => other.shard > 1)
  const result = unitResult(directory, index)
  const lines = [`== ${tag(unit, sharded)}: ${unit.plan}${unit.label ? ` (${unit.label})` : ''}`]
  for (const step of result.steps) lines.push(`${tag(unit, sharded).padEnd(10)} ${step.name.padEnd(34)} ${step.result}`)
  for (const note of result.notes) lines.push(`   ${note}`)
  if (result.exit !== 0 && !result.steps.length) lines.push(`${tag(unit, sharded).padEnd(10)} ${'(instance)'.padEnd(34)} fail`, ...result.tail)
  if (result.evidence) lines.push(`   evidence: ${result.evidence}`)
  process.stdout.write(lines.join('\n') + '\n')
}

function summary(directory, platform, minecraftVersion) {
  const units = readPlan(directory)
  const expected = JSON.parse(readFileSync(join(directory, 'contracts.json'), 'utf8'))
  const shardCount = new Set(units.map(unit => unit.shard)).size
  const sharded = units.some(unit => unit.shard > 1)
  const results = units.map((unit, index) => ({ unit, ...unitResult(directory, index) }))
  const lines = []
  let failed = false
  const stepLines = []
  for (const group of platformGroups(platform)) {
    for (const { unit, steps, exit } of results.filter(entry => entry.unit.group === group.name)) {
      for (const step of steps) stepLines.push({ line: `${tag(unit, sharded).padEnd(10)} ${step.name.padEnd(34)} ${step.result}`, result: step.result })
      if (exit !== 0 && !steps.length) stepLines.push({ line: `${tag(unit, sharded).padEnd(10)} ${'(instance)'.padEnd(34)} fail`, result: 'fail' })
    }
  }
  const passedSteps = stepLines.filter(entry => entry.result === 'pass').length
  const failedSteps = stepLines.filter(entry => entry.result === 'fail')
  if (failedSteps.length || results.some(entry => entry.exit !== 0)) failed = true
  lines.push(`Summary ${platform} ${minecraftVersion}${sharded ? ` (${shardCount} shards)` : ''}: ${passedSteps} passed, ${failedSteps.length} failed`)
  lines.push(...failedSteps.map(entry => entry.line))
  lines.push('Steps:', ...stepLines.map(entry => `  ${entry.line}`))
  for (const shard of [...new Set(units.map(unit => unit.shard))]) {
    const exitFile = join(directory, `shard-${shard}.exit`)
    const exit = existsSync(exitFile) ? readFileSync(exitFile, 'utf8').trim() : 'missing'
    if (exit !== '0') failed = true
    lines.push(`Shard s${shard} exit ${exit}: ${results.filter(entry => entry.unit.shard === shard).map(entry => entry.unit.group).join(', ')}`)
  }
  const reported = results.filter(entry => entry.unit.group === 'contracts').flatMap(entry => entry.cases.map(item => ({ ...item, shard: entry.unit.shard })))
  if (expected.length) {
    const counts = new Map()
    for (const item of reported) counts.set(item.name, (counts.get(item.name) ?? 0) + 1)
    const missing = expected.filter(name => !counts.has(name))
    const duplicated = [...counts].filter(([, count]) => count > 1).map(([name]) => name)
    const unexpected = [...counts.keys()].filter(name => !expected.includes(name))
    const tally = result => reported.filter(item => item.result === result)
    if (missing.length || duplicated.length || unexpected.length || tally('fail').length) failed = true
    lines.push(`Contracts: ${tally('pass').length} passed, ${tally('fail').length} failed, not applicable on ${platform}: ${tally('n/a').map(item => item.name).join(',') || 'none'}; `
      + `${expected.length} expected, missing ${missing.join(',') || 'none'}, duplicated ${duplicated.join(',') || 'none'}, unexpected ${unexpected.join(',') || 'none'}`)
    lines.push(...tally('fail').map(item => `  contract ${item.name} fail`))
    for (const name of expected) {
      const item = reported.find(entry => entry.name === name)
      lines.push(`  contract ${name.padEnd(34)} ${item?.result ?? 'missing'}${sharded && item ? ` s${item.shard}` : ''}`)
    }
  }
  for (const { unit, cases } of results.filter(entry => entry.unit.group !== 'contracts' && entry.cases.length)) {
    lines.push(`Group ${unit.group} contracts: ${cases.map(item => `${item.name} ${item.result}`).join(', ')}`)
  }
  for (const { notes } of results) lines.push(...notes.filter(note => note.startsWith('RETRY ')).map(note => `Retried: ${note}`))
  process.stdout.write(lines.join('\n') + '\n')
  return failed ? 1 : 0
}

const [command, ...args] = process.argv.slice(2)
try {
  if (command === 'usage') usage()
  else if (command === 'plan') {
    const [directory, platform, shardCount, only] = args
    const result = plan(platform, Number(shardCount), only || undefined)
    writeFileSync(join(directory, 'plan.tsv'), result.units.map(unit => [unit.shard, unit.group, unit.plan, unit.cases || '-', unit.slice, unit.label || '-'].join('\t')).join('\n') + '\n')
    writeFileSync(join(directory, 'contracts.json'), JSON.stringify(result.contracts))
    for (let shard = 1; shard <= Number(shardCount); shard++) {
      const assigned = result.units.filter(unit => unit.shard === shard)
      if (assigned.length) console.log(`Plan s${shard} (~${Math.round(result.estimates[shard - 1] / 60)} min): ${assigned.map(unit => unit.group === 'contracts' && unit.label ? `contracts(${unit.label})` : unit.group).join(', ')}`)
    }
  } else if (command === 'block') block(args[0], Number(args[1]))
  else if (command === 'summary') process.exitCode = summary(args[0], args[1], args[2])
  else throw new Error(`Unknown command: ${command}`)
} catch (error) {
  console.error(error.message)
  process.exitCode = 2
}
