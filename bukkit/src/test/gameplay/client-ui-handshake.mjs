import { randomBytes } from 'node:crypto'
import { frameAssembler } from './client-ui-frames.mjs'

const CHANNEL = 'adapt:ui'
const ADAPTATION = 'adapt:rift/blink'
const S2C = { accept: 1, catalog: 2, player: 3, update: 4, openRoute: 5, feedback: 15 }
const FEEDBACK = ['SUCCESS', 'FAILURE', 'INFO']

function varint(value) {
  const bytes = []
  let remaining = BigInt(value)
  while (remaining >= 0x80n) {
    bytes.push(Number((remaining & 0x7fn) | 0x80n))
    remaining >>= 7n
  }
  bytes.push(Number(remaining))
  return Buffer.from(bytes)
}

function readVarint(buffer, at) {
  let value = 0n
  let shift = 0n
  let byte
  do {
    byte = buffer[at++]
    value |= BigInt(byte & 0x7f) << shift
    shift += 7n
  } while (byte & 0x80)
  return { value, at }
}

function string(text) {
  const bytes = Buffer.from(text, 'utf8')
  return Buffer.concat([varint(bytes.length), bytes])
}

const hello = (version, mask) => Buffer.concat([Buffer.from([32]), varint(version), varint(mask)])
const learn = (id, level, revision) => Buffer.concat([Buffer.from([33]), string(id), varint(level), varint(revision)])

/**
 * A plain bot speaks the adapt:ui protocol by hand: it registers the channel, sends Hello, expects Accept, Catalog and
 * Player, learns an affordable adaptation and expects the PlayerUpdate and Feedback, and gets OpenRoute instead of a
 * chest from /adapt gui. A second plain bot without the channel gets nothing on it and still gets the chest.
 */
export default {
  name: 'adapt-client-ui-handshake',
  description: 'Verify the adapt:ui handshake, full sync, a learn action and its delta for a client that speaks the protocol.',
  async run(context) {
    const suffix = randomBytes(3).toString('hex')
    const actor = await context.connectActor(`AQAcu${suffix}`)
    const plain = await context.connectActor(`AQAcp${suffix}`)
    const evidence = context.report.clientUi = {}
    const frames = []
    const plainFrames = []
    const client = actor.bot._client
    client.registerChannel(CHANNEL, ['restBuffer', []], true)
    const assembler = frameAssembler()
    client.on(CHANNEL, data => {
      const frame = assembler.push(data)
      if (frame) frames.push(frame)
    })
    plain.bot._client.on('custom_payload', packet => {
      if (packet.channel === CHANNEL) plainFrames.push(packet.data)
    })
    const opcodes = () => frames.map(frame => frame[0])
    const waitFor = (opcode, label, after = 0) => context.waitUntil(
      () => frames.slice(after).some(frame => frame[0] === opcode), { label, timeoutMs: 15000, intervalMs: 50 })

    await context.command(`/adaptqa afford ${actor.bot.username} rift`, /^ADAPT_QA AFFORD rift/, 15000)
    client.writeChannel(CHANNEL, hello(1, 31))
    await waitFor(S2C.accept, 'Accept arrives')
    await waitFor(S2C.catalog, 'Catalog arrives')
    await waitFor(S2C.player, 'Player view arrives')
    evidence.handshake = opcodes()
    context.expect(evidence.handshake.slice(0, 3).join(',') === '1,2,3', 'Accept, Catalog and Player arrive in order', evidence.handshake)
    const catalog = frames.find(frame => frame[0] === S2C.catalog)
    evidence.catalogBytes = catalog.length
    evidence.fragments = assembler.fragments
    const revision = readVarint(catalog, 1).value
    evidence.revision = revision.toString()
    context.expect(catalog.includes(Buffer.from(ADAPTATION)), `Catalog lists ${ADAPTATION}`)

    evidence.status = await context.command(`/adapt client player=${actor.bot.username}`, /client UI/, 10000)
    context.expect(/uses the Adapt client UI: protocol 1, capabilities .*screens/.test(evidence.status),
      '/adapt client reports the negotiated protocol and capabilities', evidence.status)
    evidence.plainStatus = await context.command(`/adapt client player=${plain.bot.username}`, /client/, 10000)
    context.expect(evidence.plainStatus.includes('no Adapt client negotiated'), '/adapt client reports a plain client', evidence.plainStatus)

    const before = frames.length
    client.writeChannel(CHANNEL, learn(ADAPTATION, 1, revision))
    await waitFor(S2C.feedback, 'Feedback arrives', before)
    await waitFor(S2C.update, 'PlayerUpdate arrives', before)
    const reply = frames.slice(before)
    const feedback = reply.find(frame => frame[0] === S2C.feedback)
    const update = reply.find(frame => frame[0] === S2C.update)
    evidence.feedback = { kind: FEEDBACK[feedback[1]], text: feedback.subarray(readVarint(feedback, 2).at).toString('utf8') }
    evidence.updateBytes = update.length
    context.expect(evidence.feedback.kind === 'SUCCESS', 'Learning an affordable adaptation succeeds', evidence.feedback)
    context.expect(update.includes(Buffer.from(ADAPTATION)), `PlayerUpdate carries ${ADAPTATION}`)

    const stale = frames.length
    client.writeChannel(CHANNEL, learn(ADAPTATION, 2, revision + 1n))
    await waitFor(S2C.feedback, 'Stale revision feedback arrives', stale)
    const refused = frames.slice(stale).find(frame => frame[0] === S2C.feedback)
    context.expect(FEEDBACK[refused[1]] === 'FAILURE', 'A stale catalog revision is refused')

    const routed = frames.length
    await context.command(`/adapt gui target=main player=${actor.bot.username}`, /GUI|gui/, 10000).catch(() => {})
    await waitFor(S2C.openRoute, '/adapt gui sends OpenRoute to the native client', routed)
    context.expect(!actor.bot.currentWindow, '/adapt gui opens no chest for the native client')

    await context.command(`/adapt gui target=main player=${plain.bot.username}`, /GUI|gui/, 10000).catch(() => {})
    await context.waitUntil(() => plain.bot.currentWindow, { label: '/adapt gui opens the chest for a plain client', timeoutMs: 10000 })
    evidence.plainWindow = plain.bot.currentWindow?.title ?? null

    evidence.plainFrames = plainFrames.length
    context.expect(plainFrames.length === 0, 'A client without the channel receives no adapt:ui frames')
  }
}
