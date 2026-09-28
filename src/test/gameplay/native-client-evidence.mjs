import { createHash } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const name = 'kinetics-rubber-soul'
const attribute = 'minecraft:bounciness'
const close = (value, expected) => Number.isFinite(value) && Math.abs(value - expected) < 0.000001
const bounce = state => state?.attributes?.[attribute]
const events = state => Array.isArray(state?.events) ? state.events : []
const sound = event => event.type === 'sound' && event.name === 'minecraft:block.slime_block.fall'
  && close(event.volume, 0.5) && close(event.pitch, 1.4)
const played = state => events(state).filter(event => sound(event) && event.result === 'STARTED' && event.channelPlaying === true)
const slime = event => event.type === 'particle' && event.name === 'minecraft:item_slime'

function requireEvidence(condition, message) {
  if (!condition) throw new Error(`${name}: native client evidence ${message}`)
}

function fallMeasurements(trial, expectedAttribute) {
  requireEvidence(close(bounce(trial?.before), expectedAttribute), 'requires the measured stone baseline attribute')
  const samples = trial?.samples
  requireEvidence(Array.isArray(samples) && samples.length >= 3, 'requires raw stone fall samples')
  requireEvidence(samples.every(sample => Number.isFinite(sample?.position?.y) && Number.isFinite(sample?.velocity?.y)), 'contains invalid stone physics samples')
  requireEvidence(samples.some(sample => sample.position.y > 105.5), 'does not show the six-block stone fall')
  requireEvidence(Number.isInteger(trial.landingIndex) && trial.landingIndex >= 0 && trial.landingIndex < samples.length,
    'requires an observed stone landing')
  const landingIndex = samples.findIndex((sample, index) => {
    if (sample.position.y <= 100.05) return true
    const previous = samples[index - 1]
    return previous && previous.velocity.y < 0 && sample.velocity.y > 0
      && previous.position.y <= 101 && sample.position.y <= 101 && sample.position.y >= 100
      && Number.isFinite(previous.ticks) && Number.isFinite(sample.ticks)
      && sample.ticks > previous.ticks && sample.ticks - previous.ticks <= 3
  })
  requireEvidence(landingIndex >= 0 && trial.landingIndex === landingIndex, 'reported stone landing is not the first observed collision or near-ground rebound')
  const after = samples.slice(landingIndex)
  const apex = Math.max(...after.map(sample => sample.position.y))
  const velocity = Math.max(...after.map(sample => sample.velocity.y))
  requireEvidence(close(trial.apexAfterLanding, apex) && close(trial.upwardVelocityAfterLanding, velocity), 'stone summaries disagree with raw samples')
  return { apex, velocity }
}

export function validateNativeClientEvidence(report, { artifactSha256, reportPath, reportSha256 } = {}) {
  requireEvidence(report?.status === 'passed' && report.serverLogPassed === true, 'requires a passed report and clean server log')
  requireEvidence(Array.isArray(report.cleanupErrors) && report.cleanupErrors.length === 0
    && Array.isArray(report.clientLog?.errors) && report.clientLog.errors.length === 0 && !report.error, 'requires clean client and cleanup results')
  requireEvidence(/^[a-f0-9]{64}$/.test(artifactSha256 ?? '') && report.artifactSha256 === artifactSha256, 'artifact SHA does not match deployed Adapt.jar')
  requireEvidence(report.minecraft === '26.2', 'requires the native 26.2 client')
  requireEvidence(Array.isArray(report.cases) && report.cases.every(entry => entry?.status === 'passed'), 'contains missing or failed cases')
  const cases = new Map(report.cases.map(entry => [entry.name, entry]))
  requireEvidence(cases.size === report.cases.length, 'contains duplicate cases')
  const physics = cases.get('kinetics-rubber-soul-native-bounce')
  const feedback = cases.get('kinetics-rubber-soul-feedback')
  requireEvidence(physics && feedback, 'requires both Rubber Soul cases')
  const control = fallMeasurements(physics.control, 0)
  const active = fallMeasurements(physics.active, 0.5)
  requireEvidence(control.apex < 100.1 && control.velocity < 0.05, 'unlearned stone control rebounds')
  requireEvidence(active.apex > control.apex + 0.3 && active.velocity > 0.1, 'does not show a learned native stone rebound')
  const samples = feedback.controlSamples
  requireEvidence(Array.isArray(samples) && samples.length >= 3 && samples.every(sample => close(bounce(sample), 0)), 'requires zero bounciness throughout the honey control')
  const airborne = samples.findIndex(sample => sample.onGround === false && sample.position?.y > 100)
  requireEvidence(airborne >= 0 && samples.slice(airborne + 1).some(sample => sample.onGround === true && Number.isFinite(sample.position?.y) && Math.abs(sample.position.y - 99.9375) < 0.01), 'does not show an unlearned honey jump and landing')
  requireEvidence(!events(feedback.control).some(event => sound(event) || slime(event)), 'contains Rubber Soul feedback in the unlearned honey control')
  requireEvidence(close(bounce(feedback.before), 0.5) && close(bounce(feedback.landed), 1)
    && close(bounce(feedback.expired), 0.5) && close(bounce(feedback.removed), 0), 'does not prove passive, capped springload, expiry, and unlearning')
  requireEvidence(Number.isFinite(feedback.before?.ticks) && feedback.landed?.ticks > feedback.before.ticks
    && feedback.expired?.ticks > feedback.landed.ticks && feedback.removed?.ticks >= feedback.expired.ticks, 'springload state sequence is invalid')
  requireEvidence(played(feedback.played).length > 0, 'lacks actual exact-volume/pitch slime sound playback')
  const particles = events(feedback.played).filter(event => slime(event) && event.created === true)
  requireEvidence(particles.length > 0, 'lacks created native slime particles')
  const renderedParticles = events(feedback.rendered).filter(event => slime(event) && event.created === true
    && event.rendered === true && Number.isFinite(event.renderedFrames) && event.renderedFrames > 0)
  requireEvidence(renderedParticles.length > 0, 'lacks rendered geometry attributed to native slime particles')
  requireEvidence(Array.isArray(feedback.surfaceTrials) && feedback.surfaceTrials.length === 2
    && new Set(feedback.surfaceTrials.map(trial => trial.surface)).size === 2, 'requires slime and bed landing trials')
  for (const surface of ['slime', 'bed']) {
    const trial = feedback.surfaceTrials.find(candidate => candidate.surface === surface)
    requireEvidence(trial && close(bounce(trial.landing), 1) && played(trial.feedback).length > 0, `lacks ${surface} springload and playback`)
  }
  return {
    name,
    behavior: { status: 'passed', assertions: ['Unlearned stone fall does not rebound; learned native fall rebounds higher with upward velocity', 'Natural unlearned honey jump and landing keep bounciness zero', 'Native passive 0.5 rises to capped 1.0 on landing, expires to 0.5, and unlearning removes it', 'Slime and bed landings activate capped springload'] },
    sound: { status: 'passed', assertions: ['Exact slime fall sound is absent in the unlearned honey control and plays on an actual OpenAL channel after learning'], matchedPackets: played(feedback.played) },
    particles: { status: 'passed', assertions: ['Native slime particles are created and submitted in completed render layers after learning; they are absent in the unlearned honey control'], matchedPackets: renderedParticles },
    measurements: { physics, feedback },
    provenance: { kind: 'native-client', reportPath, reportSha256, artifactSha256, minecraft: report.minecraft,
      cases: [physics.name, feedback.name], limits: 'Native physics, exact OpenAL playback, and attributed particle rendering cover Rubber Soul only.' },
  }
}

export async function loadNativeClientEvidence(reportPath, artifactSha256) {
  const path = resolve(reportPath)
  const bytes = await readFile(path)
  return validateNativeClientEvidence(JSON.parse(bytes), { artifactSha256, reportPath: path,
    reportSha256: createHash('sha256').update(bytes).digest('hex') })
}
