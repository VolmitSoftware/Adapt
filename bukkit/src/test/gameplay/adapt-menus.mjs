/**
 * Follows the Adapt menus one bot sees. A menu opened by a fixture command reaches the bot on its own connection,
 * and Escape on an adaptation or skill menu reopens its parent menu a tick later (escClosesAllGuis is false), so the
 * window present right after a command can still be the previous or the parent menu. A window counts as the next
 * menu only when it differs from the last one this tracker returned and its contents arrived.
 */
export function adaptMenus(context, bot, { timeoutMs = 5000 } = {}) {
    let seen = null
    const filled = window => window.slots.slice(0, window.inventoryStart).some(Boolean)
    const next = async (label, previous) => {
        await context.waitUntil(() => {
            const window = bot.currentWindow
            return Boolean(window) && window !== previous && filled(window)
        }, { label, timeoutMs })
        seen = bot.currentWindow
        return seen
    }
    return {
        /** Wait for the menu after the last one returned and return it once its contents arrived. */
        opened: label => next(label, seen),
        /** Close the open menu and wait for the parent menu Escape reopens, which also proves the server handled the close. */
        async close(label) {
            const window = bot.currentWindow
            if (!window) throw new Error(`${bot.username} has no open menu to close (${label})`)
            bot.closeWindow(window)
            return next(label, window)
        },
    }
}
