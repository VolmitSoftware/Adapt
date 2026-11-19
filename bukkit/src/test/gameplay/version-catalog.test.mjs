import test from 'node:test'
import assert from 'node:assert/strict'
import { mkdtempSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { catalogForVersion, contractPlan, coreCatalogPath, loadVersionCatalog, parseVersionCatalog, stepBlocker, versionEntry, versionStep } from './version-catalog.mjs'
import { contentSince } from './version-ids.mjs'

const json = JSON.stringify({
  '1.20.1': [
    { name: 'crafting-backpacks', status: 'disabled', reason: 'bundles are not in 1.20.1' },
    { name: 'agility-armor-up', status: 'emulated', reason: 'armor attribute through a timed modifier' },
  ],
  '1.21.1': [{ name: 'crafting-backpacks', status: 'emulated', reason: 'experimental bundle' }],
})
const tsv = [
  'minecraft\tadaptation\tstatus\treason',
  '# generated',
  '1.20.1\tcrafting-backpacks\tdisabled\tbundles are not in 1.20.1',
  '1.21.1\tcrafting-backpacks\temulated\texperimental bundle',
].join('\n')

test('JSON and TSV catalogs parse to the same per-version entries', () => {
  const fromJson = catalogForVersion(parseVersionCatalog(json, 'json'), '1.20.1')
  const fromTsv = catalogForVersion(parseVersionCatalog(tsv, 'tsv'), '1.20.1')
  assert.deepEqual(fromTsv.get('crafting-backpacks'), fromJson.get('crafting-backpacks'))
  assert.equal(fromJson.get('crafting-backpacks').status, 'disabled')
  assert.equal(fromJson.size, 2)
  assert.equal(catalogForVersion(parseVersionCatalog(json, 'json'), '26.3').size, 0)
})

test('malformed catalogs are rejected instead of silently skipping contracts', () => {
  assert.throws(() => parseVersionCatalog(JSON.stringify({ '1.20.1': [{ name: 'x-y', status: 'off', reason: 'r' }] }), 'json'), /status must be/)
  assert.throws(() => parseVersionCatalog(JSON.stringify({ '1.20.1': [{ name: 'x-y', status: 'disabled', reason: ' ' }] }), 'json'), /needs a reason/)
  assert.throws(() => parseVersionCatalog(JSON.stringify({ latest: [] }), 'json'), /invalid Minecraft version/)
  assert.throws(() => parseVersionCatalog(JSON.stringify([]), 'json'), /keyed by Minecraft version/)
  assert.throws(() => parseVersionCatalog('name\tstatus\n', 'tsv'), /header/)
  assert.throws(() => parseVersionCatalog(`${tsv}\n1.20.1\tcrafting-backpacks\tdisabled\tagain`, 'tsv'), /Duplicate/)
})

test('a disabled adaptation is N/A with its reason whether the server hides it or lists it disabled', () => {
  const entry = { status: 'disabled', reason: 'bundles are not in 1.20.1' }
  assert.deepEqual(contractPlan('crafting-backpacks', undefined, entry, '1.20.1'), { run: false, note: 'disabled on Minecraft 1.20.1: bundles are not in 1.20.1' })
  assert.equal(contractPlan('crafting-backpacks', { enabled: false }, entry, '1.20.1').run, false)
})

test('a server that enables an adaptation the catalog disables is reported as drift', () => {
  assert.throws(() => contractPlan('crafting-backpacks', { enabled: true }, { status: 'disabled', reason: 'r' }, '1.20.1'), /disabled on 1.20.1 by the version catalog/)
})

test('partial adaptations run their contracts and name the missing part in the note', () => {
  assert.deepEqual(contractPlan('rift-gate', { enabled: true }, { status: 'partial', reason: 'engine feature item cooldown groups (no cooldown sweep on the item)' }, '1.21.1'),
    { run: true, note: 'partial on Minecraft 1.21.1: engine feature item cooldown groups (no cooldown sweep on the item)' })
  assert.equal(loadVersionCatalog('1.21.1').get('rift-gate').status, 'partial')
})

test('emulated adaptations still run their contracts and carry a note; unlisted ones run unchanged', () => {
  assert.deepEqual(contractPlan('agility-armor-up', { enabled: true }, { status: 'emulated', reason: 'timed modifier' }, '1.20.1'), { run: true, note: 'emulated on Minecraft 1.20.1: timed modifier' })
  assert.deepEqual(contractPlan('agility-armor-up', { enabled: true }, undefined, '26.3'), { run: true })
  assert.deepEqual(contractPlan('axe-iris-feller', undefined, undefined, '26.3'), { run: false })
})

test('the catalog file is read by extension and an unset path means no version exclusions', () => {
  const directory = mkdtempSync(join(tmpdir(), 'adapt-version-catalog-'))
  writeFileSync(join(directory, 'catalog.tsv'), tsv)
  writeFileSync(join(directory, 'catalog.json'), json)
  assert.equal(loadVersionCatalog('1.21.1', join(directory, 'catalog.tsv')).get('crafting-backpacks').status, 'emulated')
  assert.equal(loadVersionCatalog('1.20.1', join(directory, 'catalog.json')).size, 2)
  assert.equal(loadVersionCatalog('1.20.1', '').size, 0)
  assert.throws(() => loadVersionCatalog('1.20.1', join(directory, 'catalog.txt')), /\.json or \.tsv/)
})

const core = [
  'skill\tadaptation\tstatus\treason',
  'crafting\tcrafting-backpacks\tdisabled\tengine feature bundles',
  'crafting\tcrafting-masterwork\tworks\t',
  'stealth\tstealth-speed\temulated\tattribute minecraft:sneaking_speed',
].join('\n')

function withEnv(values, action) {
  const saved = Object.fromEntries(Object.keys(values).map(key => [key, process.env[key]]))
  for (const [key, value] of Object.entries(values)) {
    if (value === undefined) delete process.env[key]
    else process.env[key] = value
  }
  try {
    return action()
  } finally {
    for (const [key, value] of Object.entries(saved)) {
      if (value === undefined) delete process.env[key]
      else process.env[key] = value
    }
  }
}

test('core catalog rows keep disabled and emulated entries under the version in the file name', () => {
  const directory = mkdtempSync(join(tmpdir(), 'adapt-core-catalog-'))
  writeFileSync(join(directory, '1.20.1.tsv'), core)
  const catalog = loadVersionCatalog('1.20.1', join(directory, '1.20.1.tsv'))
  assert.deepEqual([...catalog.keys()], ['crafting-backpacks', 'stealth-speed'])
  assert.equal(catalog.get('stealth-speed').reason, 'attribute minecraft:sneaking_speed')
  assert.equal(loadVersionCatalog('1.21.1', join(directory, '1.20.1.tsv')).size, 0)
  assert.equal(coreCatalogPath('1.20.1', directory), join(directory, '1.20.1.tsv'))
  assert.equal(coreCatalogPath('1.21.1', directory), undefined)
  assert.throws(() => parseVersionCatalog('skill\tname\n', 'core', '1.20.1'), /Core catalog header/)
  assert.throws(() => parseVersionCatalog(`${core}\ncrafting\tcrafting-backpacks\tdisabled\tagain`, 'core', '1.20.1'), /Duplicate/)
})

test('an unset ADAPT_QA_VERSION_CATALOG reads core golden files for old versions and nothing for 26.x', () => {
  withEnv({ ADAPT_QA_VERSION_CATALOG: undefined }, () => {
    assert.equal(loadVersionCatalog('1.20.1').get('crafting-backpacks').status, 'disabled')
    assert.equal(loadVersionCatalog('1.20.1').get('stealth-speed').status, 'partial')
    assert.equal(loadVersionCatalog('1.20.1').get('stealth-silent-step').status, 'emulated')
    assert.equal(loadVersionCatalog('1.21.1').get('golem-copper-frame').status, 'disabled')
    assert.equal(loadVersionCatalog('1.21.1').has('stealth-speed'), false)
    assert.equal(loadVersionCatalog('26.3').size, 0)
  })
  withEnv({ ADAPT_QA_VERSION_CATALOG: '' }, () => assert.equal(loadVersionCatalog('1.20.1').size, 0))
})

test('core golden catalogs name only adaptations the contract matrix knows', () => {
  const names = new Set(JSON.parse(readFileSync(new URL('./adaptation-matrix.json', import.meta.url), 'utf8')).adaptations.map(entry => entry.name))
  for (const version of ['1.21.1', '1.20.1']) {
    const unknown = [...loadVersionCatalog(version, coreCatalogPath(version)).keys()].filter(name => !names.has(name))
    assert.deepEqual(unknown, [], version)
  }
})

test('content added after the server version is named with its release', () => {
  assert.equal(contentSince('block', 'crafter', '1.20.1'), '1.21')
  assert.equal(contentSince('block', 'minecraft:crafter', '1.21.1'), undefined)
  assert.equal(contentSince('item', 'wind_charge', '1.20.1'), '1.21')
  assert.equal(contentSince('item', 'wind_charge', '1.21.1'), undefined)
  assert.equal(contentSince('entity', 'copper_golem', '1.21.1'), '1.21.9')
  assert.equal(contentSince('entity', 'copper_golem', '26.3'), undefined)
  assert.equal(contentSince('entity', 'zombie', '1.20.1'), undefined)
})

test('a step is blocked by a disabled adaptation or missing content, not by an emulated one', () => {
  const catalog = parseVersionCatalog(core, 'core', '1.20.1')
  const forVersion = catalogForVersion(catalog, '1.20.1')
  assert.equal(stepBlocker({ adaptations: ['stealth-speed', 'crafting-masterwork'] }, '1.20.1', forVersion), undefined)
  assert.equal(stepBlocker({ adaptations: ['crafting-backpacks'] }, '1.20.1', forVersion),
    'crafting-backpacks is disabled on Minecraft 1.20.1 (engine feature bundles)')
  assert.equal(stepBlocker({ content: [['block', 'crafter']] }, '1.20.1', forVersion), 'block minecraft:crafter does not exist before Minecraft 1.21')
  assert.equal(stepBlocker({ adaptations: ['crafting-backpacks'], content: [['block', 'crafter']] }, '26.3', new Map()), undefined)
  assert.equal(versionEntry('stealth-speed', '1.20.1', forVersion).status, 'emulated')
})

test('versionStep runs the step where it applies and reports N/A with the reason elsewhere', async () => {
  const run = async version => {
    const context = { report: {}, steps: [], step: async (title, action) => { context.steps.push(title); await action() } }
    const written = []
    const write = process.stderr.write
    process.stderr.write = text => written.push(text)
    let ran = false
    try {
      const result = await withEnv({ ADAPT_QA_MINECRAFT_VERSION: version, ADAPT_QA_VERSION_CATALOG: undefined },
        () => versionStep(context, 'copper-rod', { adaptations: ['golem-copper-frame'], content: [['entity', 'copper_golem']] },
          'Copper keeps its rod', async () => { ran = true }))
      return { result, ran, context, written }
    } finally {
      process.stderr.write = write
    }
  }
  const modern = await run('26.3')
  assert.deepEqual([modern.result, modern.ran, modern.context.steps, modern.written], [true, true, ['Copper keeps its rod'], []])
  const legacy = await run('1.21.1')
  assert.deepEqual([legacy.result, legacy.ran, legacy.context.steps], [false, false, []])
  assert.match(legacy.written.join(''), /^\[N\/A\] copper-rod: golem-copper-frame is disabled on Minecraft 1\.21\.1 \(entity type minecraft:copper_golem\); entity minecraft:copper_golem does not exist before Minecraft 1\.21\.9\n$/)
  assert.equal(legacy.context.report.notApplicable[0].id, 'copper-rod')
})
