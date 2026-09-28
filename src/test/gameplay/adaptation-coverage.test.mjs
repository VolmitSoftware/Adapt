import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile, readdir } from 'node:fs/promises'
import { EventEmitter } from 'node:events'
import { setTimeout as delay } from 'node:timers/promises'
import { assertAdaptationEvidence } from './adaptation-evidence.mjs'
import { fixtureJson } from './fixture-json.mjs'
import { behaviorCases } from './adaptation-actions.mjs'
import { passiveBehaviorCases } from './adaptation-passive-actions.mjs'
import { combatBehaviorCases } from './adaptation-combat-actions.mjs'
import { brewingBehaviorCases } from './adaptation-brewing-actions.mjs'
import { blockBehaviorCases } from './adaptation-block-actions.mjs'
import { itemBehaviorCases } from './adaptation-item-actions.mjs'
import { movementBehaviorCases } from './adaptation-movement-actions.mjs'
import { rangedBehaviorCases } from './adaptation-ranged-actions.mjs'
import { architectBehaviorCases } from './adaptation-architect-actions.mjs'
import { tamingBehaviorCases } from './adaptation-taming-actions.mjs'
import { chronosBehaviorCases } from './adaptation-chronos-actions.mjs'
import { netherBehaviorCases } from './adaptation-nether-actions.mjs'
import { defenseBehaviorCases } from './adaptation-defense-actions.mjs'
import { harvestBehaviorCases } from './adaptation-harvest-actions.mjs'
import { tragoulBehaviorCases } from './adaptation-tragoul-actions.mjs'
import { knowledgeBehaviorCases } from './adaptation-knowledge-actions.mjs'
import { riftBehaviorCases } from './adaptation-rift-actions.mjs'
import { traversalBehaviorCases } from './adaptation-traversal-actions.mjs'
import { earthBehaviorCases } from './adaptation-earth-actions.mjs'
import { projectileBehaviorCases } from './adaptation-projectile-actions.mjs'
import { guardBehaviorCases } from './adaptation-guard-actions.mjs'
import { discoveryBehaviorCases } from './adaptation-discovery-actions.mjs'
import { kineticsBehaviorCases } from './adaptation-kinetics-actions.mjs'
import { utilityBehaviorCases } from './adaptation-utility-actions.mjs'
import { natureBehaviorCases } from './adaptation-nature-actions.mjs'
import { integrationBehaviorCases } from './adaptation-integration-actions.mjs'
import { craftingBehaviorCases } from './adaptation-crafting-actions.mjs'
import { decodeSoundPacket } from './feedback.mjs'

const root = new URL('../../../', import.meta.url)
const matrix = JSON.parse(await readFile(new URL('./adaptation-matrix.json', import.meta.url), 'utf8'))
const sourceRoot = 'src/main/java/art/arcane/adapt/content/'

async function javaFiles(directory) {
  const entries = await readdir(new URL(directory, root), { withFileTypes: true })
  const files = await Promise.all(entries.map(entry => entry.isDirectory()
    ? javaFiles(`${directory}${entry.name}/`)
    : entry.name.endsWith('.java') ? [`${directory}${entry.name}`] : []))
  return files.flat()
}

function constants(source, expression) {
  return [...new Set([...source.matchAll(expression)].map(match => match[1]))].sort()
}

test('activation cases name actual catalog adaptations and never override another case group', () => {
  const known = new Set(matrix.adaptations.map(entry => entry.name))
  const owners = new Map()
  for (const [group, cases] of [['basic', behaviorCases], ['passive', passiveBehaviorCases], ['combat', combatBehaviorCases], ['brewing', brewingBehaviorCases], ['blocks', blockBehaviorCases], ['items', itemBehaviorCases], ['movement', movementBehaviorCases], ['ranged', rangedBehaviorCases], ['architect', architectBehaviorCases], ['taming', tamingBehaviorCases], ['chronos', chronosBehaviorCases], ['nether', netherBehaviorCases], ['defense', defenseBehaviorCases], ['harvest', harvestBehaviorCases], ['tragoul', tragoulBehaviorCases], ['knowledge', knowledgeBehaviorCases], ['rift', riftBehaviorCases], ['crafting', craftingBehaviorCases], ['earth', earthBehaviorCases], ['projectile', projectileBehaviorCases], ['guard', guardBehaviorCases], ['discovery', discoveryBehaviorCases], ['kinetics', kineticsBehaviorCases], ['utility', utilityBehaviorCases], ['nature', natureBehaviorCases], ['integration', integrationBehaviorCases], ['traversal', traversalBehaviorCases]]) {
    assert.ok(cases instanceof Map, `${group} cases must be keyed by adaptation ID`)
    assert.ok(cases.size > 0, `${group} cases must not be empty`)
    for (const [name, entry] of cases) {
      assert.ok(known.has(name), `${group} references unknown adaptation ${name}`)
      assert.ok(!owners.has(name), `${name} occurs in both ${owners.get(name)} and ${group}`)
      assert.equal(typeof entry.trigger, 'function', `${name} needs an actual player trigger`)
      assert.ok(entry.verify || entry.attribute || entry.effect, `${name} needs an observable behavior assertion`)
      const specification = matrix.adaptations.find(candidate => candidate.name === name)
      if (specification.sounds.length || specification.feedbackPresets.length) {
        assert.equal(typeof entry.sound, 'string', `${name} needs a declared sound assertion`)
        assert.equal(typeof entry.soundVolume, 'number', `${name} needs an exact sound volume`)
        assert.ok(Number.isFinite(entry.soundPitch) || entry.soundPitchRange?.length === 2, `${name} needs a bounded sound pitch`)
      }
      if (specification.particles.length || specification.feedbackPresets.length) {
        assert.equal(typeof entry.particle, 'string', `${name} needs a declared particle assertion`)
      }
      owners.set(name, group)
    }
  }
  assert.deepEqual([...owners.keys()].sort(), [...known].sort(), 'Every catalog adaptation must have a real activation case')
})

test('brewing cases cover every brewing adaptation and require real potion output', () => {
  assert.deepEqual([...brewingBehaviorCases.keys()].sort(), matrix.adaptations.filter(entry => entry.skill === 'brewing').map(entry => entry.name).sort())
  const context = { expect(condition, message) { assert.ok(condition, message) } }
  const base = { name: 'potion', count: 1, potionId: 16, effects: [] }
  const unlearned = { before: base, output: base, received: base, ingredientRemaining: 1, elapsedMillis: 18000 }
  const result = { name: 'potion', count: 1, effects: [{ name: 'Haste', duration: 1200, amplifier: 0 }] }
  const active = { before: base, output: result, received: result, ingredientRemaining: 0, elapsedMillis: 16000 }
  const verify = brewingBehaviorCases.get('brewing-haste').verify
  assert.ok(verify({ context, unlearned, active }).assertions.length > 0)
  assert.throws(() => verify({ context, unlearned, active: unlearned }), /creates one potion/)
  assert.throws(() => verify({ context, unlearned, active: { ...active, received: base } }), /declared effect/)
  assert.throws(() => verify({ context, unlearned, active: { ...active, received: { ...result, effects: [{ name: 'Haste', duration: 1, amplifier: 0 }] } } }), /declared effect/)
  assert.throws(() => verify({ context, unlearned, active: { ...active, ingredientRemaining: 1 } }), /consumes one ingredient/)
})

test('brewing fixture prepares base ingredients without creating results or simulating brew events', async () => {
  const fixture = await readFile(new URL('src/test/gameplay/fixture/java/art/arcane/adapt/gameplay/BrewingFixtures.java', root), 'utf8')
  assert.doesNotMatch(fixture, /\.(?:addCustomEffect|addPotionEffect|callEvent|setBrewingTime|setFuelLevel)\s*\(/)
  assert.match(fixture, /setBasePotionType\(inputs\.base\(\)\)/)
})

test('adaptation matrix covers every concrete adaptation and its actual skill registration', async () => {
  assert.equal(matrix.schemaVersion, 1)
  const concrete = new Map()
  for (const file of await javaFiles(`${sourceRoot}adaptation/`)) {
    const code = await readFile(new URL(file, root), 'utf8')
    const name = code.match(/public\s+(?:final\s+)?class\s+(\w+)\s+extends\s+SimpleAdaptation</)?.[1]
    if (name) concrete.set(name, file)
  }
  const registered = new Map()
  for (const file of await javaFiles(`${sourceRoot}skill/`)) {
    const code = await readFile(new URL(file, root), 'utf8')
    const skill = code.match(/super\("([^"]+)"/)?.[1]
    if (!skill) continue
    const locals = new Map([...code.matchAll(/\b(\w+)\s+(\w+)\s*=\s*new\s+\1\s*\(/g)].map(match => [match[2], match[1]]))
    for (const match of code.matchAll(/registerAdaptation\(([^;]+)\);/g)) {
      const name = match[1].match(/^new (\w+)/)?.[1] ?? locals.get(match[1])
      assert.ok(name, `Unresolved registration in ${file}: ${match[1]}`)
      assert.ok(!registered.has(name), `Duplicate registration: ${name}`)
      registered.set(name, skill)
    }
  }
  assert.equal(matrix.adaptations.length, concrete.size)
  assert.equal(new Set(matrix.adaptations.map(entry => entry.name)).size, concrete.size)
  assert.deepEqual(matrix.adaptations.map(entry => entry.class).sort(), [...concrete.keys()].sort())
  assert.deepEqual([...registered.keys()].sort(), [...concrete.keys()].sort())
  for (const entry of matrix.adaptations) {
    assert.equal(entry.skill, registered.get(entry.class), entry.name)
    assert.equal(entry.source, concrete.get(entry.class), entry.name)
  }
})

test('matrix trigger, feedback, and behavioral expectations match their source declarations', async () => {
  for (const entry of matrix.adaptations) {
    const code = await readFile(new URL(entry.source, root), 'utf8')
    assert.equal(entry.name, code.match(/\bsuper\("([^"]+)"\)/)?.[1], entry.class)
    const triggers = constants(code, /@EventHandler(?:\([^)]*\))?\s+(?:public|protected|private)\s+void\s+\w+\(\s*(\w+Event)\s+\w+\s*\)/g)
    if (/\bvoid\s+onTick\s*\(/.test(code)) triggers.push('tick')
    if (/\bregisterRecipe\s*\(/.test(code)) triggers.push('recipe')
    assert.deepEqual(entry.triggers, triggers.sort(), `${entry.name} triggers`)
    assert.deepEqual(entry.sounds, constants(code, /\bSound\.([A-Z][A-Z_0-9]+)\b/g), `${entry.name} sounds`)
    assert.deepEqual(entry.particles, constants(code, /\bParticles?\.([A-Z][A-Z_0-9]+)\b/g), `${entry.name} particles`)
    assert.deepEqual(entry.feedbackPresets, constants(code, /\bFxPresets\.(\w+)\s*\(/g), `${entry.name} presets`)
    const description = await readFile(new URL(entry.expectationSource, root), 'utf8')
    const literal = description.match(/@ConfigDescription\("((?:[^"\\]|\\.)*)"\)/)?.[1]
    assert.ok(literal, `${entry.name} behavior description`)
    assert.equal(entry.expectation, JSON.parse(`"${literal}"`), entry.name)
  }
})

test('Iris tree feller is explicitly conditional and remains required for full catalog evidence', async () => {
  const conditional = matrix.adaptations.filter(entry => entry.availability.kind !== 'always')
  assert.equal(conditional.length, 1)
  assert.equal(conditional[0].class, 'AxeIrisFeller')
  assert.deepEqual(conditional[0].availability, { kind: 'plugin', plugin: 'Iris' })
  const axes = await readFile(new URL(`${sourceRoot}skill/SkillAxes.java`, root), 'utf8')
  assert.match(axes, /if\s*\(irisTreeFellerAvailable\)\s*\{\s*registerAdaptation\(new AxeIrisFeller\(\)\)/)
  assert.throws(() => assertAdaptationEvidence(conditional, []), new RegExp(conditional[0].name))
})

const sample = [{ name: 'sample', sounds: ['BLOCK_AMETHYST_BLOCK_CHIME'], particles: ['CLOUD'], feedbackPresets: [] }]
const positive = () => ({ name: 'sample', behavior: { status: 'passed', assertions: ['Target gained the expected effect after activation.'] }, sound: { status: 'passed', assertions: ['Expected sound packet arrived in the activation window.'] }, particles: { status: 'passed', assertions: ['Expected particle packet arrived in the activation window.'] } })

test('completeness gate rejects absent, duplicate, unknown, and catalog-only evidence', () => {
  assert.throws(() => assertAdaptationEvidence(sample, []), /sample.*missing behavior/)
  assert.throws(() => assertAdaptationEvidence(sample, [positive(), positive()]), /sample.*duplicate/)
  assert.throws(() => assertAdaptationEvidence(sample, [{ ...positive(), name: 'unknown' }]), /Unknown adaptation: unknown/)
  assert.throws(() => assertAdaptationEvidence(sample, [{ name: 'sample', configured: true, learned: true }]), /sample.*behavior/)
  assert.throws(() => assertAdaptationEvidence(sample, [{ ...positive(), behavior: { status: 'passed', assertions: [] } }]), /sample.*behavior/)
})

test('completeness gate requires declared sound and particle evidence without asserting real-client output', () => {
  for (const aspect of ['behavior', 'sound', 'particles']) {
    assert.throws(() => assertAdaptationEvidence(sample, [{ ...positive(), [aspect]: { status: 'not-applicable' } }]), new RegExp(`sample.*${aspect}`))
    assert.throws(() => assertAdaptationEvidence(sample, [{ ...positive(), [aspect]: { status: 'passed', assertions: [' '] } }]), new RegExp(`sample.*${aspect}`))
  }
  const result = assertAdaptationEvidence(sample, [positive()])
  assert.equal(result.behaviorPassed, 1)
  assert.equal(result.soundPacketsPassed, 1)
  assert.equal(result.particlePacketsPassed, 1)
  assert.equal(result.realClient.status, 'not-verified')
  const silent = [{ ...sample[0], sounds: [], particles: [] }]
  assert.equal(assertAdaptationEvidence(silent, [{ ...positive(), sound: { status: 'not-applicable' }, particles: { status: 'not-applicable' } }]).behaviorPassed, 1)
  assert.throws(() => assertAdaptationEvidence([{ ...silent[0], feedbackPresets: ['successShimmer'] }], [{ ...positive(), sound: { status: 'not-applicable' } }]), /sample.*sound/)
})

function fixtureContext(messages) {
  const bot = new EventEmitter()
  const commands = []
  bot.chat = command => {
    commands.push(command)
    queueMicrotask(() => {
      for (const message of messages) bot.emit('messagestr', message)
    })
  }
  return {
    bot,
    commands,
    async waitUntil(predicate, { timeoutMs }) {
      const deadline = Date.now() + timeoutMs
      do {
        if (await predicate()) return
        await delay(1)
      } while (Date.now() < deadline)
      throw new Error('Fixture response timed out')
    },
  }
}

test('fixture transport reassembles a large response in chunk order and ignores other responses', async () => {
  const expected = { adaptations: matrix.adaptations.map(entry => ({ name: entry.name, skill: entry.skill })) }
  const encoded = JSON.stringify(expected)
  assert.ok(encoded.length > 4096)
  const chunks = encoded.match(/[\s\S]{1,1800}/g)
  const messages = chunks.map((chunk, index) => `ADAPT_QA CATALOG c1 ${index} ${chunks.length} ${chunk}`).reverse()
  messages.splice(1, 0, 'An unrelated player message', 'ADAPT_QA CATALOG c2 0 1 {}')
  assert.ok(messages.every(message => message.length < 4096))
  const context = fixtureContext(messages)
  assert.deepEqual(await fixtureJson(context, '/adaptqa catalog c1', 'CATALOG c1'), expected)
  assert.deepEqual(context.commands, ['/adaptqa catalog c1'])
  assert.equal(context.bot.listenerCount('messagestr'), 0)
})

test('fixture transport rejects duplicate, inconsistent, malformed, and invalid JSON responses', async () => {
  const cases = [
    ['duplicate', ['0 2 {', '0 2 {', '1 2 }'], /Inconsistent/],
    ['count mismatch', ['0 2 {', '1 3 }'], /Inconsistent/],
    ['out of range', ['2 2 {}'], /Inconsistent/],
    ['zero count', ['0 0 {}'], /Inconsistent/],
    ['excessive count', ['0 129 {}'], /Inconsistent/],
    ['malformed header', ['not-a-chunk'], /Invalid fixture/],
    ['negative index', ['-1 1 {}'], /Invalid fixture/],
    ['invalid JSON', ['0 1 {invalid}'], SyntaxError],
  ]
  for (const [label, payloads, error] of cases) {
    const context = fixtureContext(payloads.map(payload => `ADAPT_QA SNAPSHOT s1 ${payload}`))
    await assert.rejects(fixtureJson(context, '/adaptqa snapshot player s1', 'SNAPSHOT s1'), error, label)
    assert.equal(context.bot.listenerCount('messagestr'), 0, `${label} listener cleanup`)
  }
})

test('fixture timeout removes only its listener and permits a later request', async () => {
  const context = fixtureContext(['ADAPT_QA CATALOG c1 0 2 {'])
  const observed = []
  const existing = message => observed.push(message)
  context.bot.on('messagestr', existing)
  await assert.rejects(fixtureJson(context, '/adaptqa catalog c1', 'CATALOG c1', 15), /timed out/)
  assert.deepEqual(context.bot.listeners('messagestr'), [existing])
  context.bot.chat = command => {
    context.commands.push(command)
    queueMicrotask(() => {
      context.bot.emit('messagestr', 'ADAPT_QA CATALOG c1 1 2 }')
      context.bot.emit('messagestr', 'ADAPT_QA CATALOG c2 0 1 {"ready":true}')
    })
  }
  assert.deepEqual(await fixtureJson(context, '/adaptqa catalog c2', 'CATALOG c2'), { ready: true })
  assert.deepEqual(context.bot.listeners('messagestr'), [existing])
  assert.equal(observed.length, 3)
  context.bot.off('messagestr', existing)
})

test('sound holders resolve decoded zero-based indices instead of one-based data IDs', () => {
  const first = { id: 1, name: 'ambient.cave' }
  const second = { id: 2, name: 'item.armor.equip_leather' }
  const registry = { soundsArray: [first, second], sounds: { 1: first, 2: second } }
  assert.deepEqual(decodeSoundPacket(registry, { sound: { soundId: 0 }, volume: 0.3, pitch: 0.35 }), {
    name: 'ambient.cave', registryIndex: 0, volume: 0.3, pitch: 0.35,
  })
  assert.equal(decodeSoundPacket(registry, { sound: { soundId: 1 }, volume: 1, pitch: 1 }).name, 'item.armor.equip_leather')
  assert.equal(decodeSoundPacket(registry, { sound: { soundId: 2 }, volume: 1, pitch: 1 }).name, undefined)
})

test('inline sound names take precedence and packet float values are preserved', () => {
  const registry = { soundsArray: [{ id: 1, name: 'ambient.cave' }] }
  const volume = Math.fround(0.35)
  const pitch = Math.fround(1.15)
  assert.deepEqual(decodeSoundPacket(registry, {
    sound: { soundId: 0, data: { soundName: 'minecraft:block.conduit.activate' } }, volume, pitch,
  }), { name: 'block.conduit.activate', registryIndex: 0, volume, pitch })
  assert.equal(decodeSoundPacket(registry, {
    sound: { data: { soundName: 'custom:ability.activate' } }, volume, pitch,
  }).name, 'custom:ability.activate')
})
