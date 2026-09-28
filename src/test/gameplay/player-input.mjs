export function synchronizePlayerInput(bot) {
    if (!bot.supportFeature('newPlayerInputPacket')) throw new Error('Adapt gameplay input requires the current player_input protocol')
    const setControlState = bot.setControlState
    const write = bot._client.write
    const inputs = () => ({
        forward: bot.getControlState('forward'), backward: bot.getControlState('back'),
        left: bot.getControlState('left'), right: bot.getControlState('right'),
        jump: bot.getControlState('jump'), shift: bot.getControlState('sneak'), sprint: bot.getControlState('sprint'),
    })
    bot._client.write = function (name, packet, ...rest) {
        return write.call(this, name, name === 'player_input' ? { ...packet, inputs: inputs() } : packet, ...rest)
    }
    bot.setControlState = function (control, state) {
        const changed = bot.getControlState(control) !== state
        const result = setControlState.call(this, control, state)
        if (changed && control !== 'sneak') bot._client.write('player_input', { inputs: inputs() })
        return result
    }
    return () => {
        bot.setControlState = setControlState
        bot._client.write = write
    }
}
