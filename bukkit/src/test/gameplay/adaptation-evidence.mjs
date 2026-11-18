export function assertAdaptationEvidence(adaptations, entries) {
  if (!Array.isArray(adaptations) || adaptations.length === 0 || !Array.isArray(entries)) {
    throw new Error('Adaptation coverage requires a catalog and evidence entries')
  }
  const expected = new Map(adaptations.map(adaptation => [adaptation.name, adaptation]))
  if (expected.size !== adaptations.length) throw new Error('Duplicate adaptation names in catalog')
  const observed = new Map()
  const failures = []
  for (const entry of entries) {
    if (!entry || !expected.has(entry.name)) {
      failures.push(`Unknown adaptation: ${entry?.name}`)
      continue
    }
    if (observed.has(entry.name)) failures.push(`${entry.name}: duplicate evidence`)
    observed.set(entry.name, entry)
  }
  for (const adaptation of adaptations) {
    const entry = observed.get(adaptation.name)
    if (!entry) {
      failures.push(`${adaptation.name}: missing behavior evidence`)
      continue
    }
    const required = {
      behavior: true,
      sound: adaptation.sounds.length > 0 || adaptation.feedbackPresets.length > 0,
      particles: adaptation.particles.length > 0 || adaptation.feedbackPresets.length > 0,
    }
    for (const [aspect, mustPass] of Object.entries(required)) {
      const evidence = entry[aspect]
      if (!mustPass && evidence?.status === 'not-applicable') continue
      if (evidence?.status !== 'passed' || !Array.isArray(evidence.assertions) || evidence.assertions.length === 0
          || evidence.assertions.some(assertion => typeof assertion !== 'string' || assertion.trim().length === 0)) {
        failures.push(`${adaptation.name}: ${aspect} lacks positive assertions`)
      }
    }
  }
  if (failures.length) throw new Error(`Incomplete adaptation coverage (${failures.length} gaps):\n${failures.join('\n')}`)
  return {
    adaptations: adaptations.length,
    behaviorPassed: adaptations.length,
    soundPacketsPassed: entries.filter(entry => entry.sound.status === 'passed').length,
    particlePacketsPassed: entries.filter(entry => entry.particles.status === 'passed').length,
    realClient: { status: 'not-verified', scope: 'Particle rendering and audible sound require a real Minecraft client.' },
  }
}
