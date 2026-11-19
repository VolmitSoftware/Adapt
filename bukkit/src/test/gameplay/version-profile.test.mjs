import test from 'node:test'
import assert from 'node:assert/strict'
import { mkdtempSync, mkdirSync, utimesSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { botRoute, bukkitJarRange, findArtifacts, isModJar, packMeta, serverJavaMajor, shellProfile } from './version-profile.mjs'
import { damagedItem, itemStackNbt, serverId } from './version-ids.mjs'

test('server Java follows the Minecraft version', () => {
  assert.equal(serverJavaMajor('26.3'), 25)
  assert.equal(serverJavaMajor('26.1.2'), 25)
  assert.equal(serverJavaMajor('1.21.1'), 21)
  assert.equal(serverJavaMajor('1.20.1'), 17)
  assert.throws(() => serverJavaMajor('1.21-pre1'), /Unsupported/)
})

test('bots stay 26.1 clients and every version but 26.1.2 goes through ViaProxy', () => {
  assert.deepEqual(botRoute('26.1.2'), { clientVersion: '26.1', proxy: false })
  assert.deepEqual(botRoute('26.3'), { clientVersion: '26.1', proxy: true })
  assert.deepEqual(botRoute('1.21.1'), { clientVersion: '26.1', proxy: true })
  assert.deepEqual(botRoute('1.20.1'), { clientVersion: '26.1', proxy: true })
  assert.match(shellProfile('1.20.1'), /^profile_java_major=17$/m)
  assert.match(shellProfile('1.20.1'), /^profile_proxy=true$/m)
})

test('fixture pack.mcmeta uses pack_format before format 82 and the ranged form after', () => {
  assert.deepEqual(JSON.parse(packMeta('1.20.1')).pack, { description: 'Adapt gameplay fixture dimensions', pack_format: 15 })
  assert.deepEqual(JSON.parse(packMeta('1.21.1')).pack, { description: 'Adapt gameplay fixture dimensions', pack_format: 48 })
  assert.equal(packMeta('26.3', { '26.3': { dataPackFormat: 121 } }), '{"pack":{"description":"Adapt gameplay fixture dimensions","min_format":121,"max_format":121}}')
  assert.throws(() => packMeta('26.4', {}), /No data pack format/)
})

test('jar names select the right artifact for each platform and version', () => {
  assert.deepEqual(bukkitJarRange('Adapt v2.2.7 [CraftBukkit] 26.1.2-26.3.jar'), { low: '26.1.2', high: '26.3' })
  assert.deepEqual(bukkitJarRange('Adapt v2.2.7 [CraftBukkit] 1.20.1.jar'), { low: '1.20.1', high: '1.20.1' })
  assert.equal(bukkitJarRange('Adapt-2.2.7-thin.jar'), undefined)
  assert.equal(isModJar('Adapt v2.2.7 [Forge] 1.20.1+47.4.10.jar', 'forge', '1.20.1'), true)
  assert.equal(isModJar('Adapt v2.2.7 [Forge] 1.20.1+47.4.10.jar', 'forge', '1.21.1'), false)
  assert.equal(isModJar('Adapt v2.2.7 [NeoForge] 1.21.1+21.1.200.jar', 'neoforge', '1.21.1'), true)
})

test('artifact discovery picks the covering Bukkit jar and falls back to the Forge jar for NeoForge 1.20.1', () => {
  const root = mkdtempSync(join(tmpdir(), 'adapt-artifacts-'))
  const touch = (path, seconds) => { writeFileSync(path, ''); utimesSync(path, seconds, seconds) }
  mkdirSync(join(root, 'build/libs'), { recursive: true })
  mkdirSync(join(root, 'legacy/libs'), { recursive: true })
  mkdirSync(join(root, 'build/bukkit-1.21.1/libs'), { recursive: true })
  touch(join(root, 'build/bukkit-1.21.1/libs/Adapt v2.2.7 [CraftBukkit] 1.21.1.jar'), 100)
  touch(join(root, 'build/libs/Adapt v2.2.7 [CraftBukkit] 26.1.2-26.3.jar'), 100)
  touch(join(root, 'build/libs/Adapt v2.2.6 [CraftBukkit] 26.1.2-26.3.jar'), 50)
  touch(join(root, 'legacy/libs/Adapt v2.2.7 [CraftBukkit] 1.20.1.jar'), 100)
  assert.deepEqual(findArtifacts('paper', '26.3', root), {
    adapt: join(root, 'build/libs/Adapt v2.2.7 [CraftBukkit] 26.1.2-26.3.jar'), fixture: join(root, 'build/libs/AdaptGameplayFixture.jar') })
  assert.equal(findArtifacts('paper', '1.21.1', root).adapt, join(root, 'build/bukkit-1.21.1/libs/Adapt v2.2.7 [CraftBukkit] 1.21.1.jar'))
  assert.throws(() => findArtifacts('paper', '1.20.1', root), /No Bukkit Adapt jar covering 1.20.1/)
  assert.equal(findArtifacts('paper', '1.20.1', root, `${join(root, 'build/libs')}:${join(root, 'legacy/libs')}`).fixture, join(root, 'legacy/libs/AdaptGameplayFixture.jar'))
  mkdirSync(join(root, 'adapters/forge/build/1.20.1/libs'), { recursive: true })
  touch(join(root, 'adapters/forge/build/1.20.1/libs/Adapt v2.2.7 [Forge] 1.20.1+47.4.10.jar'), 100)
  assert.deepEqual(findArtifacts('neoforge', '1.20.1', root), {
    adapt: join(root, 'adapters/forge/build/1.20.1/libs/Adapt v2.2.7 [Forge] 1.20.1+47.4.10.jar'), fixture: join(root, 'adapters/forge/build/1.20.1/gameplay/AdaptGameplayFixture.jar') })
  assert.throws(() => findArtifacts('neoforge', '1.21.1', root), /adapters\/\{neoforge\}\/build\/1.21.1/)
})

test('server-bound ids and item syntax follow the server version', () => {
  assert.equal(serverId('attribute', 'movement_speed', '26.3'), 'movement_speed')
  assert.equal(serverId('attribute', 'movement_speed', '1.21.1'), 'generic.movement_speed')
  assert.equal(serverId('attribute', 'minecraft:block_break_speed', '1.21.1'), 'player.block_break_speed')
  assert.equal(serverId('attribute', 'jump_strength', '1.21.1'), 'generic.jump_strength')
  assert.equal(serverId('attribute', 'jump_strength', '1.20.1'), 'horse.jump_strength')
  assert.equal(serverId('block', 'short_grass', '1.20.1'), 'grass')
  assert.equal(serverId('block', 'short_grass', '1.21.1'), 'short_grass')
  assert.equal(serverId('item', 'stone', '1.20.1'), 'stone')
  assert.equal(damagedItem('diamond_pickaxe', 1560, '26.3'), 'minecraft:diamond_pickaxe[minecraft:damage=1560]')
  assert.equal(damagedItem('diamond_pickaxe', 1560, '1.20.1'), 'minecraft:diamond_pickaxe{Damage:1560}')
  assert.equal(itemStackNbt('paper', 1, '1.21.1'), '{id:"minecraft:paper",count:1}')
  assert.equal(itemStackNbt('paper', 1, '1.20.1'), '{id:"minecraft:paper",Count:1b}')
})
