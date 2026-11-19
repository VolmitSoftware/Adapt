import { compareVersions } from './version-profile.mjs'

// Server-bound text (commands, NBT) must use the server's own ids and syntax. Client-side names need no aliasing:
// the bots are 26.1 clients and ViaProxy translates every registry id the server sends into its 26.1 name.

const PLAYER_ATTRIBUTES = new Set(['block_break_speed', 'block_interaction_range', 'entity_interaction_range', 'mining_efficiency',
  'sneaking_speed', 'submerged_mining_speed', 'sweeping_damage_ratio'])
const RENAMES = [
  { kind: 'item', name: 'short_grass', legacy: 'grass', since: '1.20.3' },
  { kind: 'block', name: 'short_grass', legacy: 'grass', since: '1.20.3' },
  { kind: 'item', name: 'turtle_scute', legacy: 'scute', since: '1.20.5' },
  { kind: 'enchantment', name: 'sweeping_edge', legacy: 'sweeping', since: '1.20.5' },
]
const CONTENT = [
  { kind: 'block', name: 'crafter', since: '1.21' },
  { kind: 'item', name: 'wind_charge', since: '1.21' },
  { kind: 'item', name: 'scrape_pottery_sherd', since: '1.21' },
  { kind: 'entity', name: 'copper_golem', since: '1.21.9' },
]
const ATTRIBUTE_PREFIX_REMOVED = '1.21.2'
const ITEM_COMPONENTS = '1.20.5'

/** Minecraft version of the server under test (platform-run.sh exports it); scripts run outside the runner target 26.x. */
export function serverVersion() {
  return process.env.ADAPT_QA_MINECRAFT_VERSION || '26.3'
}

const before = (version, release) => compareVersions(version, release) < 0

function attributeId(name, version) {
  if (!before(version, ATTRIBUTE_PREFIX_REMOVED)) return name
  if (name === 'spawn_reinforcements') return 'zombie.spawn_reinforcements'
  if (name === 'jump_strength' && before(version, ITEM_COMPONENTS)) return 'horse.jump_strength'
  return `${PLAYER_ATTRIBUTES.has(name) ? 'player' : 'generic'}.${name}`
}

/** The server's id for a 26.x registry name (without namespace), e.g. attribute movement_speed -> generic.movement_speed on 1.21.1. */
export function serverId(kind, name, version = serverVersion()) {
  const bare = name.replace(/^minecraft:/, '')
  if (kind === 'attribute') return attributeId(bare, version)
  return RENAMES.find(rename => rename.kind === kind && rename.name === bare && before(version, rename.since))?.legacy ?? bare
}

/** The release that added a 26.x registry entry a scenario needs, when the server predates it; undefined when present. */
export function contentSince(kind, name, version = serverVersion()) {
  const since = CONTENT.find(entry => entry.kind === kind && entry.name === name.replace(/^minecraft:/, ''))?.since
  return since && before(version, since) ? since : undefined
}

/** Item argument for /give or /item with a damage value: components from 1.20.5, NBT before. */
export function damagedItem(name, damage, version = serverVersion()) {
  const id = `minecraft:${serverId('item', name, version)}`
  return before(version, ITEM_COMPONENTS) ? `${id}{Damage:${damage}}` : `${id}[minecraft:damage=${damage}]`
}

/** Item stack as stored in block entity or entity NBT: count from 1.20.5, Count byte before. */
export function itemStackNbt(name, count, version = serverVersion()) {
  const id = `minecraft:${serverId('item', name, version)}`
  return before(version, ITEM_COMPONENTS) ? `{id:"${id}",Count:${count}b}` : `{id:"${id}",count:${count}}`
}
