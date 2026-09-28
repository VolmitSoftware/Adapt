function varInt(buffer, cursor) {
    let value = 0
    for (let index = 0; index < 5; index++) {
        if (cursor.offset >= buffer.length) throw new Error('Truncated attribute packet')
        const byte = buffer[cursor.offset++]
        if (index === 4 && (byte & 0xf0)) throw new Error('Attribute VarInt exceeds 32 bits')
        value += (byte & 0x7f) * 2 ** (index * 7)
        if (!(byte & 0x80)) return value
    }
    throw new Error('Unterminated attribute VarInt')
}

function protocol(bot) {
    if (!/^26\.1(?:\.\d+)?$/.test(bot.version ?? '')) throw new Error('Attribute observation requires Minecraft 26.1.x')
    const types = bot.registry.protocol.play.toClient.types
    const packets = types.packet[1].find(field => field.name === 'name').type[1].mappings
    const id = Object.entries(packets).find(([, name]) => name === 'entity_update_attributes')?.[0]
    const properties = types.packet_entity_update_attributes[1].find(field => field.name === 'properties')
    const fields = properties.type[1].type[1]
    const keys = fields.find(field => field.name === 'key').type[1].mappings
    if (id === undefined || !keys) throw new Error('Unsupported attribute packet schema')
    return { id: Number(id), keys }
}

export function createAttributePacketDecoder(bot, registry) {
    const { id } = protocol(bot)
    if (Object.keys(registry).length === 0 || Object.entries(registry).some(([key, name]) => !/^\d+$/.test(key) || !/^[a-z0-9_.-]+:[a-z0-9_/.-]+$/.test(name))) {
        throw new Error('Invalid server attribute registry')
    }
    return buffer => {
        const cursor = { offset: 0 }
        if (varInt(buffer, cursor) !== id) return null
        const entityId = varInt(buffer, cursor)
        const count = varInt(buffer, cursor)
        if (count > 1024) throw new Error('Invalid attribute count')
        const properties = []
        for (let index = 0; index < count; index++) {
            const registryId = varInt(buffer, cursor)
            const key = registry[registryId]
            if (!key) throw new Error(`Unknown server attribute ID ${registryId}`)
            const value = buffer.readDoubleBE(cursor.offset)
            cursor.offset += 8
            const modifierCount = varInt(buffer, cursor)
            if (!Number.isFinite(value) || modifierCount > 1024) throw new Error('Invalid attribute value or modifier count')
            const modifiers = []
            for (let modifierIndex = 0; modifierIndex < modifierCount; modifierIndex++) {
                const length = varInt(buffer, cursor)
                if (length < 1 || length > 32767 || cursor.offset + length + 9 > buffer.length) throw new Error('Invalid attribute modifier identifier')
                const uuid = buffer.toString('utf8', cursor.offset, cursor.offset + length)
                cursor.offset += length
                const amount = buffer.readDoubleBE(cursor.offset)
                cursor.offset += 8
                const operation = buffer[cursor.offset++]
                if (!Number.isFinite(amount) || operation > 2) throw new Error('Invalid attribute modifier')
                modifiers.push({ uuid, amount, operation })
            }
            properties.push({ registryId, key, value, modifiers })
        }
        if (cursor.offset !== buffer.length) throw new Error('Unexpected attribute packet suffix')
        return { entityId, properties }
    }
}

export function synchronizePlayerAttributes(bot, registry, onError = error => bot.emit('error', error)) {
    const { keys } = protocol(bot)
    const decode = createAttributePacketDecoder(bot, registry)
    const reverse = new Map(Object.entries(keys).map(([id, name]) => [name, id]))
    for (const entity of Object.values(bot.entities)) {
        if (!entity.attributes) continue
        const canonical = {}
        for (const [key, value] of Object.entries(entity.attributes)) {
            const name = registry[reverse.get(key)]
            if (name) canonical[name] = value
        }
        entity.attributes = canonical
    }
    const previousSprint = bot.physics.sprintingUUID
    bot.physics.sprintingUUID = 'minecraft:sprinting'
    const stream = bot._client.decompressor ?? bot._client.splitter
    if (!stream?.on || !stream?.off) throw new Error('No decompressed attribute packet stream available')
    const receive = buffer => {
        if (bot._client.state !== 'play') return
        try {
            const packet = decode(buffer)
            if (!packet) return
            const entity = bot.entities[packet.entityId]
            if (!entity) return
            entity.attributes ??= {}
            for (const property of packet.properties) {
                delete entity.attributes[keys[property.registryId]]
                entity.attributes[property.key] = { value: property.value, modifiers: property.modifiers }
            }
        } catch (error) { onError(error) }
    }
    stream.on('data', receive)
    return () => {
        stream.off('data', receive)
        bot.physics.sprintingUUID = previousSprint
    }
}
