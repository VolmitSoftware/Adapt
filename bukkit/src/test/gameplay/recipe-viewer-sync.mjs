import { createRequire } from 'node:module'
import { randomBytes } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import { join } from 'node:path'
import { fixtureJson } from './fixture-json.mjs'

const TIMEOUT = 20000
const COMPLETED = 'Deferred recipe registration completed.'
const NATIVE = new Set(['1.20.1', '1.21.1'])

async function serverLog(context) {
    try {
        return await readFile(context.report.server.logPath, 'utf8')
    } catch {
        return ''
    }
}

async function serverPort(context) {
    const properties = await readFile(join(context.report.server.directory, 'server.properties'), 'utf8')
    return Number(/^server-port=(\d+)$/m.exec(properties)[1])
}

function varint(buffer, at) {
    let value = 0
    let shift = 0
    let byte
    do {
        byte = buffer[at++]
        value |= (byte & 0x7f) << shift
        shift += 7
    } while (byte & 0x80)
    return value
}

/**
 * Lists the adapt: recipe entries of a raw declare_recipes packet with their serializer. 1.20.1 writes each entry as
 * serializer id string then recipe id string; 1.21.1 writes the recipe id string then the serializer as a registry varint.
 * Only length-prefixed ids in that position count, so item data inside results cannot be mistaken for an entry.
 */
function adaptEntries(buffer, version, serializerNames) {
    const text = buffer.toString('latin1')
    const entries = {}
    const pattern = /adapt:[a-z0-9_.\/-]+/g
    let match
    while ((match = pattern.exec(text))) {
        const at = match.index
        const length = match[0].length
        if (buffer[at - 1] !== length) continue
        if (version === '1.20.1') {
            const serializer = /minecraft:[a-z_]+$/.exec(text.slice(Math.max(0, at - 64), at - 1))
            if (!serializer || buffer[at - 2 - serializer[0].length] !== serializer[0].length) continue
            entries[match[0]] = serializer[0]
        } else {
            entries[match[0]] = serializerNames[varint(buffer, at + length)] ?? 'unknown'
        }
    }
    return entries
}

function summarize(buffer, version, serializerNames) {
    const entries = adaptEntries(buffer, version, serializerNames)
    const serializers = {}
    for (const serializer of Object.values(entries)) serializers[serializer] = (serializers[serializer] ?? 0) + 1
    return { bytes: buffer.length, adapt: Object.keys(entries).length, serializers, sample: Object.keys(entries).slice(0, 3) }
}

/**
 * Joins a plain client of the server's own protocol (no Adapt, no recipe viewer) and keeps every raw recipe definition
 * packet it receives: what JEI, REI and EMI read from the client recipe manager before 1.21.2. The raw bytes are read
 * because minecraft-protocol's 1.21.1 item component definitions cannot decode every vanilla result.
 */
async function nativeJoin(context, version, port, name) {
    const require = createRequire(process.argv[1])
    const protocol = require('minecraft-protocol')
    const data = require('minecraft-data')(version)
    const packetNames = data.protocol.play.toClient.types.packet[1][0].type[1].mappings
    const recipePacket = Number(Object.keys(packetNames).find(id => packetNames[id] === 'declare_recipes'))
    const entry = data.protocol.play.toClient.types.packet_declare_recipes[1][0].type[1].type[1]
    const serializerNames = version === '1.20.1' ? {} : entry[1].type[1].mappings
    const client = protocol.createClient({ host: '127.0.0.1', port, username: name, version, auth: 'offline' })
    const packets = []
    let spawned = false
    let failure
    const parser = Object.getPrototypeOf(client.deserializer)
    const parse = parser.parsePacketBuffer
    parser.parsePacketBuffer = function (buffer) {
        if (this === client.deserializer && client.state === 'play' && buffer[0] === recipePacket) {
            packets.push({ at: Date.now(), summary: summarize(Buffer.from(buffer), version, serializerNames) })
        }
        return parse.call(this, buffer)
    }
    client.on('login', () => { spawned = true })
    client.on('kick_disconnect', packet => { failure = new Error(`kicked: ${JSON.stringify(packet)}`) })
    client.on('end', reason => { if (!failure) failure = new Error(`connection ended: ${reason}`) })
    const close = () => {
        parser.parsePacketBuffer = parse
        client.removeAllListeners('end')
        client.end()
    }
    try {
        await context.waitUntil(() => {
            if (failure) throw failure
            return spawned && packets.length > 0
        }, { label: `${name} joins and receives the recipe packet`, timeoutMs: TIMEOUT })
    } catch (error) {
        close()
        throw error
    }
    return { packets, failure: () => failure, close }
}

async function legacy(context, version, evidence) {
    const port = await serverPort(context)
    const completedAtJoin = (await serverLog(context)).includes(COMPLETED)
    const early = await nativeJoin(context, version, port, `RVa${randomBytes(3).toString('hex')}`)
    try {
        evidence.early = { completedAtJoin, join: early.packets[0].summary }
        await context.waitUntil(async () => (await serverLog(context)).includes(COMPLETED),
            { label: 'Adapt finishes registering its recipes', timeoutMs: 120000 })
        await context.waitUntil(() => {
            if (early.failure()) throw early.failure()
            return early.packets.at(-1).summary.adapt > 0
        }, { label: 'A client that joined first holds Adapt recipes once registration completed', timeoutMs: TIMEOUT })
        evidence.early.latest = early.packets.at(-1).summary
        const before = early.packets.length
        context.bot.chat('/reload')
        await context.waitUntil(() => {
            if (early.failure()) throw early.failure()
            return early.packets.length > before
        }, { label: 'A datapack reload sends the recipe packet again', timeoutMs: 60000 })
        evidence.early.afterReload = early.packets.at(-1).summary
        evidence.early.packets = early.packets.length
        const late = await nativeJoin(context, version, port, `RVb${randomBytes(3).toString('hex')}`)
        evidence.late = late.packets[0].summary
        late.close()
    } finally {
        early.close()
    }
    for (const summary of [evidence.early.latest, evidence.early.afterReload, evidence.late]) {
        context.expect(summary.adapt > 0, 'The recipe packet carries adapt: recipes', summary)
        context.expect(Object.keys(summary.serializers).every(type => type.startsWith('minecraft:')),
            'Every adapt: recipe uses a vanilla serializer a client without Adapt decodes', summary.serializers)
    }
    context.expect(evidence.late.adapt === evidence.early.latest.adapt && evidence.early.afterReload.adapt === evidence.early.latest.adapt,
        'Early joiners, late joiners and a reload carry the same Adapt recipe count', evidence)
}

async function modern(context, evidence) {
    await context.waitUntil(async () => (await serverLog(context)).includes(COMPLETED),
        { label: 'Adapt finishes registering its recipes', timeoutMs: 120000 })
    evidence.loaderSync = await fixtureJson(context, '/adaptqa-recipe-sync', 'RECIPE_SYNC', 15000)
    context.bot.chat('/reload')
    await context.waitUntil(async () => {
        evidence.afterReload = await fixtureJson(context, '/adaptqa-recipe-sync', 'RECIPE_SYNC', 15000)
        return evidence.afterReload.syncEvents > evidence.loaderSync.syncEvents
    }, { label: 'A datapack reload runs the loader recipe sync again', timeoutMs: 60000, intervalMs: 1000 })
    for (const sync of [evidence.loaderSync, evidence.afterReload]) {
        context.expect(sync.adapt > 0, 'The loader recipe sync payload carries adapt: recipes', sync)
        context.expect(sync.adapt === sync.registered, 'Every registered Adapt recipe is in the sync payload', sync)
        context.expect(Object.keys(sync.serializers).every(type => type.startsWith('minecraft:')),
            'Every adapt: recipe uses a vanilla serializer a client without Adapt decodes', sync.serializers)
    }
}

export default {
    name: 'adapt-recipe-viewer-sync',
    description: 'Records the recipe data a recipe viewer on a client without Adapt receives: vanilla recipe definitions before 1.21.2, the loader recipe sync payload after.',
    async run(context) {
        context.bot.physicsEnabled = false
        const version = process.env.ADAPT_QA_MINECRAFT_VERSION
        const evidence = context.report.recipeViewerSync = { version }
        if (NATIVE.has(version)) {
            await context.step('vanilla recipe definitions', () => legacy(context, version, evidence))
        } else {
            await context.step('loader recipe sync', () => modern(context, evidence))
        }
    }
}
