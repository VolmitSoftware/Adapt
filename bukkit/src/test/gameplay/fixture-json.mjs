export async function fixtureJson(context, command, response, timeoutMs = 5000) {
  const prefix = `ADAPT_QA ${response} `
  const chunks = new Map()
  let total
  let failure
  const listener = message => {
    if (message.startsWith('ADAPT_QA ERROR ')) { failure = new Error(message); return }
    if (!message.startsWith(prefix)) return
    const match = message.slice(prefix.length).match(/^(\d+) (\d+) ([\s\S]*)$/)
    if (!match) { failure = new Error(`Invalid fixture JSON chunk: ${response}`); return }
    const index = Number(match[1])
    const count = Number(match[2])
    if (count < 1 || count > 128 || index >= count || (total !== undefined && total !== count) || chunks.has(index)) {
      failure = new Error(`Inconsistent fixture JSON chunks: ${response}`)
      return
    }
    total = count
    chunks.set(index, match[3])
  }
  context.bot.on('messagestr', listener)
  try {
    context.bot.chat(command)
    await context.waitUntil(() => {
      if (failure) throw failure
      return total !== undefined && chunks.size === total
    }, { label: response, timeoutMs, intervalMs: 10 })
    return JSON.parse(Array.from({ length: total }, (_, index) => chunks.get(index)).join(''))
  } finally {
    context.bot.off('messagestr', listener)
  }
}

export async function commandAcrossWorlds(context, command, expected, timeoutMs) {
  let placed = false
  const onPlaced = () => { placed = true }
  context.bot.on('forcedMove', onPlaced)
  try {
    const response = await context.command(command, expected, timeoutMs)
    await context.waitUntil(() => placed && context.bot.isAlive, { label: `operator loaded after ${command.split(' ').slice(0, 2).join(' ')}`, timeoutMs: 15000 })
    await context.bot.waitForTicks(2)
    return response
  } finally {
    context.bot.off('forcedMove', onPlaced)
  }
}
