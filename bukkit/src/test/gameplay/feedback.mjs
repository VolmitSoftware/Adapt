export function decodeSoundPacket(registry, packet) {
  const holder = packet.sound
  const name = holder.data?.soundName ?? registry.soundsArray[holder.soundId]?.name
  return {
    name: name?.replace(/^minecraft:/, ''),
    registryIndex: holder.soundId,
    volume: packet.volume,
    pitch: packet.pitch,
  }
}

const particleHeader = [
  ['longDistance', 'bool'], ['alwaysShow', 'bool'],
  ['x', 'f64'], ['y', 'f64'], ['z', 'f64'],
  ['offsetX', 'f32'], ['offsetY', 'f32'], ['offsetZ', 'f32'],
  ['velocityOffset', 'f32'], ['amount', 'i32'], ['particle', 'Particle'],
]

function readVarInt(buffer, offset) {
  let value = 0
  for (let index = 0; index < 5; index++) {
    if (offset + index >= buffer.length) throw new Error('Truncated packet VarInt')
    const byte = buffer[offset + index]
    if (index === 4 && (byte & 0xf0) !== 0) throw new Error('Packet VarInt exceeds 32 bits')
    value += (byte & 0x7f) * (2 ** (index * 7))
    if ((byte & 0x80) === 0) {
      if (index > 0 && byte === 0) throw new Error('Noncanonical packet VarInt')
      return { value, end: offset + index + 1 }
    }
  }
  throw new Error('Unterminated packet VarInt')
}

function payloadSize(buffer, offset, type) {
  const fixed = { void: 0, bool: 1, u8: 1, i32: 4, f32: 4, f64: 8, position: 8, vec3f64: 24 }
  if (typeof type === 'string' && Object.hasOwn(fixed, type)) return fixed[type]
  if (type === 'varint') return readVarInt(buffer, offset).end - offset
  if (Array.isArray(type) && type[0] === 'container') {
    let size = 0
    for (const field of type[1]) {
      const fieldSize = payloadSize(buffer, offset + size, field.type)
      if (fieldSize === undefined) return undefined
      size += fieldSize
    }
    return size
  }
  return undefined
}

export function createParticlePacketDecoder(registry, version = registry.version?.minecraftVersion) {
  if (!/^26\.1(?:\.\d+)?$/.test(version ?? '')) {
    throw new Error(`Raw particle observation requires Minecraft 26.1.x, received ${version}`)
  }
  const types = registry.protocol?.play?.toClient?.types
  const nameField = types?.packet?.[1]?.find(field => field.name === 'name')
  const packetIds = nameField?.type?.[1]?.mappings
  const particlePacketId = Object.entries(packetIds ?? {}).find(([, name]) => name === 'world_particles')?.[0]
  const header = types?.packet_world_particles
  if (particlePacketId === undefined || header?.[0] !== 'container'
    || JSON.stringify(header[1].map(field => [field.name, field.type])) !== JSON.stringify(particleHeader)) {
    throw new Error('Unsupported world_particles packet header')
  }
  const particle = registry.protocol.types?.Particle
  const typeField = particle?.[1]?.find(field => field.name === 'type')
  const names = typeField?.type?.[1]?.mappings
  const dataField = particle?.[1]?.find(field => field.name === 'data')
  const payloads = dataField?.type?.[1]?.fields
  if (typeField?.type?.[0] !== 'mapper' || typeField.type[1].type !== 'varint'
    || !names || dataField?.type?.[0] !== 'switch' || !payloads) {
    throw new Error('Unsupported particle registry schema')
  }
  const expectedId = Number(particlePacketId)
  if (!Number.isSafeInteger(expectedId) || expectedId < 0) throw new Error('Invalid particle packet ID')

  return buffer => {
    if (!Buffer.isBuffer(buffer)) throw new TypeError('Particle observer requires a packet Buffer')
    const packetId = readVarInt(buffer, 0)
    if (packetId.value !== expectedId) return null
    const start = packetId.end
    if (buffer.length < start + 47) throw new Error('Truncated world_particles header')
    if (buffer[start] > 1 || buffer[start + 1] > 1) throw new Error('Invalid particle flags')
    const coordinates = [buffer.readDoubleBE(start + 2), buffer.readDoubleBE(start + 10), buffer.readDoubleBE(start + 18)]
    const offsets = [buffer.readFloatBE(start + 26), buffer.readFloatBE(start + 30), buffer.readFloatBE(start + 34)]
    const speed = buffer.readFloatBE(start + 38)
    const count = buffer.readInt32BE(start + 42)
    if (![...coordinates, ...offsets, speed].every(Number.isFinite) || count < 0) {
      throw new Error('Invalid particle coordinates, offsets, speed, or count')
    }
    const particleId = readVarInt(buffer, start + 46)
    const name = names[String(particleId.value)]
    if (typeof name !== 'string') throw new Error(`Unknown particle registry ID ${particleId.value}`)
    const payloadLength = buffer.length - particleId.end
    const result = {
      name, count, registryIndex: particleId.value,
      position: { x: coordinates[0], y: coordinates[1], z: coordinates[2] },
      offset: { x: offsets[0], y: offsets[1], z: offsets[2] },
      speed, longDistance: buffer[start] === 1, alwaysShow: buffer[start + 1] === 1,
      byteLength: buffer.length, packetHex: buffer.toString('hex'),
      source: 'decompressed-server-packet', payloadDecoded: false,
    }
    if (name === 'dust') {
      if (payloadLength !== 8) throw new Error('Invalid 26.1 dust payload length')
      const rgb = buffer.readUInt32BE(particleId.end)
      const scale = buffer.readFloatBE(particleId.end + 4)
      if (!Number.isFinite(scale) || scale <= 0) throw new Error('Invalid dust scale')
      result.dust = { rgb, red: (rgb >>> 16) & 255, green: (rgb >>> 8) & 255, blue: rgb & 255, scale }
      result.payloadDecoded = true
    } else {
      const type = payloads[name] ?? 'void'
      const expectedSize = name === 'dust_color_transition' || name === 'trail'
        ? undefined : payloadSize(buffer, particleId.end, type)
      if (expectedSize !== undefined && payloadLength !== expectedSize) throw new Error(`Invalid ${name} particle payload length`)
      if (expectedSize === undefined && payloadLength < 1) throw new Error(`Missing ${name} particle payload`)
      result.payloadHex = buffer.subarray(particleId.end).toString('hex')
      result.payloadDecoded = type === 'void'
    }
    return result
  }
}

export function isFeedbackReady(snapshot) {
  return Number.isFinite(snapshot.tps) && snapshot.tps >= 19.25 && snapshot.feedbackTransitionDensity === 1
}

export function observeParticlePackets(bot, onParticle, onError = error => bot.emit('error', error)) {
  const client = bot._client
  const decode = createParticlePacketDecoder(bot.registry, bot.version ?? client.version)
  const stream = client.decompressor ?? client.splitter
  if (!stream?.on || !stream?.off) throw new Error('No decompressed Minecraft packet stream available')
  const listener = buffer => {
    if (client.state !== 'play') return
    let particle
    try {
      particle = decode(buffer)
    } catch (error) {
      onError(error)
      return
    }
    if (particle) onParticle(particle)
  }
  stream.on('data', listener)
  return () => stream.off('data', listener)
}
