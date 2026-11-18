const WINDOW_PACKETS = ['open_window', 'window_items', 'set_slot', 'close_window', 'set_player_inventory', 'set_cursor_item']

/** Drop held inputs, close the open window, and forget windows that never received their contents. */
export function releaseClient(bot) {
    bot.clearControlStates()
    bot.deactivateItem()
    if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
    // mineflayer emits windowOpen for a window only once its contents arrive; a window whose
    // contents never came keeps that listener and later answers the next window with the same id.
    for (const event of bot.eventNames()) {
        if (typeof event === 'string' && event.startsWith('setWindowItems:') && event !== 'setWindowItems:0') bot.removeAllListeners(event)
    }
}

/** Wait until the server answers a full player inventory resync, which it only does with no container open. */
export async function resynchronizeInventory(context, bot, { timeoutMs = 5000, attemptMs = 1000 } = {}) {
    const deadline = Date.now() + timeoutMs
    for (;;) {
        let timer
        const answered = await Promise.race([
            bot._syncWindow(bot.inventory).then(() => true),
            new Promise(resolve => { timer = setTimeout(() => resolve(false), attemptMs) }),
        ])
        clearTimeout(timer)
        if (answered && !bot.currentWindow && !bot.inventory.selectedItem) return
        context.signal?.throwIfAborted()
        if (Date.now() >= deadline) {
            throw new Error(`${bot.username} inventory was not resynchronized with every window closed and an empty cursor`)
        }
        if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
    }
}

/**
 * Publish a container window to mineflayer together with its contents. The server sends open_window and window_items
 * back to back, but they reach mineflayer as separate events; a case polling bot.currentWindow in between saw an empty
 * window ("16 oak_planks available for crafting slot 1"). Every other window packet releases a held window first.
 */
export function holdWindowsUntilFilled(bot) {
    const client = bot._client
    const emit = client.emit
    let held = null
    const release = () => {
        const pending = held
        held = null
        if (pending) emit.apply(client, pending)
    }
    client.emit = function (event, packet, ...rest) {
        if (event === 'open_window') {
            release()
            held = [event, packet, ...rest]
            return true
        }
        if (held && (WINDOW_PACKETS.includes(event) || event === 'craft_progress_bar' || event === 'trade_list' || event === 'end')) release()
        return emit.call(this, event, packet, ...rest)
    }
    return () => {
        release()
        client.emit = emit
    }
}

/** Record recent window packets for failure reports. */
export function traceWindowPackets(bot, limit = 64) {
    const entries = []
    const listeners = WINDOW_PACKETS.map(name => [name, packet => {
        const entry = { at: Date.now(), packet: name, windowId: packet.windowId }
        if (name === 'open_window') entry.type = packet.inventoryType
        if (name === 'window_items') entry.filled = packet.items.filter(item => item && item.itemCount > 0).length
        if (name === 'set_slot' || name === 'set_player_inventory') entry.slot = packet.slot ?? packet.slotId
        entries.push(entry)
        if (entries.length > limit) entries.shift()
    }])
    for (const [name, listener] of listeners) bot._client.on(name, listener)
    return {
        entries,
        stop: () => { for (const [name, listener] of listeners) bot._client.off(name, listener) },
    }
}
