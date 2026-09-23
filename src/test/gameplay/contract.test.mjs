import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { skillMatrix, assertSkillCoverage, xpEvidence } from './skill-matrix.mjs'

test('matrix covers every registered skill and uses its actual stable name', async () => {
  const registry = await readFile(new URL('../../main/java/art/arcane/adapt/api/skill/SkillRegistry.java', import.meta.url), 'utf8')
  const registered = [...registry.matchAll(/registerSkill\((Skill\w+)\.class\)/g)].map(match => match[1]).sort()
  assert.deepEqual(skillMatrix.map(entry => entry.source).sort(), registered)
  for (const { source, skill } of skillMatrix) {
    const code = await readFile(new URL(`../../main/java/art/arcane/adapt/content/skill/${source}.java`, import.meta.url), 'utf8')
    assert.match(code, new RegExp(`super\\("${skill}"`))
  }
})

test('runtime registry omissions, extra skills, duplicate names, and disabled skills fail coverage', () => {
  const all = skillMatrix.map(entry => entry.skill)
  assert.doesNotThrow(() => assertSkillCoverage(all))
  assert.throws(() => assertSkillCoverage(all.slice(1)), /mismatch/)
  assert.throws(() => assertSkillCoverage([...all, 'new-skill']), /mismatch/)
  assert.throws(() => assertSkillCoverage([...all, all[0]]), /mismatch/)
  assert.throws(() => assertSkillCoverage(all, all.slice(1)), /disabled/)
})

test('XP evidence rejects no gain, invalid values, operators, and mixed identities', () => {
  const before = { player: 'ordinary', operator: false, xp: { agility: 2 } }
  const after = { player: 'ordinary', operator: false, xp: { agility: 5 } }
  assert.equal(xpEvidence('agility', before, after).gained, 3)
  for (const invalid of [before, { ...after, operator: true }, { ...after, player: 'other' }, { ...after, xp: { agility: NaN } }]) {
    assert.throws(() => xpEvidence('agility', before, invalid))
  }
})

test('fixture never grants Adapt XP or dispatches synthetic gameplay events', async () => {
  const fixture = await readFile(new URL('./fixture/java/art/arcane/adapt/gameplay/AdaptGameplayFixture.java', import.meta.url), 'utf8')
  assert.doesNotMatch(fixture, /\.(?:giveXP|giveXPFresh|setXp|setPooledXp|flushXpPool|callEvent)\s*\(/)
  assert.match(fixture, /getXp\(\) \+ line\.getPooledXp\(\)/)
  const scenario = await readFile(new URL('./all-skills.mjs', import.meta.url), 'utf8')
  for (const { skill } of skillMatrix) assert.match(scenario, new RegExp(`case '${skill}'`))
})
