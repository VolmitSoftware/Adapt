import { createRequire } from 'node:module'

const ADD = new Set(['on', 'addListener', 'once', 'prependListener', 'prependOnceListener'])
const REMOVE = new Set(['removeListener', 'off'])

/**
 * Wraps mineflayer's createBot so a connect that sits in the configuration phase without spawning (the ViaProxy stall:
 * the server later drops it with "Timed out" in ServerConfigurationPacketListenerImpl) is torn down and reconnected before
 * anyone sees it. The returned bot is a stable handle; listeners and plugins added before spawn move to each new attempt and
 * the abandoned attempt's events never reach them. A disconnect packet (a kick for a real reason) is never retried, and the
 * last attempt is left to finish or fail exactly as an unwrapped connect would. After spawn the handle forwards unchanged.
 */
export function resilientCreateBot(createBot, { stallMs = 12000, attempts = 3, settleMs = 500, pollMs = 250, log = line => process.stderr.write(`${line}\n`) } = {}) {
    return function createResilientBot(options) {
        const template = { ...options }
        const registrations = []
        const plugins = []
        let current
        let attempt = 0
        let spawned = false
        let state

        const attach = (bot, entry, owner) => {
            entry.wrapper = function (...args) {
                if (!owner.active) return
                if (entry.once) detach(entry)
                return entry.listener.apply(this, args)
            }
            bot[entry.prepend ? 'prependListener' : 'on'](entry.event, entry.wrapper)
        }
        const detach = entry => {
            current.removeListener(entry.event, entry.wrapper)
            registrations.splice(registrations.indexOf(entry), 1)
        }
        const start = () => {
            attempt++
            const owner = { active: true, configurationAt: undefined, disconnectPacket: false, timer: undefined }
            state = owner
            const bot = createBot({ ...template })
            current = bot
            for (const entry of registrations) attach(bot, entry, owner)
            for (const plugin of plugins) bot.loadPlugin(plugin)
            const client = bot._client
            if (client?.state === 'configuration') owner.configurationAt = Date.now()
            client?.on('state', next => { if (next === 'configuration') owner.configurationAt ??= Date.now() })
            client?.on('packet', (_, meta) => { if (/disconnect/.test(meta?.name ?? '')) owner.disconnectPacket = true })
            bot.once('spawn', () => { spawned = true; clearInterval(owner.timer) })
            bot.once('end', () => clearInterval(owner.timer))
            if (attempt >= attempts) return
            owner.timer = setInterval(() => {
                if (spawned || !owner.active || owner.disconnectPacket || owner.configurationAt === undefined) return
                if (client.state !== 'configuration' || Date.now() - owner.configurationAt < stallMs) return
                retry(owner, bot)
            }, pollMs)
            owner.timer.unref?.()
        }
        const retry = (owner, bot) => {
            owner.active = false
            clearInterval(owner.timer)
            log(`RETRY connect ${template.username}: no spawn after ${Math.round((Date.now() - owner.configurationAt) / 1000)} s in the configuration phase, reconnecting (attempt ${attempt + 1}/${attempts})`)
            for (const entry of registrations) bot.removeListener(entry.event, entry.wrapper)
            bot.on('error', () => {})
            try { bot.end('Configuration phase stalled') } catch {}
            try { bot._client?.socket?.destroy() } catch {}
            setTimeout(start, settleMs).unref?.()
        }

        const handle = {}
        for (const method of ADD) {
            handle[method] = (event, listener) => {
                if (spawned) current[method](event, listener)
                else {
                    const entry = { event, listener, once: method.includes('Once') || method === 'once', prepend: method.startsWith('prepend') }
                    registrations.push(entry)
                    attach(current, entry, state)
                }
                return facade
            }
        }
        for (const method of REMOVE) {
            handle[method] = (event, listener) => {
                const entry = registrations.find(candidate => candidate.event === event && candidate.listener === listener)
                if (entry) detach(entry)
                else current[method](event, listener)
                return facade
            }
        }
        handle.loadPlugin = plugin => {
            if (!spawned) plugins.push(plugin)
            return current.loadPlugin(plugin)
        }
        handle.loadPlugins = list => { for (const plugin of list) handle.loadPlugin(plugin) }

        const facade = new Proxy({}, {
            get: (_, property) => Object.hasOwn(handle, property) ? handle[property] : Reflect.get(current, property, current),
            set: (_, property, value) => Reflect.set(current, property, value, current),
            has: (_, property) => property in current,
            deleteProperty: (_, property) => Reflect.deleteProperty(current, property),
            ownKeys: () => Reflect.ownKeys(current),
            getOwnPropertyDescriptor: (_, property) => {
                const descriptor = Reflect.getOwnPropertyDescriptor(current, property)
                return descriptor && { ...descriptor, configurable: true }
            },
        })
        start()
        return facade
    }
}

/** Replaces the harness's mineflayer createBot (resolved from the harness entry script) with the resilient one. */
export function installResilientConnect(entry = process.argv[1], settings = {}) {
    const mineflayer = createRequire(entry)('mineflayer')
    if (mineflayer.createBot.resilientConnect) return
    const wrapped = resilientCreateBot(mineflayer.createBot.bind(mineflayer), settings)
    wrapped.resilientConnect = true
    mineflayer.createBot = wrapped
}

if (process.env.ADAPT_QA_RESILIENT_CONNECT === '1') installResilientConnect()
