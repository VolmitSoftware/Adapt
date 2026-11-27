import { copyFile, readFile, readdir, rm } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import path from 'node:path'

const TIMEOUT = 15000
const SKILL = 'example:geology'
const PHASES = new Set(['stage', 'live', 'removed', 'restored'])

/** Key=value pairs of one GEOLOGY reply; list values keep their brackets. */
function fields(line) {
    const result = {}
    for (const match of line.matchAll(/(\w+)=(\[[^\]]*\]|\S+)/g)) result[match[1]] = match[2]
    return result
}

/** The player save below the server folder, wherever the platform keeps Adapt's data. */
async function findSave(root, file, depth = 0) {
    if (depth > 5) return null
    const entries = await readdir(root, { withFileTypes: true }).catch(() => [])
    for (const entry of entries) {
        const child = path.join(root, entry.name)
        if (entry.isFile() && entry.name === file && child.includes(`${path.sep}players${path.sep}`)) return child
    }
    for (const entry of entries) {
        if (!entry.isDirectory() || ['libraries', 'versions', 'logs', 'cache', 'world', 'world_nether', 'world_the_end'].includes(entry.name)) continue
        const found = await findSave(path.join(root, entry.name), file, depth + 1)
        if (found) return found
    }
    return null
}

function plainText(value) {
    if (value === undefined || value === null) return ''
    if (typeof value === 'string') return value
    return JSON.stringify(value)
}

function slotText(item) {
    if (!item) return ''
    return [item.customName, item.displayName, item.name, plainText(item.components), plainText(item.nbt)]
        .map(plainText).join(' ').replace(/§./g, '')
}

export default {
    name: 'example-geology',
    description: 'Prove the example Geology addon, built only against adapt-api, is first-class content.',
    async run(context) {
        const phase = context.options.command || 'live'
        context.expect(PHASES.has(phase), `Unknown phase ${phase}`)
        const bot = context.bot
        const directory = context.server.directory
        const jarName = process.env.EXAMPLE_GEOLOGY_JAR ? path.basename(process.env.EXAMPLE_GEOLOGY_JAR) : ''
        const contentFolder = existsSync(path.join(directory, 'plugins')) && !existsSync(path.join(directory, 'mods'))
            ? 'plugins' : 'mods'
        const installed = path.join(directory, contentFolder, jarName)
        const bukkit = contentFolder === 'plugins'
        const evidence = context.report.exampleGeology ??= {}
        const record = evidence[phase] = { contentFolder }

        const ask = async action => {
            const verb = action.split(' ')[0]
            const line = await context.command(`/adapt geology ${action}`, new RegExp(`^GEOLOGY ${verb} `), TIMEOUT)
            record[`ask ${action}`] = line
            return { line, ...fields(line) }
        }
        const run = async (command, expected) => {
            const line = await context.command(command, expected, TIMEOUT)
            record[command] = line
            return line
        }
        const silent = async (command, settle = 600) => {
            bot.chat(command)
            await context.sleep(settle)
        }
        const mainMenu = async label => {
            const opened = context.waitForEvent('windowOpen', () => true, TIMEOUT)
            bot.chat('/adapt gui')
            const [window] = await opened
            await context.waitUntil(() => window.slots.slice(0, window.inventoryStart).some(Boolean), { label, timeoutMs: TIMEOUT })
            await context.sleep(500)
            const titles = window.slots.slice(0, window.inventoryStart).filter(Boolean).map(slotText)
            return { window, titles }
        }
        const skillPage = async name => {
            const { window, titles } = await mainMenu(`main menu for ${name}`)
            const slot = window.slots.slice(0, window.inventoryStart).findIndex(item => slotText(item).includes(name))
            context.expect(slot >= 0, `The main menu has a ${name} tile`, titles.map(title => title.slice(0, 120)))
            const opened = context.waitForEvent('windowOpen', () => true, TIMEOUT)
            await bot.clickWindow(slot, 0, 0)
            const [page] = await opened
            await context.waitUntil(() => page.slots.slice(0, page.inventoryStart).some(Boolean), { label: `${name} page`, timeoutMs: TIMEOUT })
            await context.sleep(500)
            const pageTitles = page.slots.slice(0, page.inventoryStart).filter(Boolean).map(slotText)
            for (let index = 0; index < 3 && bot.currentWindow; index++) {
                bot.closeWindow(bot.currentWindow)
                await context.sleep(300)
            }
            return pageTitles
        }
        const breakAt = async (dx, type) => {
            await silent(`/setblock ~${dx} ~ ~ ${type}`)
            const target = bot.blockAt(bot.entity.position.floored().offset(dx, 0, 0))
            await context.waitUntil(() => bot.blockAt(target.position)?.name === type, { label: `${type} placed`, timeoutMs: TIMEOUT })
            await bot.dig(bot.blockAt(target.position), true)
        }
        // A server sends no completion packet when nothing matches, so a timeout means no suggestions.
        const offers = async (typed, word) => {
            const matches = await bot.tabComplete(typed, false, false, 4000).catch(() => [])
            return matches.some(match => (match.match ?? match) === word)
        }
        const statusUntil = async (label, predicate) => {
            let status
            await context.waitUntil(async () => {
                status = await ask('status')
                return predicate(status)
            }, { label, timeoutMs: 20000, intervalMs: 500 })
            return status
        }

        if (phase === 'stage') {
            await context.step('Install the example jar for the next boot', async () => {
                context.expect(Boolean(process.env.EXAMPLE_GEOLOGY_JAR), 'EXAMPLE_GEOLOGY_JAR names the example jar')
                await copyFile(process.env.EXAMPLE_GEOLOGY_JAR, installed)
                record.installed = installed
            })
            return
        }

        if (phase === 'removed') {
            await context.step('Without the example jar the skill is gone and the save keeps its line', async () => {
                const reply = await context.command('/adapt geology status', /^GEOLOGY status /, 4000).then(line => line, () => null)
                context.expect(reply === null, 'Nothing answers /adapt geology', reply)
                const { titles } = await mainMenu('main menu without the addon')
                record.menu = titles
                context.expect(!titles.some(title => /Geology|Geologie/.test(title)), 'The main menu has no Geology tile', titles)
                context.expect(!titles.some(title => /Deep Strata/.test(title)), 'The suspended mutation has no tile', titles)
                bot.closeWindow(bot.currentWindow)
                const save = await findSave(directory, `${bot.player.uuid}.json`)
                record.save = save
                context.expect(save !== null, 'The player save exists')
                const text = await readFile(save, 'utf8')
                const at = text.indexOf(SKILL)
                record.saveExcerpt = at < 0 ? '' : text.slice(Math.max(0, at - 40), at + 400)
                context.expect(at >= 0, 'The save still holds the example:geology line')
                record.suspendedLine = text.includes(`${SKILL}+adapt:pickaxe`)
                context.expect(record.suspendedLine, 'The save keeps the suspended mutated line')
                await copyFile(process.env.EXAMPLE_GEOLOGY_JAR, installed)
            })
            return
        }

        if (phase === 'restored') {
            await context.step('The returning addon finds the saved progress', async () => {
                const status = await ask('status')
                record.status = status
                context.expect(status.available === 'true', 'The skill is available again', status)
                const line = await ask('mutation')
                record.mutation = line
                context.expect(line.pair === `${SKILL}+adapt:pickaxe` && Number(line.xp) > 0, 'The mutation and its XP came back', line)
                context.expect(/echo=1/.test(line.line) && /motherlode=1/.test(line.line), 'Deep Echo and Motherlode came back', line)
                const notes = await ask('notes')
                record.notes = notes
                context.expect(notes.entries === '2' && notes.section === 'example:field_notes', 'The field notes section came back', notes)
                const { titles } = await mainMenu('main menu with the returned mutation')
                context.expect(titles.some(title => title.includes('Deep Strata')), 'The main menu shows Deep Strata again', titles.map(title => title.slice(0, 120)))
                bot.closeWindow(bot.currentWindow)
            })
            return
        }

        const failures = []
        const attempt = async (name, action) => {
            try {
                await context.step(name, action)
            } catch (error) {
                failures.push(`${name}: ${error.message}`)
                record.failures = failures
                for (let index = 0; index < 3 && bot.currentWindow; index++) {
                    bot.closeWindow(bot.currentWindow)
                    await context.sleep(300)
                }
                bot.setControlState('sneak', false)
            }
        }

        await context.step('Prepare the operator', async () => {
            await silent('/gamemode survival')
            await silent('/clear')
            await silent('/give @s iron_pickaxe')
            await silent('/effect give @s minecraft:saturation 600 10 true')
            await context.waitUntil(() => bot.inventory.items().some(item => item.name === 'iron_pickaxe'), { label: 'pickaxe arrives', timeoutMs: TIMEOUT })
            await bot.equip(bot.inventory.items().find(item => item.name === 'iron_pickaxe'), 'hand')
        })

        await attempt('The addon command is registered with completion and help', async () => {
            record.rootCompletes = await offers('/adapt geo', 'geology')
            context.expect(record.rootCompletes, 'Tab completion offers geology after /adapt')
            record.subCompletes = await offers('/adapt geology st', 'status')
            context.expect(record.subCompletes, 'Tab completion offers the status subcommand')
            record.valueCompletes = await offers('/adapt geology delivery ', 'inventory')
            context.expect(record.valueCompletes, 'Tab completion offers delivery values')
            const help = await run('/adapt geology', /status/)
            context.expect(/status/.test(help), 'The help page lists the subcommands', help)
            const invalid = await run('/adapt geology learn example:geology/prospect 0', /level/)
            context.expect(/level/.test(invalid), 'An out-of-range level is refused with the argument named', invalid)
            record.builtinCompletes = await offers('/adapt geology learn adapt:pickaxe/gem', 'adapt:pickaxe/gem_polish')
            context.expect(record.builtinCompletes, 'The adaptation argument completes a built-in adaptation')
            const unknown = await run('/adapt geology learn adapt:pickaxe/no_such_thing 1', /adaptation/)
            context.expect(/adaptation/.test(unknown), 'An unknown adaptation is refused with the argument named', unknown)
        })

        await attempt('The player section is declared and written', async () => {
            const notes = await ask('notes')
            record.notes = notes
            context.expect(notes.entries === '1' && notes.section === 'example:field_notes', 'The first field note is written', notes)
        })

        await attempt('The addon is active and its content is in the catalog', async () => {
            const addon = await ask('addon')
            context.expect(addon.state === 'ACTIVE', 'The addon handle is ACTIVE', addon)
            const catalog = await ask('catalog')
            record.catalog = catalog
            context.expect(catalog.listed.includes(SKILL), 'The catalog lists example:geology', catalog)
            context.expect(catalog.geology === '[geology/stone_skin, geology/prospect, geology/core_sample, geology/geode_lens]',
                'The skill carries its four adaptations in order', catalog)
            context.expect(catalog.owner === 'example' && catalog.aftershockOwner === 'example', 'Both skill and the added adaptation belong to example', catalog)
            context.expect(catalog.skillConfig === 'true' && catalog.prospectConfig === 'true' && catalog.aftershockConfig === 'true',
                'Config files exist under the namespaced paths', catalog)
            context.expect(catalog.assets === '6' && catalog.locales === '[de_DE, en_US]', 'The jar lists six sprites and two locales', catalog)
            record.builtinsListed = catalog.pickaxeInCatalog
            context.expect(catalog.pickaxeInCatalog === 'true' && catalog.aftershockOnPickaxe === 'true',
                'The catalog lists the built-in Pickaxes skill with Aftershock on it', catalog)
            record.aftershockOnPickaxe = catalog.aftershockOnPickaxe
        })

        await attempt('Text resolves in English and German', async () => {
            const english = await run('/adapt geology text en_US', /^GEOLOGY text locale=en_US/)
            const german = await run('/adapt geology text de_DE', /^GEOLOGY text locale=de_DE/)
            context.expect(/skill=Geology \| prospect=Prospect \| aftershock=Aftershock \| lens=Geode Lens \| found=Ore nearby: 3/.test(english), 'English names resolve', english)
            context.expect(/skill=Geologie \| prospect=Schuerfen \| aftershock=Nachbeben \| lens=Geodenlinse \| found=Erz in der Naehe: 3/.test(german), 'German names resolve', german)
        })

        let baseline
        await attempt('Breaking stone pays Geology XP through its declared source', async () => {
            baseline = await ask('status')
            await breakAt(2, 'stone')
            const status = await statusUntil('stone XP arrives', candidate => Number(candidate.xpEvents) > Number(baseline.xpEvents))
            record.afterStone = status
            context.expect(Number(status.xp) > Number(baseline.xp), 'Geology XP grew', { baseline, status })
            context.expect(Number(status.rocks) >= 1, 'The rocks stat counts the block', status)
            await breakAt(-2, 'iron_ore')
            const ore = await statusUntil('ore XP arrives', candidate => Number(candidate.xpEvents) > Number(status.xpEvents))
            record.afterOre = ore
            context.expect(Number(ore.xp) > Number(status.xp), 'Ore pays more Geology XP', { status, ore })
        })

        await attempt('The skill appears in the Adapt menu with its adaptations', async () => {
            const { window, titles } = await mainMenu('main menu')
            record.mainMenu = titles
            const slot = window.slots.slice(0, window.inventoryStart).findIndex(item => /Geology/.test(slotText(item)))
            context.expect(slot >= 0, 'The main menu has a Geology tile', titles)
            const opened = context.waitForEvent('windowOpen', () => true, TIMEOUT)
            await bot.clickWindow(slot, 0, 0)
            const [skillWindow] = await opened
            await context.waitUntil(() => skillWindow.slots.slice(0, skillWindow.inventoryStart).some(Boolean), { label: 'skill menu', timeoutMs: TIMEOUT })
            await context.sleep(500)
            const skillTitles = skillWindow.slots.slice(0, skillWindow.inventoryStart).filter(Boolean).map(slotText)
            record.skillMenu = skillTitles
            for (const name of ['Stone Skin', 'Prospect', 'Core Sample', 'Geode Lens']) {
                context.expect(skillTitles.some(title => title.includes(name)), `The skill menu shows ${name}`, skillTitles)
            }
            bot.closeWindow(bot.currentWindow)
            await context.sleep(300)
            if (bot.currentWindow) bot.closeWindow(bot.currentWindow)
        })

        await attempt('A learned passive takes effect', async () => {
            record.award = await ask('xp 40000')
            record.levelled = await statusUntil('awarded XP raises the level', candidate => Number(candidate.level) > 5)
            await ask('knowledge 60')
            let learned
            await context.waitUntil(async () => {
                learned = await ask('learn example:geology/stone_skin 2')
                return learned.status !== 'INSUFFICIENT_POWER'
            }, { label: 'power follows the new level', timeoutMs: 20000, intervalMs: 1000 })
            record.stoneSkinLearn = learned
            context.expect(learned.to === '2', 'Stone Skin is learned by paying knowledge', learned)
            const status = await statusUntil('armor applies', candidate => Number(candidate.armor) >= 3)
            record.stoneSkin = status
            context.expect(Number(status.learnEvents) >= 1, 'The learn event reached the public listener', status)
            context.expect(status.permission === 'true', 'The adapt.use.example node is granted to the operator', status)
        })

        await attempt('A built-in requirement gates Core Sample', async () => {
            const refused = await ask('learn example:geology/core_sample 1')
            record.coreSampleRefused = refused
            context.expect(refused.status !== 'SUCCESS', 'Core Sample is refused without Gem Polish', refused)
            await ask('learn adapt:pickaxe/gem_polish 1 force=true')
            const learned = await ask('learn example:geology/core_sample 5')
            record.coreSampleLearned = learned
            context.expect(learned.to === '5', 'Core Sample is learned after the requirement', learned)
        })

        await attempt('The delivery preference changes where samples go', async () => {
            const changed = await ask('delivery INVENTORY')
            context.expect(changed.now === 'INVENTORY', 'The preference is stored', changed)
            const before = await ask('status')
            const items = () => bot.inventory.items().filter(item => ['flint', 'raw_iron', 'amethyst_shard'].includes(item.name))
                .reduce((sum, item) => sum + item.count, 0)
            const carried = items()
            for (let index = 0; index < 12; index++) await breakAt(index % 2 === 0 ? 2 : -2, 'stone')
            const after = await statusUntil('samples counted', candidate => Number(candidate.samples) > Number(before.samples))
            record.samples = { before: before.samples, after: after.samples, inventory: items() - carried }
            context.expect(items() > carried, 'Samples arrive in the inventory', record.samples)
        })

        await attempt('The active ability shows its HUD and holds a cooldown', async () => {
            record.prospectLearn = await ask('learn example:geology/prospect 1 force=true')
            const before = await ask('status')
            await silent('/setblock ~2 ~ ~ stone')
            await silent('/setblock ~2 ~1 ~ iron_ore')
            const target = bot.blockAt(bot.entity.position.floored().offset(2, 0, 0))
            const actionBar = new Promise(resolve => bot.once('actionBar', message => resolve(message.toString())))
            bot.setControlState('sneak', true)
            await context.sleep(300)
            await bot.activateBlock(target)
            const first = await statusUntil('first prospect', candidate => Number(candidate.prospects) > Number(before.prospects))
            await bot.activateBlock(target)
            await context.sleep(800)
            const second = await ask('status')
            bot.setControlState('sneak', false)
            record.prospect = { before: before.prospects, first: first.prospects, second: second.prospects }
            record.actionBar = await Promise.race([actionBar, context.sleep(3000).then(() => 'none')])
            context.expect(Number(second.prospects) === Number(first.prospects), 'The cooldown blocks the second use', record.prospect)
        })

        await attempt('The custom recipe and the adaptation on a built-in skill', async () => {
            const lens = await ask('learn example:geology/geode_lens 1 force=true')
            record.lens = lens
            record.recipe = await run(`/recipe give ${bot.username} example:geode_lens`, /recipe|Recipe|Rezept|Unknown|unknown/)
            const aftershock = await ask('learn example:pickaxe/aftershock 1 force=true')
            record.aftershock = aftershock
            const status = await ask('status')
            context.expect(status.aftershock === '1', 'Aftershock is learned on the built-in Pickaxes skill', status)
            const pickaxes = await skillPage('Pickax')
            record.pickaxeMenu = pickaxes.filter(title => /Aftershock|Gem Polish/.test(title)).map(title => title.slice(0, 200))
            context.expect(pickaxes.some(title => title.includes('Aftershock')), 'The Pickaxes page shows Aftershock', record.pickaxeMenu)
        })

        if (bukkit) {
            await attempt('Live uninstall keeps the player data and reinstall restores it', async () => {
                const before = await ask('status')
                const removed = await run('/adaptgeology uninstall', /^GEOLOGY-PLUGIN uninstall /)
                context.expect(/listed=false/.test(removed), 'The skill left the catalog', removed)
                context.expect(/example:geology/.test(removed), 'The progress is listed as an orphan', removed)
                const quiet = await context.command('/adapt geology status', /^GEOLOGY status /, 3000).then(line => line, () => null)
                context.expect(quiet === null, 'The addon command left with the addon', quiet)
                record.completesAfterUninstall = await offers('/adapt geo', 'geology')
                context.expect(!record.completesAfterUninstall, 'Tab completion no longer offers geology')
                const back = await run('/adaptgeology install', /^GEOLOGY-PLUGIN install /)
                context.expect(/state=ACTIVE listed=true/.test(back), 'The addon is active again', back)
                const after = await ask('status')
                record.reinstall = { before, after }
                context.expect(after.adaptations === before.adaptations && after.xp === before.xp, 'Levels and XP survive the reinstall', { before, after })
            })
        }

        await attempt('Geology mutates with a built-in skill in both orders', async () => {
            record.trainGeology = await ask('train example:geology 1000000')
            record.trainPickaxe = await ask('train adapt:pickaxe 1000000')
            const led = await ask('lead adapt:pickaxe')
            record.reverse = led
            context.expect(led.status === 'MUTATED' && led.pair === `adapt:pickaxe+${SKILL}`, 'Pickaxes leads a mutation with Geology', led)
            const reverseMenu = await mainMenu('main menu with the reverse mutation')
            record.reverseMenu = reverseMenu.titles.filter(title => /Stratified|Delver/.test(title)).map(title => title.slice(0, 200))
            context.expect(reverseMenu.titles.some(title => title.includes('Stratified Delver')), 'The main menu shows Stratified Delver', record.reverseMenu)
            bot.closeWindow(bot.currentWindow)
            const undone = await ask('unmutate')
            context.expect(undone.status === 'UNMUTATED', 'The reverse mutation is undone', undone)
            const merged = await ask('mutate adapt:pickaxe')
            record.forward = merged
            context.expect(merged.status === 'MUTATED' && merged.pair === `${SKILL}+adapt:pickaxe`, 'Geology leads a mutation with Pickaxes', merged)
            const page = await skillPage('Deep Strata')
            record.mutatedPage = page.map(title => title.slice(0, 200))
            for (const name of ['Graft', 'Echo', 'Barter', 'Attunement', 'Convergence', 'Motherlode']) {
                context.expect(page.some(title => title.includes(name)), `The Deep Strata page shows ${name}`, record.mutatedPage)
            }
            record.learnEcho = await ask('learn example:mutated/geology/adapt/pickaxe/echo 1 force=true')
            context.expect(record.learnEcho.status === 'LEARNED', 'Deep Echo is learned on the mutated line', record.learnEcho)
            record.learnUpgrade = await ask('learn adapt:pickaxe/upgrade/motherlode 1 force=true')
            context.expect(record.learnUpgrade.status === 'LEARNED', 'Motherlode is learned on the mutated line', record.learnUpgrade)
            const haste = bot.registry.effectsByName?.Haste?.id ?? 3
            await silent('/effect clear @s minecraft:haste')
            await bot.equip(bot.inventory.items().find(item => item.name === 'iron_pickaxe'), 'hand')
            await breakAt(1, 'iron_ore')
            await context.waitUntil(() => Object.values(bot.entity.effects ?? {}).some(effect => effect.id === haste),
                { label: 'Pickaxes Trigger sets off the Geology Work Strike (Haste)', timeoutMs: TIMEOUT })
            record.echoHaste = true
            const line = await ask('mutation')
            record.mutation = line
            context.expect(/echo=1/.test(line.line) && /motherlode=1/.test(line.line), 'The mutated line holds Deep Echo and Motherlode', line)
        })

        if (!bukkit) {
            await attempt('Uninstall through the addon command removes the command', async () => {
                record.beforeRemoval = await ask('status')
                await silent('/adapt geology uninstall', 1500)
                const quiet = await context.command('/adapt geology status', /^GEOLOGY status /, 3000).then(line => line, () => null)
                context.expect(quiet === null, 'The addon command left with the addon', quiet)
                record.completesAfterUninstall = await offers('/adapt geo', 'geology')
                context.expect(!record.completesAfterUninstall, 'Tab completion no longer offers geology')
            })
        }

        await context.step('Remove the example jar for the next boot', async () => {
            if (bukkit) record.beforeRemoval = await ask('status')
            await rm(installed)
        })
        context.expect(failures.length === 0, `Live checks failed: ${failures.join(' | ')}`, failures)
    },
}
