export const skillMatrix = Object.freeze([
  { skill: 'agility', source: 'SkillAgility', action: 'walk a measured circle' },
  { skill: 'axes', source: 'SkillAxes', action: 'hit another player with an axe' },
  { skill: 'swords', source: 'SkillSwords', action: 'hit another player with a sword' },
  { skill: 'unarmed', source: 'SkillUnarmed', action: 'trade unarmed hits with another player' },
  { skill: 'stealth', source: 'SkillStealth', action: 'hit another player while sneaking' },
  { skill: 'tragoul', source: 'SkillTragOul', action: 'receive damage from another player' },
  { skill: 'blocking', source: 'SkillBlocking', action: 'block another player with a shield' },
  { skill: 'architect', source: 'SkillArchitect', action: 'place a new structural block' },
  { skill: 'pickaxe', source: 'SkillPickaxes', action: 'mine natural fixture ore' },
  { skill: 'excavation', source: 'SkillExcavation', action: 'dig natural fixture clay' },
  { skill: 'nether', source: 'SkillNether', action: 'break a wither rose' },
  { skill: 'crafting', source: 'SkillCrafting', action: 'craft planks from logs' },
  { skill: 'discovery', source: 'SkillDiscovery', action: 'discover a fixture block by interaction' },
  { skill: 'herbalism', source: 'SkillHerbalism', action: 'eat an apple while hungry' },
  { skill: 'brewing', source: 'SkillBrewing', action: 'drink a swiftness potion' },
  { skill: 'chronos', source: 'SkillChronos', action: 'drink a swiftness potion' },
  { skill: 'ranged', source: 'SkillRanged', action: 'shoot an arrow at another player' },
  { skill: 'rift', source: 'SkillRift', action: 'throw an ender pearl and teleport' },
  { skill: 'enchanting', source: 'SkillEnchanting', action: 'enchant a sword in the enchanting table' },
  { skill: 'hunter', source: 'SkillHunter', action: 'kill a fixture cow with a player attack' },
  { skill: 'taming', source: 'SkillTaming', action: 'feed and breed two cows' },
  { skill: 'kinetics', source: 'SkillKinetics', action: 'walk off a ledge and break the fall on hay' },
  { skill: 'seaborne', source: 'SkillSeaborne', action: 'break a block while submerged' },
].map(Object.freeze))

export function assertSkillCoverage(registered, enabled = registered) {
  const expected = skillMatrix.map(entry => entry.skill).sort()
  const actual = [...registered].sort()
  if (new Set(actual).size !== actual.length || JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(`Adapt skill matrix mismatch: registered=${actual.join(',')} covered=${expected.join(',')}`)
  }
  const disabled = expected.filter(skill => !enabled.includes(skill))
  if (disabled.length) throw new Error(`Required Adapt skills are disabled: ${disabled.join(',')}`)
}

export function xpEvidence(skill, before, after) {
  if (before.player !== after.player || before.operator || after.operator) throw new Error('XP evidence requires one ordinary player')
  const start = before.xp?.[skill]
  const end = after.xp?.[skill]
  if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) throw new Error(`${skill} did not earn XP: ${start} -> ${end}`)
  return { skill, player: after.player, before: start, after: end, gained: end - start, includesPendingPayout: true }
}
