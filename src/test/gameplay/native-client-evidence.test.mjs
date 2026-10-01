import assert from 'node:assert/strict'
import { test } from 'node:test'
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { loadNativeClientEvidence, validateNativeClientEvidence } from './native-client-evidence.mjs'
import { assertAdaptationEvidence } from './adaptation-evidence.mjs'
import { captureEvidenceManifest } from './evidence-manifest.mjs'

const sha = 'a'.repeat(64)
const state = (value, ticks = 1, y = 100, onGround = true) => ({ attributes: { 'minecraft:bounciness': value },
  ticks, position: { y }, velocity: { y: 0 }, onGround, events: [], particleRenderLayers: 0 })
const audio = () => ({ type: 'sound', name: 'minecraft:block.slime_block.fall', volume: 0.5, pitch: 1.4,
  result: 'STARTED', channelPlaying: true })
function fall(value, apex, velocity) {
  const samples = [state(value, 1, 106, false), state(value, 10), state(value, 11, apex, false)]
  samples[2].velocity.y = velocity
  return { before: state(value), samples, landingIndex: 1, apexAfterLanding: apex, upwardVelocityAfterLanding: velocity }
}
function validReport() {
  const played = state(1, 12)
  played.events = [audio(), { type: 'particle', name: 'minecraft:item_slime', created: true }]
  return { status: 'passed', serverLogPassed: true, cleanupErrors: [], clientLog: { errors: [] },
    minecraft: '26.2', artifactSha256: sha,
    cases: [
      { name: 'kinetics-rubber-soul-native-bounce', status: 'passed', control: fall(0, 100, 0), active: fall(0.5, 101, 0.4) },
      { name: 'kinetics-rubber-soul-feedback', status: 'passed', control: state(0, 5, 99.9375),
        controlSamples: [state(0, 1, 99.9375), state(0, 2, 100.1, false), state(0, 3, 99.9375)],
        before: state(0.5, 10), landed: state(1, 11), played, rendered: { events: [{ type: 'particle', name: 'minecraft:item_slime', created: true, rendered: true, renderedFrames: 2 }] },
        expired: state(0.5, 30), removed: state(0, 40),
        surfaceTrials: ['slime', 'bed'].map(surface => ({ surface, landing: state(1), feedback: { events: [audio()] } })) },
    ] }
}

function validate(report) { return validateNativeClientEvidence(report, { artifactSha256: sha }) }

test('native proof covers only Rubber Soul and satisfies its strict behavior and feedback contract', async () => {
  const entry = validate(validReport())
  const matrix = JSON.parse(await readFile(new URL('./adaptation-matrix.json', import.meta.url), 'utf8')).adaptations
  assert.equal(entry.name, 'kinetics-rubber-soul')
  assert.equal(assertAdaptationEvidence(matrix.filter(item => item.name === entry.name), [entry]).behaviorPassed, 1)
  assert.throws(() => assertAdaptationEvidence(matrix, [entry]), /missing behavior evidence/)
})

for (const [label, mutate] of [
  ['failed report', report => { report.status = 'failed' }],
  ['unclean server', report => { report.serverLogPassed = false }],
  ['missing server gate', report => { delete report.serverLogPassed }],
  ['cleanup error', report => { report.cleanupErrors.push('client still alive') }],
  ['missing cleanup gate', report => { delete report.cleanupErrors }],
  ['client exception', report => { report.clientLog.errors.push('render exception') }],
  ['artifact mismatch', report => { report.artifactSha256 = 'b'.repeat(64) }],
  ['unsupported protocol proof', report => { report.minecraft = '26.1.2' }],
  ['one case only', report => { report.cases.pop() }],
  ['duplicate case', report => { report.cases.push(report.cases[0]) }],
  ['failed case', report => { report.cases[0].status = 'failed' }],
  ['missing raw physics', report => { delete report.cases[0].active.samples }],
  ['fabricated physics summary', report => { report.cases[0].active.apexAfterLanding = 103 }],
  ['no native rebound', report => { report.cases[0].active = fall(0.5, 100, 0) }],
  ['rebounding control', report => { report.cases[0].control = fall(0, 100.5, 0.2) }],
  ['missing passive', report => { report.cases[0].active.before = state(0) }],
  ['no control jump', report => { report.cases[1].controlSamples[1] = state(0, 2) }],
  ['no control landing', report => { report.cases[1].controlSamples[2].onGround = false }],
  ['control bonus', report => { report.cases[1].controlSamples[1].attributes['minecraft:bounciness'] = 0.5 }],
  ['control authored sound', report => { report.cases[1].control.events.push(audio()) }],
  ['control authored particles', report => { report.cases[1].control.events.push({ type: 'particle', name: 'minecraft:item_slime' }) }],
  ['unclamped springload', report => { report.cases[1].landed = state(1.3, 11) }],
  ['no expiry', report => { report.cases[1].expired = state(1, 30) }],
  ['no unlearning', report => { report.cases[1].removed = state(0.5, 40) }],
  ['invalid state ordering', report => { report.cases[1].expired.ticks = 9 }],
  ['no actual channel', report => { report.cases[1].played.events[0].channelPlaying = false }],
  ['wrong sound pitch', report => { report.cases[1].played.events[0].pitch = 1 }],
  ['failed particle factory', report => { report.cases[1].played.events[1].created = false }],
  ['no attributed rendered particle', report => { report.cases[1].rendered.events = [] }],
  ['particle never rendered', report => { report.cases[1].rendered.events[0].rendered = false }],
  ['no completed render frame', report => { report.cases[1].rendered.events[0].renderedFrames = 0 }],
  ['unrelated particle rendering', report => { report.cases[1].rendered.events[0].name = 'minecraft:cloud' }],
  ['missing native surface', report => { report.cases[1].surfaceTrials.pop() }],
  ['wrong native surface', report => { report.cases[1].surfaceTrials[1].surface = 'stone' }],
  ['surface missing playback', report => { report.cases[1].surfaceTrials[1].feedback.events = [] }],
]) {
  test(`rejects ${label}`, () => {
    const report = validReport()
    mutate(report)
    assert.throws(() => validate(report), /kinetics-rubber-soul: native client evidence/)
  })
}

test('loader preserves exact report provenance and rejects missing or malformed reports', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'adapt-native-evidence-'))
  const path = join(directory, 'report.json')
  try {
    await assert.rejects(loadNativeClientEvidence(path, sha), /ENOENT/)
    await writeFile(path, JSON.stringify(validReport()))
    const entry = await loadNativeClientEvidence(path, sha)
    assert.equal(entry.provenance.reportPath, path)
    assert.equal(entry.provenance.artifactSha256, sha)
    assert.match(entry.provenance.reportSha256, /^[a-f0-9]{64}$/)
    await assert.rejects(loadNativeClientEvidence(path, 'b'.repeat(64)), /artifact SHA/)
    await writeFile(path, '{')
    await assert.rejects(loadNativeClientEvidence(path, sha), SyntaxError)
  } finally { await rm(directory, { recursive: true, force: true }) }
})

function skippedCollision() {
  const samples = [
    [194, 106, -0.0784000015],
    [206, 100.31161120717262, -0.9054323524772837],
    [208, 100.37560618134559, 0.42000854544344746],
    [214, 101.62785837282313, -0.07543648365656166],
    [221, 100, 0.17707498664410207],
  ].map(([ticks, y, velocity]) => ({ ...state(0.5, ticks, y, false), velocity: { y: velocity } }))
  return { before: state(0.5), samples, landingIndex: 2,
    apexAfterLanding: 101.62785837282313, upwardVelocityAfterLanding: 0.42000854544344746 }
}

test('first low-altitude rebound proves collision when polling misses the contact tick', () => {
  const report = validReport()
  report.cases[0].active = skippedCollision()
  assert.equal(validate(report).behavior.status, 'passed')
})

test('later landing cannot discard the first rebound when summaries agree with the later samples', () => {
  const report = validReport()
  const active = report.cases[0].active = skippedCollision()
  active.landingIndex = 4
  active.apexAfterLanding = 100
  active.upwardVelocityAfterLanding = active.samples[4].velocity.y
  assert.throws(() => validate(report), /not the first observed collision/)
})

for (const [label, mutate] of [
  ['high-altitude reversal', active => { active.samples[2].position.y = 102 }],
  ['high-altitude previous sample', active => { active.samples[1].position.y = 102 }],
  ['excessive tick gap', active => { active.samples[2].ticks = 210 }],
  ['same-tick reversal', active => { active.samples[2].ticks = 206 }],
  ['missing tick', active => { delete active.samples[1].ticks }],
  ['no prior downward motion', active => { active.samples[1].velocity.y = 0 }],
  ['no upward motion', active => { active.samples[2].velocity.y = 0 }],
]) {
  test(`rejects ${label} as skipped collision proof`, () => {
    const report = validReport()
    const active = report.cases[0].active = skippedCollision()
    mutate(active)
    assert.throws(() => validate(report), /not the first observed collision/)
  })
}

test('deployed manifest digest accepts exactly one external slot in the 311-adaptation gate', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'adapt-native-integration-'))
  try {
    await mkdir(join(directory, 'plugins'))
    await mkdir(join(directory, 'harness'))
    for (const file of ['server.jar', '.server-source', 'server.properties', 'plugins/Adapt.jar']) {
      await writeFile(join(directory, file), 'integration fixture ' + file)
    }
    const manifest = await captureEvidenceManifest(directory, { harnessDirectory: join(directory, 'harness') })
    const deployedSha = manifest.artifacts.find(artifact => artifact.path === 'plugins/Adapt.jar').sha256
    const report = validReport()
    report.artifactSha256 = deployedSha
    const reportPath = join(directory, 'native report with spaces.json')
    await writeFile(reportPath, JSON.stringify(report))
    const native = await loadNativeClientEvidence(reportPath, deployedSha)
    const matrix = JSON.parse(await readFile(new URL('./adaptation-matrix.json', import.meta.url), 'utf8')).adaptations
    assert.equal(matrix.length, 311)
    const protocol = matrix.filter(adaptation => adaptation.name !== native.name).map(adaptation => ({
      name: adaptation.name,
      behavior: { status: 'passed', assertions: ['Unit fixture behavior evidence'] },
      sound: { status: 'passed', assertions: ['Unit fixture sound evidence'] },
      particles: { status: 'passed', assertions: ['Unit fixture particle evidence'] },
    }))
    assert.equal(protocol.length, 310)
    assert.throws(() => assertAdaptationEvidence(matrix, protocol), /kinetics-rubber-soul: missing behavior evidence/)
    assert.equal(assertAdaptationEvidence(matrix, [...protocol, native]).behaviorPassed, 311)
    assert.throws(() => assertAdaptationEvidence(matrix, [...protocol, native, native]), /kinetics-rubber-soul: duplicate evidence/)
    assert.throws(() => assertAdaptationEvidence(matrix, [...protocol.slice(1), native]), new RegExp(protocol[0].name + ': missing behavior evidence'))
    assert.equal(native.provenance.reportPath, reportPath)
    assert.equal(native.provenance.artifactSha256, deployedSha)
    await writeFile(join(directory, 'plugins/Adapt.jar'), 'changed deployed artifact')
    const changed = await captureEvidenceManifest(directory, { harnessDirectory: join(directory, 'harness') })
    const changedSha = changed.artifacts.find(artifact => artifact.path === 'plugins/Adapt.jar').sha256
    await assert.rejects(loadNativeClientEvidence(reportPath, changedSha), /artifact SHA does not match/)
  } finally { await rm(directory, { recursive: true, force: true }) }
})
