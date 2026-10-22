import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { measureProgress, progressionRequirements } from './xp-integrity.mjs'

test('progress evidence separates observed balances from elapsed time and rejects mixed identities', () => {
    const before = { player: 'ordinary', operator: false, masterXp: 1, masterLevel: 0,
        lines: { crafting: { totalXp: 10, level: 0 } } }
    const after = { ...before, now: 2000, masterXp: 490, lines: { crafting: { totalXp: 1310, level: 1 } } }
    const evidence = measureProgress(before, after, 1000, 5)
    assert.equal(evidence.elapsedMillis, 1000)
    assert.equal(evidence.skills[0].gained, 1300)
    assert.equal(evidence.masterXpGained, 489)
    assert.throws(() => measureProgress(before, { ...after, player: 'other' }, 1000, 5))
    assert.throws(() => measureProgress(before, { ...after, operator: true }, 1000, 5))
    assert.throws(() => measureProgress(before, after, 2000, 5))
    assert.throws(() => measureProgress(before, after, 1000, 0))
})

test('tree requirements distinguish individual knowledge from simultaneous power and the skill cap', () => {
    const requirements = progressionRequirements({ maximumSkillLevel: 1000, maximumSkillXp: 101200000,
        powerPerLevel: 0.65, skills: [{ skill: 'crafting', enabled: true,
            adaptations: [{ id: 'a', enabled: true, maximumLevel: 5, knowledge: 15, requirements: [] },
                { id: 'b', enabled: false, maximumLevel: 10, knowledge: 200, requirements: [] }] }] })
    assert.equal(requirements.maximumSkillXp, 101200000)
    assert.equal(requirements.skills[0].minimumSkillLevelForKnowledge, 14)
    assert.equal(requirements.skills[0].adaptationLevels, 5)
    assert.equal(requirements.skills[0].minimumMasterLevelForPower, 8)
    assert.equal(requirements.masterLevelForSimultaneousMaximumAdaptations, 8)
    assert.equal(requirements.simultaneousMaximumFitsMasterCap, true)
})

test('XP fixture cannot grant progression or synthesize gameplay events to establish speed', async () => {
    const code = await readFile(new URL('./fixture/java/art/arcane/adapt/gameplay/XpIntegrityFixture.java', import.meta.url), 'utf8')
    assert.doesNotMatch(code, /\.(?:giveXP|giveXPFresh|setXp|setMasterXp|setPooledXp|flushXpPool|callEvent|award)\s*\(/)
    assert.doesNotMatch(code, /data\.toJson\(/)
    assert.match(code, /line\.getXp\(\) \+ line\.getPooledXp\(\)/)
})
