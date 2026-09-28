export function synchronizePlayerPassengers(bot) {
    const reconcile = ({ entityId, passengers }) => {
        const previous = bot.vehicle
        if (!previous || previous.id !== entityId || passengers.includes(bot.entity.id)) return
        previous.passengers = (previous.passengers ?? []).filter(passenger => passenger.id !== bot.entity.id)
        bot.entity.vehicle = null
        bot.vehicle = null
        bot.emit('dismount', previous)
    }
    bot._client.on('set_passengers', reconcile)
    return () => bot._client.off('set_passengers', reconcile)
}
