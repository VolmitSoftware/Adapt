const CHAT_DISABLED = /Chat disabled in client options/
const MAX_RESENDS = 3

/** Re-sends the operator's client settings after world changes and repeats a command the server refused for hidden chat. */
export function guardOperator(bot) {
    const send = bot.chat.bind(bot)
    let last
    let resends = 0
    bot.chat = text => {
        last = text
        resends = 0
        send(text)
    }
    bot.on('respawn', () => bot.setSettings({}))
    bot.on('messagestr', message => {
        if (!CHAT_DISABLED.test(message) || last === undefined || resends >= MAX_RESENDS) return
        resends++
        bot.setSettings({})
        setTimeout(() => send(last), 100)
    })
    return bot
}

/** Wraps a scenario module so its operator bot is guarded before the scenario starts. */
export function guardedScenario(scenario) {
    return {
        ...scenario,
        run: context => {
            guardOperator(context.bot)
            return scenario.run(context)
        },
    }
}
