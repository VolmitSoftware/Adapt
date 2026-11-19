// Through ViaProxy a 26.3 server can leave the 26.1 client's rotation-only packets unapplied while an
// item is in use, so the bow would still fire along the draw-start aim. The same rotation sent with the
// unchanged position is a legal vanilla movement packet; it is resent until the server reports the aim.
/** Turn to aim while an item is in use and wait until the server reports the new yaw. */
export async function aimWhileUsingItem(context, bot, aim, serverLocation) {
    await sendAimUntilApplied(context, bot, aim, serverLocation, false)
}

// Before 1.21.2 a thrown item leaves along the server's last known rotation (ViaProxy drops the rotation a 26.x
// use_item packet carries), and the server may not apply the client's rotation-only packet before the throw.
/** Turn to aim before using an item and wait until the server reports both the new yaw and pitch. */
export async function aimBeforeUse(context, bot, aim, serverLocation) {
    await sendAimUntilApplied(context, bot, aim, serverLocation, true)
}

async function sendAimUntilApplied(context, bot, aim, serverLocation, withPitch) {
    await bot.lookAt(aim, true)
    const yaw = (Math.PI - bot.entity.yaw) * 180 / Math.PI
    const pitch = -bot.entity.pitch * 180 / Math.PI
    const expected = ((yaw % 360) + 540) % 360 - 180
    const write = bot._client.write
    let pending = true
    let observed
    bot._client.write = function (name, packet, ...rest) {
        if (pending && (name === 'look' || name === 'position' || name === 'position_look')) {
            pending = false
            const position = bot.entity.position
            return write.call(this, 'position_look', { ...packet, x: position.x, y: position.y, z: position.z, yaw, pitch })
        }
        return write.call(this, name, packet, ...rest)
    }
    try {
        await context.waitUntil(async () => {
            observed = await serverLocation()
            const yawApplied = Math.abs(((observed.yaw - expected) % 360 + 540) % 360 - 180) < 0.5
            if (yawApplied && (!withPitch || Math.abs(observed.pitch - pitch) < 0.5)) return true
            if (!pending) {
                pending = true
                bot.entity.yaw += 0.0026 * (bot.entity.yaw > 0 ? -1 : 1)
            }
            return false
        }, { label: `server applies aim ${expected.toFixed(1)}${withPitch ? ` / ${pitch.toFixed(1)}` : ''}`, timeoutMs: 3000, intervalMs: 100 })
    } catch (error) {
        error.message += `; last server rotation ${JSON.stringify(observed)}`
        throw error
    } finally {
        if (bot._client.write !== write) bot._client.write = write
    }
}
