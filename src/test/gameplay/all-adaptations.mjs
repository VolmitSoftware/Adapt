import { randomBytes } from 'node:crypto'
import { join } from 'node:path'
import { mkdir, readFile, rename, writeFile } from 'node:fs/promises'
import { assertSkillCoverage } from './skill-matrix.mjs'
import { assertAdaptationEvidence } from './adaptation-evidence.mjs'
import { loadNativeClientEvidence } from './native-client-evidence.mjs'
import { behaviorCases as basicBehaviorCases } from './adaptation-actions.mjs'
import { passiveBehaviorCases } from './adaptation-passive-actions.mjs'
import { combatBehaviorCases } from './adaptation-combat-actions.mjs'
import { blockBehaviorCases } from './adaptation-block-actions.mjs'
import { itemBehaviorCases } from './adaptation-item-actions.mjs'
import { brewingBehaviorCases } from './adaptation-brewing-actions.mjs'
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
import { craftingBehaviorCases } from './adaptation-crafting-actions.mjs'
import { earthBehaviorCases } from './adaptation-earth-actions.mjs'
import { projectileBehaviorCases } from './adaptation-projectile-actions.mjs'
import { guardBehaviorCases } from './adaptation-guard-actions.mjs'
import { discoveryBehaviorCases } from './adaptation-discovery-actions.mjs'
import { kineticsBehaviorCases } from './adaptation-kinetics-actions.mjs'
import { synchronizePlayerVelocity } from './player-velocity.mjs'
import { synchronizePlayerInput } from './player-input.mjs'
import { synchronizePlayerAttributes } from './player-attributes.mjs'
import { synchronizePlayerPassengers } from './player-passengers.mjs'
import { utilityBehaviorCases } from './adaptation-utility-actions.mjs'
import { natureBehaviorCases } from './adaptation-nature-actions.mjs'
import { integrationBehaviorCases } from './adaptation-integration-actions.mjs'
import { fixtureJson } from './fixture-json.mjs'
import { decodeSoundPacket, observeParticlePackets, isFeedbackReady } from './feedback.mjs'
import { errorsSince, logPosition } from './runtime-log.mjs'
import { captureEvidenceManifest, writeEvidenceManifest } from './evidence-manifest.mjs'

const entries = [...basicBehaviorCases, ...passiveBehaviorCases, ...combatBehaviorCases, ...blockBehaviorCases, ...itemBehaviorCases, ...brewingBehaviorCases, ...movementBehaviorCases, ...rangedBehaviorCases, ...architectBehaviorCases, ...tamingBehaviorCases, ...chronosBehaviorCases, ...netherBehaviorCases, ...defenseBehaviorCases, ...harvestBehaviorCases, ...tragoulBehaviorCases, ...knowledgeBehaviorCases, ...riftBehaviorCases, ...craftingBehaviorCases, ...earthBehaviorCases, ...projectileBehaviorCases, ...guardBehaviorCases, ...discoveryBehaviorCases, ...kineticsBehaviorCases, ...utilityBehaviorCases, ...natureBehaviorCases, ...integrationBehaviorCases, ...traversalBehaviorCases]
const behaviorCases = new Map(entries)
if (behaviorCases.size !== entries.length) throw new Error('Duplicate adaptation behavior handlers')

export function matchesParticle(particle, testCase) {
  return particle.name === testCase.particle?.replace(/^minecraft:/, '')
    && (testCase.particleCount === undefined || particle.count === testCase.particleCount)
    && (testCase.particleColor === undefined || particle.dust?.rgb === testCase.particleColor)
    && (testCase.particleY === undefined || Math.abs(particle.position.y - testCase.particleY) < 0.00001)
    && (testCase.particleOffset === undefined || [particle.offset.x, particle.offset.y, particle.offset.z].every(value => Math.abs(value - testCase.particleOffset) < 0.00001))
}

export async function runAdaptationSuite(context, { requireComplete = true, caseNames, nativeClientReport } = {}) {
  if (caseNames && (requireComplete || caseNames.some(name => !behaviorCases.has(name)))) throw new Error('Targeted smoke cases must have registered behavior handlers')
  const matrix = JSON.parse(await readFile(new URL('./adaptation-matrix.json', import.meta.url), 'utf8')).adaptations
  const suffix = randomBytes(4).toString('hex')
  const reportDirectory = new URL(`../../../build/gameplay/reports/cases-${Date.now()}-${suffix}/`, import.meta.url)
  await mkdir(reportDirectory, { recursive: true })
  const manifest = await captureEvidenceManifest(context.report.server.directory, {
    scope: { suite: 'adaptations', requireComplete, cases: caseNames ?? 'all' },
  })
  const manifestReference = await writeEvidenceManifest(new URL('manifest.json', reportDirectory), manifest)
  context.report.evidenceManifest = manifest
  const nativeEvidence = nativeClientReport ? await loadNativeClientEvidence(nativeClientReport,
    manifest.artifacts.find(artifact => artifact.path === 'plugins/Adapt.jar')?.sha256) : undefined
  async function checkpoint(name, record) {
    const destination = new URL(`${name}.json`, reportDirectory)
    const temporary = new URL(`${name}.json.tmp`, reportDirectory)
    await writeFile(temporary, JSON.stringify({ ...record, evidenceManifest: manifestReference }, null, 2))
    await rename(temporary, destination)
  }
  process.stderr.write(`[INFO] Case reports: ${reportDirectory.pathname}\n`)
  const actor = await context.connectActor(`AQAa${suffix}`)
  const opponent = await context.connectActor(`AQAb${suffix}`)
  const stopInputs = [actor, opponent].flatMap(player => [synchronizePlayerInput(player.bot), synchronizePlayerVelocity(player.bot), synchronizePlayerPassengers(player.bot)])
  const evidence = context.report.adapt = {
    coverage: 'live level assignment contracts and explicitly listed activation cases',
    realClient: nativeEvidence ? { adaptations: [nativeEvidence.name], provenance: nativeEvidence.provenance } : 'not verified', completeness: { status: 'incomplete' }, catalog: [], adaptations: [],
    caseReportDirectory: reportDirectory.pathname, missingBehavior: matrix.map(entry => entry.name), unavailable: [], caseFailures: [],
  }
  let sequence = 0
  let setup = false
  const sounds = []
  const particles = []
  const normalize = name => name?.replace(/^minecraft:/, '')
  const matchesSound = (sound, testCase) => testCase.soundAlternatives
    ? testCase.soundAlternatives.some(alternative => matchesSound(sound, alternative))
    : sound.name === normalize(testCase.sound)
    && Math.abs(sound.volume - testCase.soundVolume) < 0.00001
    && (testCase.soundPitchRange ? sound.pitch >= testCase.soundPitchRange[0] && sound.pitch <= testCase.soundPitchRange[1] : Math.abs(sound.pitch - testCase.soundPitch) < 0.00001)
  const onSound = packet => sounds.push(decodeSoundPacket(actor.bot.registry, packet))
  actor.bot._client.on('sound_effect', onSound)
  const stopParticles = observeParticlePackets(actor.bot, packet => particles.push(packet))
  async function snapshot(player = actor) {
    const token = `a${++sequence}`
    return fixtureJson(context, `/adaptqa snapshot ${player.bot.username} ${token}`, `SNAPSHOT ${token}`)
  }
  async function learn(name, level, preserve = false) {
    const command = preserve ? 'learn-add' : 'learn'
    await context.command(`/adaptqa ${command} ${name} ${level}`, new RegExp(`^ADAPT_QA ${command.toUpperCase()} ${name} ${level}$`), 5000)
  }
  async function learnPrerequisites(testCase) {
    for (const prerequisite of testCase.prerequisites ?? []) await learn(prerequisite.name, prerequisite.level, true)
  }
  async function stage(name) {
    actor.bot.clearControlStates()
    actor.bot.deactivateItem()
    opponent.bot.clearControlStates()
    opponent.bot.deactivateItem()
    await context.command(`/adaptqa stage ${name}`, new RegExp(`^ADAPT_QA STAGE ${name}$`), name.startsWith('discovery-qa-') ? 60000 : 10000)
    await actor.bot.waitForTicks(6)
  }
  async function equip(name, player = actor, destination = 'hand') {
    await context.waitUntil(() => player.bot.inventory.items().some(item => item.name === name), { label: `${name} available`, timeoutMs: 5000 })
    await player.bot.equip(player.bot.inventory.items().find(item => item.name === name), destination)
  }
  try {
    await context.step('prepare ordinary adaptation players and validate live catalog', async () => {
      await context.command(`/adaptqa setup ${actor.bot.username} ${opponent.bot.username}`, /^ADAPT_QA SETUP /, 60000)
      setup = true
      for (const player of [actor, opponent]) stopInputs.push(synchronizePlayerAttributes(player.bot))
      const initial = await snapshot()
      context.expect(!initial.operator, 'Adaptation actor is not an operator')
      assertSkillCoverage(initial.registered, initial.enabled)
      const catalog = await fixtureJson(context, '/adaptqa catalog c1', 'CATALOG c1')
      evidence.catalog = catalog.skills.flatMap(skill => skill.adaptations.map(adaptation => ({ ...adaptation, skill: skill.name })))
      const names = new Set(evidence.catalog.map(entry => entry.name))
      context.expect(names.size === evidence.catalog.length, 'No duplicate live adaptations')
      for (const entry of matrix) {
        if (!names.has(entry.name)) {
          context.expect(entry.availability.kind === 'plugin', `Required adaptation missing: ${entry.name}`)
          evidence.unavailable.push(entry.name)
        }
      }
      for (const entry of evidence.catalog) {
        context.expect(matrix.some(expected => expected.name === entry.name && expected.skill === entry.skill), `Unexpected adaptation: ${entry.name}`)
        context.expect(entry.enabled && entry.maxLevel > 0, `Adaptation disabled or invalid: ${entry.name}`)
      }
    })
    for (const entry of caseNames ? [] : evidence.catalog) {
      await context.step(`level assignment contract: ${entry.name}`, async () => {
        for (const level of new Set([1, entry.maxLevel, 0])) {
          await learn(entry.name, level)
          const state = await snapshot()
          context.expect(state.learned[entry.name] === level, `${entry.name} retains level ${level}`)
          context.expect(Object.entries(state.learned).every(([name, value]) => name === entry.name || value === 0), 'Only the selected adaptation is learned')
        }
      })
    }
    for (const entry of evidence.catalog) {
      if (nativeEvidence?.name === entry.name && (!caseNames || caseNames.includes(entry.name))) {
        await context.step(`native client activation: ${entry.name}`, async () => {
          evidence.adaptations.push(nativeEvidence)
          evidence.missingBehavior = evidence.missingBehavior.filter(name => name !== entry.name)
          await checkpoint(entry.name, { status: 'passed', ...nativeEvidence })
          process.stderr.write(`[PASS] ${entry.name} (native client, matching Adapt artifact)\n`)
        })
        continue
      }
      const testCase = behaviorCases.get(entry.name)
      const specification = matrix.find(candidate => candidate.name === entry.name)
      if (!testCase || (caseNames && !caseNames.includes(entry.name))) continue
      let logStart
      try {
        await context.step(`activation: ${entry.name}`, async () => {
        logStart = await logPosition(join(context.report.server.directory, 'logs/latest.log'))
        evidence.currentCase = { name: entry.name, sounds, particles }
        await context.waitUntil(async () => {
          const state = await snapshot()
          evidence.currentCase.readiness = { tps: state.tps, transitionDensity: state.feedbackTransitionDensity }
          return isFeedbackReady(state)
        }, { label: 'server TPS and feedback density recovered for assertions', timeoutMs: 120000, intervalMs: 1000 })
        await learnPrerequisites(testCase)
        await stage(testCase.stage)
        if (testCase.prepare) await testCase.prepare({ context, actor, opponent, snapshot, equip })
        let baseline = await snapshot()
        sounds.length = 0
        particles.length = 0
        const unlearnedResult = await testCase.trigger({ context, actor, opponent, snapshot, equip })
        evidence.currentCase.unlearned = unlearnedResult
        await checkpoint(entry.name, { status: 'running', ...evidence.currentCase })
        await actor.bot.waitForTicks(testCase.negativeWindowTicks ?? (testCase.stage === 'seaborne' ? 100 : 12))
        const unlearned = await snapshot()
        if (testCase.attribute) context.expect(Math.abs(unlearned.attributes[testCase.attribute] - baseline.attributes[testCase.attribute]) < 0.00001, `${entry.name} has no unlearned attribute bonus`)
        else if (testCase.effect) context.expect(!unlearned.effects.some(effect => effect.type === testCase.effect), `${entry.name} has no unlearned potion effect`)
        if (testCase.sound && testCase.soundControl !== 'per-action-count') context.expect(!sounds.some(sound => matchesSound(sound, testCase)), `${entry.name} sound is absent while unlearned`)
        if (testCase.particle) context.expect(!particles.some(particle => matchesParticle(particle, testCase)), `${entry.name} particle is absent while unlearned`)
        await stage(testCase.stage)
        if (testCase.prepare) await testCase.prepare({ context, actor, opponent, snapshot, equip })
        baseline = await snapshot()
        sounds.length = 0
        particles.length = 0
        await learn(entry.name, entry.maxLevel)
        await learnPrerequisites(testCase)
        const activeResult = await testCase.trigger({ context, actor, opponent, snapshot, equip })
        evidence.currentCase.active = activeResult
        await checkpoint(entry.name, { status: 'running', ...evidence.currentCase })
        let active
        let verification
        if (testCase.verify) {
          verification = await testCase.verify({ context, unlearned: unlearnedResult, active: activeResult })
          active = await snapshot()
        } else await context.waitUntil(async () => {
          active = await snapshot()
          if (testCase.attribute) return testCase.direction === 'decrease'
            ? active.attributes[testCase.attribute] < baseline.attributes[testCase.attribute]
            : active.attributes[testCase.attribute] > baseline.attributes[testCase.attribute]
          return active.effects.some(effect => effect.type === testCase.effect)
        }, { label: `${entry.name} changes ${testCase.attribute ?? testCase.effect}`, timeoutMs: 15000, intervalMs: 250 })
        if (testCase.soundControl === 'per-action-count') context.expect(Array.isArray(verification?.soundAssertions) && verification.soundAssertions.length >= 2, `${entry.name} requires explicit control and learned sound-count assertions`)
        if (testCase.sound) await context.waitUntil(() => sounds.some(sound => matchesSound(sound, testCase)), { label: `${entry.name} specific sound packet`, timeoutMs: 5000 })
        if (testCase.particle) await context.waitUntil(() => particles.some(particle => matchesParticle(particle, testCase)), { label: `${entry.name} specific particle packet`, timeoutMs: 5000 })
        const result = {
          name: entry.name, prerequisites: testCase.prerequisites ?? [],
          behavior: { status: 'passed', assertions: verification?.assertions ?? ['same trigger has no effect while unlearned', `${testCase.attribute ?? testCase.effect} changed after ${testCase.description ?? 'natural player trigger'}`] },
          sound: { status: testCase.sound ? 'passed' : specification.sounds.length || specification.feedbackPresets.length ? 'unverified' : 'not-applicable', assertions: testCase.sound ? [`received ${sounds.find(sound => matchesSound(sound, testCase))?.name}`, ...(verification?.soundAssertions ?? [])] : [], packets: sounds.slice(0, 32), matchedPackets: testCase.sound ? sounds.filter(sound => matchesSound(sound, testCase)).slice(0, 32) : [] },
          particles: { status: testCase.particle ? 'passed' : specification.particles.length || specification.feedbackPresets.length ? 'unverified' : 'not-applicable', assertions: testCase.particle ? [`received ${testCase.particle}`] : [], packets: particles.slice(0, 32), matchedPackets: testCase.particle ? particles.filter(particle => matchesParticle(particle, testCase)).slice(0, 32) : [] },
          measurements: verification?.measurements ?? { attribute: testCase.attribute, effect: testCase.effect,
            baseline: testCase.attribute ? baseline.attributes[testCase.attribute] : baseline.effects,
            unlearned: testCase.attribute ? unlearned.attributes[testCase.attribute] : unlearned.effects,
            active: testCase.attribute ? active.attributes[testCase.attribute] : active.effects },
        }
        actor.bot.clearControlStates()
        await learn(entry.name, 0)
        if (testCase.attribute) {
          await context.waitUntil(async () => Math.abs((await snapshot()).attributes[testCase.attribute] - baseline.attributes[testCase.attribute]) < 0.00001,
            { label: `${entry.name} attribute removed after unlearning`, timeoutMs: 15000, intervalMs: 250 })
          result.behavior.assertions.push('attribute removed after unlearning')
        }
        result.runtime = await errorsSince(logStart)
        result.readiness = evidence.currentCase.readiness
        evidence.currentCase.runtime = result.runtime
        context.expect(result.runtime.errors.length === 0, `${entry.name} emitted server errors`, result.runtime.errors)
        evidence.adaptations.push(result)
        await checkpoint(entry.name, { status: 'passed', ...result })
        process.stderr.write(`[PASS] ${entry.name}\n`)
        delete evidence.currentCase
        evidence.missingBehavior = evidence.missingBehavior.filter(name => name !== entry.name)
        })
      } catch (error) {
        let runtime = evidence.currentCase?.runtime
        if (!runtime && logStart) {
          try { runtime = await errorsSince(logStart) } catch (logError) { runtime = { error: logError.message } }
        }
        evidence.caseFailures.push({ name: entry.name, error: error.message, runtime, readiness: evidence.currentCase?.readiness, unlearned: evidence.currentCase?.unlearned, active: evidence.currentCase?.active, inventory: actor.bot.inventory.items().map(item => ({ name: item.name, count: item.count, slot: item.slot })), sounds: sounds.slice(0, 64), particles: particles.slice(0, 64) })
        await checkpoint(entry.name, { status: 'failed', ...evidence.caseFailures.at(-1) })
        process.stderr.write(`[FAIL] ${entry.name}: ${error.message}\n`)
        context.signal.throwIfAborted()
        actor.bot.clearControlStates()
        actor.bot.deactivateItem()
        await learn(entry.name, 0)
      }
    }
    let completenessError
    try {
      evidence.completeness = { status: 'passed', ...assertAdaptationEvidence(matrix, evidence.adaptations) }
      if (nativeEvidence) evidence.completeness.realClient = { status: 'partially-verified', adaptations: [nativeEvidence.name],
        provenance: nativeEvidence.provenance, scope: 'Native physics, sound playback, and attributed particle rendering for Rubber Soul only.' }
    } catch (error) {
      evidence.completeness = { status: 'incomplete', reason: error.message }
      completenessError = error
    }
    await checkpoint('summary', { status: evidence.caseFailures.length || requireComplete && completenessError ? 'failed' : 'passed', completeness: evidence.completeness, passed: evidence.adaptations.map(entry => entry.name), failed: evidence.caseFailures.map(entry => entry.name), missing: evidence.missingBehavior, unavailable: evidence.unavailable })
    context.expect(evidence.caseFailures.length === 0, `${evidence.caseFailures.length} adaptation cases failed`, evidence.caseFailures)
    if (requireComplete && completenessError) throw completenessError
  } finally {
    actor.bot._client.off('sound_effect', onSound)
    stopParticles()
    for (const stop of stopInputs) stop()
    for (const player of [actor, opponent]) {
      player.bot.clearControlStates()
      player.bot.deactivateItem()
      if (player.bot.currentWindow) player.bot.closeWindow(player.bot.currentWindow)
    }
    if (setup && !context.signal.aborted) await context.command('/adaptqa cleanup', /^ADAPT_QA CLEANUP$/, 15000)
  }
}

export default {
  name: 'adapt-all-adaptations',
  description: 'Exercise learning and available activation cases; fail if any adaptation lacks behavioral or feedback evidence.',
  async run(context) {
    await context.command('/iris create name=adapt_gameplay_iris type=native-terrain seed=78264193', /Successfully created your world/i, 180000)
    await runAdaptationSuite(context, { nativeClientReport: context.options.command })
  },
}
