import { randomBytes } from 'node:crypto'
import { gunzipSync } from 'node:zlib'
import { frameAssembler } from './client-ui-frames.mjs'

const CHANNEL = 'adapt:ui'
const ITEMS = 16
const SCREENS = 1
const S2C = { accept: 1, catalog: 2, player: 3, customItems: 14 }
const EXPECTED = ['adapt:knowledge_orb', 'adapt:experience_orb']

function varint(value) {
  const bytes = []
  let remaining = value
  while (remaining >= 0x80) {
    bytes.push((remaining & 0x7f) | 0x80)
    remaining >>>= 7
  }
  bytes.push(remaining)
  return Buffer.from(bytes)
}

class Reader {
  constructor(buffer) {
    this.buffer = buffer
    this.at = 0
  }

  u8() {
    return this.buffer[this.at++]
  }

  varint() {
    let value = 0
    let shift = 0
    let byte
    do {
      byte = this.u8()
      value += (byte & 0x7f) * 2 ** shift
      shift += 7
    } while (byte & 0x80)
    return value
  }

  raw(length) {
    const slice = this.buffer.subarray(this.at, this.at + length)
    this.at += length
    return slice
  }

  string() {
    return this.raw(this.varint()).toString('utf8')
  }

  list(element) {
    const count = this.varint()
    return Array.from({ length: count }, () => element())
  }
}

/** Decodes a CustomItems frame (opcode 14) as FrameCodec writes it. */
function customItems(frame) {
  const reader = new Reader(frame)
  reader.u8()
  return reader.list(() => {
    const id = reader.string()
    const baseItemId = reader.string()
    const name = reader.string()
    const lore = reader.list(() => reader.string())
    const itemModelId = reader.u8() ? reader.string() : null
    const customModelData = reader.raw(4).readInt32BE(0)
    const components = Buffer.from(reader.raw(reader.varint()))
    return { id, baseItemId, name, lore, itemModelId, customModelData, components }
  })
}

const hello = mask => Buffer.concat([Buffer.from([32]), varint(1), varint(mask)])

/**
 * A bot that negotiates ITEMS gets the recipe viewer items after Accept, Catalog and Player, as CustomItems chunks ended
 * by an empty frame; every item is gzip NBT naming its base item. A bot that negotiates without ITEMS and a plain bot
 * without the channel get no CustomItems.
 */
export default {
  name: 'adapt-client-ui-items',
  description: 'Verify that a client negotiating the items capability receives the Adapt recipe viewer items.',
  async run(context) {
    const suffix = randomBytes(3).toString('hex')
    const actor = await context.connectActor(`AQAci${suffix}`)
    const screensOnly = await context.connectActor(`AQAcs${suffix}`)
    const plain = await context.connectActor(`AQAcp${suffix}`)
    const evidence = context.report.clientUiItems = {}
    const listen = bot => {
      const frames = []
      bot._client.registerChannel(CHANNEL, ['restBuffer', []], true)
      const assembler = frameAssembler()
      bot._client.on(CHANNEL, data => {
        const frame = assembler.push(data)
        if (frame) frames.push(frame)
      })
      return frames
    }
    const frames = listen(actor.bot)
    const screensFrames = listen(screensOnly.bot)
    const plainFrames = []
    plain.bot._client.on('custom_payload', packet => {
      if (packet.channel === CHANNEL) plainFrames.push(packet.data)
    })

    actor.bot._client.writeChannel(CHANNEL, hello(ITEMS))
    screensOnly.bot._client.writeChannel(CHANNEL, hello(SCREENS))
    const terminated = () => frames.some(frame => frame[0] === S2C.customItems && frame.length > 1 && frame[1] === 0)
    await context.waitUntil(terminated, { label: 'CustomItems list ends with an empty frame', timeoutMs: 20000, intervalMs: 50 })
    await context.waitUntil(() => screensFrames.some(frame => frame[0] === S2C.player),
      { label: 'Screens-only client gets its Player view', timeoutMs: 20000, intervalMs: 50 })

    const opcodes = frames.map(frame => frame[0])
    evidence.opcodes = opcodes
    const firstItems = opcodes.indexOf(S2C.customItems)
    context.expect(opcodes.slice(0, 3).join(',') === '1,2,3' && firstItems === 3,
      'Accept, Catalog and Player come before the first CustomItems frame', opcodes)
    const chunks = frames.filter(frame => frame[0] === S2C.customItems).map(customItems)
    evidence.chunkSizes = chunks.map(chunk => chunk.length)
    context.expect(chunks.at(-1).length === 0 && chunks.slice(0, -1).every(chunk => chunk.length > 0),
      'Every chunk but the terminating one carries items', evidence.chunkSizes)
    const items = chunks.flat()
    evidence.ids = items.map(item => item.id)
    evidence.items = items.map(item => ({ id: item.id, base: item.baseItemId, name: item.name, bytes: item.components.length }))
    for (const id of EXPECTED) {
      context.expect(evidence.ids.includes(id), `The list contains ${id}`, evidence.ids)
    }
    context.expect(evidence.ids.some(id => !EXPECTED.includes(id)), 'The list contains Adapt recipe outputs', evidence.ids)
    context.expect(new Set(evidence.ids).size === evidence.ids.length, 'Every id appears once', evidence.ids)
    const unreadable = items.filter(item => {
      if (item.components[0] !== 0x1f || item.components[1] !== 0x8b) return true
      const nbt = gunzipSync(item.components)
      return !nbt.includes(Buffer.from(item.baseItemId)) && !nbt.includes(Buffer.from(item.baseItemId.replace('minecraft:', '')))
    }).map(item => item.id)
    evidence.unreadable = unreadable
    context.expect(unreadable.length === 0, 'Every item is gzip NBT naming its base item', unreadable)

    evidence.screensOnlyOpcodes = screensFrames.map(frame => frame[0])
    context.expect(!evidence.screensOnlyOpcodes.includes(S2C.customItems), 'A client without ITEMS gets no CustomItems',
      evidence.screensOnlyOpcodes)
    evidence.plainFrames = plainFrames.length
    context.expect(plainFrames.length === 0, 'A client without the channel receives no adapt:ui frames')
  }
}
