import test from 'node:test'
import assert from 'node:assert/strict'
import { EventEmitter } from 'node:events'
import { createParticlePacketDecoder, observeParticlePackets, isFeedbackReady } from './feedback.mjs'

test('feedback readiness requires recovered TPS and the current production density', () => {
  assert.equal(isFeedbackReady({ tps: 20, feedbackTransitionDensity: 0 }), false)
  assert.equal(isFeedbackReady({ tps: 19.3, feedbackTransitionDensity: 0.8 }), false)
  assert.equal(isFeedbackReady({ tps: 19, feedbackTransitionDensity: 1 }), false)
  assert.equal(isFeedbackReady({ tps: 20 }), false)
  assert.equal(isFeedbackReady({ tps: 19.25, feedbackTransitionDensity: 1 }), true)
})

function registry() {
  return {
    version: { minecraftVersion: '26.1.2' },
    protocol: {
      play: { toClient: { types: {
        packet: ['container', [{ name: 'name', type: ['mapper', { type: 'varint', mappings: { '0x2f': 'world_particles', '0x01': 'other' } }] }]],
        packet_world_particles: ['container', [
          ['longDistance', 'bool'], ['alwaysShow', 'bool'], ['x', 'f64'], ['y', 'f64'], ['z', 'f64'],
          ['offsetX', 'f32'], ['offsetY', 'f32'], ['offsetZ', 'f32'], ['velocityOffset', 'f32'], ['amount', 'i32'], ['particle', 'Particle'],
        ].map(([name, type]) => ({ name, type }))],
      } } },
      types: { Particle: ['container', [
        { name: 'type', type: ['mapper', { type: 'varint', mappings: { 4: 'cloud', 14: 'dust', 130: 'block' } }] },
        { name: 'data', type: ['switch', { compareTo: 'type', fields: {
          dust: ['container', ['red', 'green', 'blue', 'scale'].map(name => ({ name, type: 'f32' }))], block: 'varint',
        } }] },
      ]] },
    },
  }
}

function packet(particleId, payload = Buffer.alloc(0)) {
  const id = particleId < 128 ? Buffer.from([particleId]) : Buffer.from([(particleId & 127) | 128, particleId >>> 7])
  const header = Buffer.alloc(47)
  header[0] = 0x2f
  header[1] = 1
  header.writeDoubleBE(3.5, 3)
  header.writeDoubleBE(100.25, 11)
  header.writeDoubleBE(-2.5, 19)
  header.writeFloatBE(0.2, 27)
  header.writeFloatBE(0.3, 31)
  header.writeFloatBE(0.4, 35)
  header.writeFloatBE(0.05, 39)
  header.writeInt32BE(12, 43)
  return Buffer.concat([header, id, payload])
}

function dustPayload() {
  const data = Buffer.alloc(8)
  data.writeUInt32BE(0x4080ff, 0)
  data.writeFloatBE(1.25, 4)
  return data
}

test('dust observation reads packed RGB and scale from the complete server packet', () => {
  const decode = createParticlePacketDecoder(registry())
  const buffer = packet(14, dustPayload())
  const result = decode(buffer)
  assert.equal(result.name, 'dust')
  assert.equal(result.count, 12)
  assert.deepEqual(result.dust, { rgb: 0x4080ff, red: 64, green: 128, blue: 255, scale: 1.25 })
  assert.deepEqual(result.position, { x: 3.5, y: 100.25, z: -2.5 })
  assert.equal(result.packetHex, buffer.toString('hex'))
  assert.equal(result.byteLength, 56)
  assert.equal(result.payloadDecoded, true)
})

test('particle names and packet IDs come from the negotiated registry', () => {
  const data = registry()
  data.protocol.play.toClient.types.packet[1][0].type[1].mappings = { '0x30': 'world_particles' }
  const decode = createParticlePacketDecoder(data)
  const cloud = packet(4)
  assert.equal(decode(cloud), null)
  cloud[0] = 0x30
  assert.equal(decode(cloud).name, 'cloud')
  const block = packet(130, Buffer.from([0x81, 0x01]))
  block[0] = 0x30
  assert.equal(decode(block).name, 'block')
  assert.equal(decode(block).registryIndex, 130)
})

test('malformed particle packets cannot become positive feedback evidence', () => {
  const decode = createParticlePacketDecoder(registry())
  const dust = packet(14, dustPayload())
  for (let length = 1; length < dust.length; length++) assert.throws(() => decode(dust.subarray(0, length)))
  assert.throws(() => decode(Buffer.concat([dust, Buffer.from([0])])), /payload length/)
  assert.throws(() => decode(packet(14, Buffer.alloc(16))), /payload length/)
  assert.throws(() => decode(packet(99)), /Unknown particle/)
  assert.throws(() => decode(packet(130, Buffer.from([0x80]))), /Truncated/)
  assert.throws(() => decode(packet(4, Buffer.from([1]))), /payload length/)
  const negativeCount = packet(4)
  negativeCount.writeInt32BE(-1, 43)
  assert.throws(() => decode(negativeCount), /count/)
  const invalidCoordinates = packet(4)
  invalidCoordinates.writeDoubleBE(Number.NaN, 3)
  assert.throws(() => decode(invalidCoordinates), /coordinates/)
  const invalidScale = dustPayload()
  invalidScale.writeFloatBE(Number.NaN, 4)
  assert.throws(() => decode(packet(14, invalidScale)), /scale/)
  const invalidFlags = packet(4)
  invalidFlags[1] = 2
  assert.throws(() => decode(invalidFlags), /flags/)
})

test('decoder rejects other protocol versions and changed fixed headers', () => {
  assert.throws(() => createParticlePacketDecoder(registry(), '1.21.11'), /requires Minecraft/)
  assert.throws(() => createParticlePacketDecoder(registry(), '26.2'), /requires Minecraft/)
  const changed = registry()
  changed.protocol.play.toClient.types.packet_world_particles[1].shift()
  assert.throws(() => createParticlePacketDecoder(changed), /header/)
})

test('observer reads decompressor packets independently of deserialization and cleans up only its listener', () => {
  const stream = new EventEmitter()
  const unrelated = () => {}
  stream.on('data', unrelated)
  const bot = { registry: registry(), version: '26.1.2', _client: { state: 'play', decompressor: stream, splitter: new EventEmitter() } }
  const observed = []
  const failures = []
  const detach = observeParticlePackets(bot, particle => observed.push(particle), error => failures.push(error))
  stream.emit('data', packet(14, dustPayload()))
  stream.emit('data', packet(14))
  stream.emit('data', Buffer.from([1]))
  assert.equal(observed.length, 1)
  assert.equal(failures.length, 1)
  bot._client.state = 'configuration'
  stream.emit('data', packet(14, dustPayload()))
  assert.equal(observed.length, 1)
  detach()
  assert.deepEqual(stream.listeners('data'), [unrelated])
  stream.emit('data', packet(4))
  assert.equal(observed.length, 1)
})

test('observer also handles complete uncompressed frames from the splitter', () => {
  const splitter = new EventEmitter()
  const bot = { registry: registry(), version: '26.1', _client: { state: 'play', decompressor: null, splitter } }
  const observed = []
  const detach = observeParticlePackets(bot, particle => observed.push(particle))
  splitter.emit('data', packet(4))
  assert.equal(observed[0].name, 'cloud')
  detach()
  assert.equal(splitter.listenerCount('data'), 0)
})
