export function synchronizePlayerVelocity(bot) {
    const types = bot.registry.protocol.play.toClient.types
    const packetNames = ['entity_velocity', 'spawn_entity', 'spawn_entity_living']
        .filter(name => types[`packet_${name}`]?.[0] === 'container'
            && types[`packet_${name}`][1].some(field => field.name === 'velocity' && field.type === 'lpVec3'))
    if (!packetNames.includes('entity_velocity')) throw new Error('Adapt gameplay velocity requires the lpVec3 protocol')
    const receive = packet => {
        const entity = bot.entities[packet.entityId]
        const velocity = packet.velocity
        if (!entity || !velocity) return
        if (![velocity.x, velocity.y, velocity.z].every(Number.isFinite)) throw new Error('Invalid server velocity vector')
        entity.velocity.set(velocity.x, velocity.y, velocity.z)
    }
    for (const name of packetNames) bot._client.on(name, receive)
    return () => { for (const name of packetNames) bot._client.off(name, receive) }
}
