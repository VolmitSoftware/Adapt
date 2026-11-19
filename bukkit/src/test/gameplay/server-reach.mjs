import { createRequire } from 'node:module'
import { decodeSoundPacket, observeParticlePackets } from './feedback.mjs'
import { synchronizePlayerAttributes } from './player-attributes.mjs'
import { compareVersions } from './version-profile.mjs'
import { serverId, serverVersion } from './version-ids.mjs'

// Server reach check without Adapt (run with ADAPT_QA_BARE=1): every client-visible behaviour the contracts depend on,
// and a join/leave load loop. Command "basics" (default) or "load:<rounds>:<bots>[:<stagger ms between joins>]".

function probe(context, results) {
  return async (name, action, diagnose = () => undefined) => {
    const started = Date.now()
    try {
      const detail = await action()
      results.push({ name, result: 'pass', ms: Date.now() - started, detail })
      process.stderr.write(`[PASS] reach-${name}\n`)
    } catch (error) {
      results.push({ name, result: 'fail', ms: Date.now() - started, error: error.message, diagnostic: diagnose() })
      process.stderr.write(`[FAIL] reach-${name}: ${error.message}\n`)
    }
  }
}

function rawPackets(bot, name) {
  const seen = []
  bot._client.on(name, packet => seen.push(JSON.stringify(packet)))
  return seen
}

async function basics(context) {
  const { bot, command, waitUntil, waitForMessage, expect } = context
  const version = serverVersion()
  const token = Math.random().toString(36).slice(2, 8)
  const results = []
  const check = probe(context, results)
  const origin = bot.entity.position.floored()
  const x = origin.x
  const y = 150
  const z = origin.z
  const Vec3 = createRequire(process.argv[1])('vec3').Vec3
  const at = (dx, dy, dz) => bot.blockAt(new Vec3(x + dx, y + dy, z + dz))
  const raw = Object.fromEntries(['action_bar', 'set_title_text', 'boss_bar', 'scoreboard_objective', 'scoreboard_score', 'advancements', 'world_particles']
    .map(name => [name, rawPackets(bot, name)]))
  const sounds = []
  bot._client.on('sound_effect', packet => sounds.push(decodeSoundPacket(bot.registry, packet)))
  const particles = []
  const stopParticles = observeParticlePackets(bot, particle => particles.push(particle), error => particles.push({ error: error.message }))
  const stopAttributes = synchronizePlayerAttributes(bot, () => {})
  const report = context.report.reach = { version, clientVersion: bot.version, results }

  await check('command-and-chat', async () => {
    await command(`/say reach-${token}`, `reach-${token}`, 10000)
    const echo = waitForMessage(`hello-${token}`, 10000)
    bot.chat(`hello-${token}`)
    await echo
  })
  await check('arena-and-teleport', async () => {
    await command(`/gamemode survival`)
    await command(`/clear @s`)
    await bot.waitForChunksToLoad()
    await waitUntil(async () => /Successfully filled|No blocks were filled/.test(await command(`/fill ${x - 4} ${y - 1} ${z - 4} ${x + 4} ${y - 1} ${z + 4} minecraft:stone`, /Successfully filled|not loaded|No blocks were filled/, 5000)), { label: 'arena floor filled (spawn chunks loaded)', timeoutMs: 20000, intervalMs: 500 })
    await command(`/fill ${x - 4} ${y} ${z - 4} ${x + 4} ${y + 4} ${z + 4} minecraft:air`)
    await command(`/tp @s ${x + 0.5} ${y} ${z + 0.5} 0 0`)
    await waitUntil(() => Math.abs(bot.entity.position.y - y) < 0.01 && at(0, -1, 0)?.name === 'stone', { label: 'standing on the arena floor', timeoutMs: 15000 })
  })
  await check('move', async () => {
    const start = bot.entity.position.clone()
    await bot.look(Math.PI, 0, true)
    bot.setControlState('forward', true)
    await bot.waitForTicks(10)
    bot.setControlState('forward', false)
    await bot.waitForTicks(20)
    const moved = bot.entity.position.distanceTo(start)
    expect(moved > 1, 'player walked more than one block', { moved })
    await command(`/tp @s ${x + 0.5} ${y} ${z + 0.5} 0 0`)
    await waitUntil(() => bot.entity.position.distanceTo(start) < 0.1, { label: 'back at the start', timeoutMs: 5000 })
    return { moved }
  })
  await check('break-block', async () => {
    await command(`/setblock ${x + 1} ${y} ${z} minecraft:dirt`)
    await waitUntil(() => at(1, 0, 0)?.name === 'dirt', { label: 'dirt visible', timeoutMs: 5000 })
    await bot.dig(at(1, 0, 0), true)
    await waitUntil(() => at(1, 0, 0)?.name === 'air', { label: 'dirt broken', timeoutMs: 5000 })
    const drop = await waitUntil(() => Object.values(bot.entities).find(entity => entity.name === 'item' && entity.getDroppedItem?.()?.name === 'dirt'), { label: 'dirt drop visible', timeoutMs: 5000 })
    await bot.waitForTicks(15)
    await command(`/tp @s ${drop.position.x} ${y} ${drop.position.z}`)
    await waitUntil(() => bot.inventory.items().some(item => item.name === 'dirt'), { label: 'dirt drop picked up', timeoutMs: 8000 })
    await command(`/tp @s ${x + 0.5} ${y} ${z + 0.5} 0 0`)
    await waitUntil(() => bot.entity.position.distanceTo(new Vec3(x + 0.5, y, z + 0.5)) < 0.1, { label: 'back at the start', timeoutMs: 5000 })
  }, () => ({ player: bot.entity.position, inventory: bot.inventory.items().map(item => `${item.name}x${item.count}`),
    drops: Object.values(bot.entities).filter(entity => entity.name === 'item').map(entity => ({ position: entity.position, item: entity.getDroppedItem?.()?.name })) }))
  await check('place-block', async () => {
    await bot.equip(bot.inventory.items().find(item => item.name === 'dirt'), 'hand')
    await bot.placeBlock(at(1, -1, 0), new Vec3(0, 1, 0))
    await waitUntil(() => at(1, 0, 0)?.name === 'dirt', { label: 'dirt placed', timeoutMs: 5000 })
    await command(`/execute if block ${x + 1} ${y} ${z} minecraft:dirt run say placed-${token}`, `placed-${token}`, 5000)
  })
  await check('container-items', async () => {
    await command(`/setblock ${x} ${y} ${z - 2} minecraft:chest`)
    await waitUntil(() => at(0, 0, -2)?.name === 'chest', { label: 'chest visible', timeoutMs: 5000 })
    await command(`/item replace block ${x} ${y} ${z - 2} container.0 with minecraft:diamond 3`)
    const window = await bot.openContainer(at(0, 0, -2))
    try {
      await waitUntil(() => window.slots[0]?.name === 'diamond' && window.slots[0].count === 3, { label: 'chest slot shows 3 diamonds', timeoutMs: 5000 })
      await bot.clickWindow(0, 0, 1)
      await waitUntil(() => !window.slots[0] && window.slots.slice(window.inventoryStart).some(item => item?.name === 'diamond' && item.count === 3), { label: 'diamonds shift-clicked into the player inventory', timeoutMs: 5000 })
    } finally { bot.closeWindow(window) }
    await command(`/execute if block ${x} ${y} ${z - 2} minecraft:chest{Items:[]} run say emptied-${token}`, `emptied-${token}`, 5000)
  })
  await check('attack-entity', async () => {
    await command(`/summon minecraft:zombie ${x + 0.5} ${y} ${z + 2.5} {NoAI:1b,PersistenceRequired:1b,Silent:1b}`)
    const zombie = await waitUntil(() => Object.values(bot.entities).find(entity => entity.name === 'zombie' && entity.position.distanceTo(bot.entity.position) < 4), { label: 'zombie visible', timeoutMs: 5000 })
    await bot.lookAt(zombie.position.offset(0, 1.6, 0), true)
    const hurt = context.waitForEvent('entityHurt', entity => entity.id === zombie.id, 5000)
    bot.attack(zombie)
    await hurt
    await command(`/kill @e[type=minecraft:zombie,distance=..10]`)
  })
  await check('action-bar', async () => {
    await command(`/title @s actionbar {"text":"ab-${token}"}`)
    await waitUntil(() => raw.action_bar.some(packet => packet.includes(`ab-${token}`)), { label: 'action bar packet', timeoutMs: 5000 })
  })
  await check('title', async () => {
    await command(`/title @s title {"text":"ti-${token}"}`)
    await waitUntil(() => raw.set_title_text.some(packet => packet.includes(`ti-${token}`)), { label: 'title packet', timeoutMs: 5000 })
  })
  await check('boss-bar', async () => {
    await command(`/bossbar add adaptqa:reach {"text":"bb-${token}"}`)
    await command(`/bossbar set adaptqa:reach players @s`)
    await waitUntil(() => raw.boss_bar.some(packet => packet.includes(`bb-${token}`)), { label: 'boss bar packet', timeoutMs: 5000 })
    await command(`/bossbar remove adaptqa:reach`)
  })
  await check('scoreboard', async () => {
    await command(`/scoreboard objectives add qa${token} dummy`)
    await command(`/scoreboard objectives setdisplay sidebar qa${token}`)
    await command(`/scoreboard players set @s qa${token} 7`)
    await waitUntil(() => raw.scoreboard_objective.some(packet => packet.includes(`qa${token}`)) && raw.scoreboard_score.some(packet => packet.includes(`qa${token}`) && packet.includes('7')), { label: 'objective and score packets', timeoutMs: 5000 })
    await command(`/scoreboard objectives remove qa${token}`)
  })
  await check('advancement', async () => {
    await command(`/advancement revoke @s only minecraft:story/root`)
    raw.advancements.length = 0
    await command(`/advancement grant @s only minecraft:story/root`)
    await waitUntil(() => raw.advancements.some(packet => packet.includes('story/root')), { label: 'advancement progress packet', timeoutMs: 5000 })
  })
  await check('display-entity', async () => {
    await command(`/summon minecraft:block_display ${x + 2} ${y + 1} ${z + 2} {Tags:["adaptqa_reach"],block_state:{Name:"minecraft:gold_block"}}`)
    const display = await waitUntil(() => Object.values(bot.entities).find(entity => entity.name === 'block_display'), { label: 'block display visible', timeoutMs: 5000 })
    await command(`/kill @e[type=minecraft:block_display,tag=adaptqa_reach]`)
    return { id: display.id, metadataEntries: display.metadata?.filter(entry => entry !== undefined).length }
  })
  await check('particles', async () => {
    particles.length = 0
    await command(`/particle minecraft:crit ${x + 0.5} ${y + 1} ${z + 0.5} 0.3 0.3 0.3 0.08 6 force @s`)
    const dust = compareVersions(version, '1.20.5') < 0 ? 'minecraft:dust 1 0 0 1' : 'minecraft:dust{color:[1.0,0.0,0.0],scale:1.0}'
    await command(`/particle ${dust} ${x + 0.5} ${y + 1} ${z + 0.5} 0 0 0 0 1 force @s`)
    await waitUntil(() => particles.some(particle => particle.name === 'crit' && particle.count === 6 && Math.abs(particle.offset.x - 0.3) < 1e-5 && Math.abs(particle.speed - 0.08) < 1e-5)
      && particles.some(particle => particle.name === 'dust' && particle.dust?.rgb === 0xff0000), { label: 'crit and red dust particle packets', timeoutMs: 5000 })
    const errors = particles.filter(particle => particle.error)
    expect(!errors.length, 'particle packets decode', errors)
  }, () => ({ decoded: particles.slice(-4), parsed: raw.world_particles.slice(-4) }))
  await check('sounds', async () => {
    sounds.length = 0
    await command(`/playsound minecraft:entity.experience_orb.pickup master @s ${x} ${y} ${z} 0.7 1.5`)
    await waitUntil(() => sounds.some(sound => sound.name === 'entity.experience_orb.pickup' && Math.abs(sound.volume - 0.7) < 1e-5 && Math.abs(sound.pitch - 1.5) < 1e-5), { label: 'sound packet', timeoutMs: 5000 })
  })
  await check('attributes', async () => {
    await command(`/attribute @s minecraft:${serverId('attribute', 'movement_speed', version)} base set 0.15`)
    await waitUntil(() => Math.abs((bot.entity.attributes?.['minecraft:movement_speed']?.value ?? 0) - 0.15) < 1e-6, { label: 'movement speed attribute update', timeoutMs: 5000 })
    await command(`/attribute @s minecraft:${serverId('attribute', 'movement_speed', version)} base set 0.1`)
  })
  stopParticles()
  stopAttributes()
  const failed = results.filter(entry => entry.result === 'fail')
  report.failed = failed.length
  expect(!failed.length, `Reach checks failed: ${failed.map(entry => entry.name).join(', ')}`, failed)
}

async function load(context, rounds, count, staggerMs) {
  const { bot, waitUntil, expect } = context
  const mineflayer = createRequire(process.argv[1])('mineflayer')
  const port = bot._client.socket.remotePort
  const stats = { rounds, bots: count, staggerMs, logins: 0, spawned: 0, chatted: 0, failures: [], spawnMs: [] }
  context.report.reach = { load: stats }
  for (let round = 1; round <= rounds; round++) {
    const bots = await Promise.all(Array.from({ length: count }, async (_, index) => {
      const username = `LR${round}x${index}`
      await context.sleep(index * staggerMs)
      const started = Date.now()
      stats.logins++
      const client = mineflayer.createBot({ host: '127.0.0.1', port, username, auth: 'offline', version: bot.version, hideErrors: true, logErrors: false })
      client.on('error', () => {})
      try {
        await new Promise((resolve, reject) => {
          const timer = setTimeout(() => reject(new Error('spawn timed out after 60 s')), 60000)
          client.once('spawn', () => { clearTimeout(timer); resolve() })
          client.once('end', reason => { clearTimeout(timer); reject(new Error(`ended before spawn: ${reason}`)) })
        })
        stats.spawned++
        stats.spawnMs.push(Date.now() - started)
        const text = `lr-${round}-${index}`
        const heard = context.waitForMessage(text, 15000)
        client.chat(text)
        await heard
        stats.chatted++
      } catch (error) {
        stats.failures.push({ round, username, error: error.message })
        process.stderr.write(`[LOAD] round ${round} ${username}: ${error.message}\n`)
      }
      return client
    }))
    await Promise.all(bots.map(client => new Promise(resolve => {
      const timer = setTimeout(resolve, 5000)
      client.once('end', () => { clearTimeout(timer); resolve() })
      client.quit()
    })))
    await waitUntil(() => !Object.keys(bot.players).some(name => name.startsWith(`LR${round}x`)), { label: `round ${round} players left`, timeoutMs: 15000 })
  }
  const sorted = [...stats.spawnMs].sort((a, b) => a - b)
  stats.spawnMedianMs = sorted[Math.floor(sorted.length / 2)]
  stats.spawnMaxMs = sorted.at(-1)
  process.stderr.write(`[LOAD] logins=${stats.logins} spawned=${stats.spawned} chatted=${stats.chatted} failures=${stats.failures.length} spawnMedianMs=${stats.spawnMedianMs} spawnMaxMs=${stats.spawnMaxMs}\n`)
  process.stderr.write(`[${stats.failures.length ? 'FAIL' : 'PASS'}] reach-load\n`)
  expect(!stats.failures.length, 'Every load login spawned and chatted', stats.failures)
}

export default {
  name: 'adapt-server-reach',
  description: 'Bare-server reach: join, move, blocks, containers, combat, chat, HUD packets, display entities, particles, sounds, attributes; or a join/leave load loop.',
  async run(context) {
    const mode = context.options.command || 'basics'
    if (mode === 'basics') return basics(context)
    const match = /^load:(\d+):(\d+)(?::(\d+))?$/.exec(mode)
    if (!match) throw new Error(`Unknown reach mode: ${mode}`)
    return load(context, Number(match[1]), Number(match[2]), Number(match[3] ?? 0))
  },
}
