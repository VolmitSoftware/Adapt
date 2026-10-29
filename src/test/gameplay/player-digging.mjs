export function diggingEnchantments(bot, item) {
    if (!item) return []
    const component = item.componentMap?.get('enchantments')
    if (!component) return item.enchants
    if (!Array.isArray(component.data?.enchantments)) throw new Error('Invalid enchantments component in digging item')
    return component.data.enchantments.map(({ id, level }) => {
        const name = bot.registry.enchantments[id]?.name
        if (!name || !Number.isInteger(level) || level < 1) throw new Error(`Invalid digging enchantment ${id}:${level}`)
        return { name, lvl: level }
    })
}

export function synchronizeDiggingEnchantments(bot) {
    const originalDigTime = bot.digTime
    bot.digTime = block => {
        const held = bot.heldItem
        const helmet = bot.inventory.slots[bot.getEquipmentDestSlot('head')]
        return block.digTime(held?.type ?? null, bot.game.gameMode === 'creative',
            ['water', 'flowing_water'].includes(bot._getBlockAtEyeLevel()?.name), !bot.entity.onGround,
            [...diggingEnchantments(bot, held), ...diggingEnchantments(bot, helmet)], bot.entity.effects)
    }
    return () => { bot.digTime = originalDigTime }
}
