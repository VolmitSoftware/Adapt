import assert from 'node:assert/strict'
import test from 'node:test'
import { adaptMenus } from './adapt-menus.mjs'

const context = {
    async waitUntil(condition, { label, timeoutMs }) {
        const deadline = Date.now() + timeoutMs
        while (!(await condition())) {
            if (Date.now() >= deadline) throw new Error(`Timed out waiting for ${label}`)
            await new Promise(resolve => setTimeout(resolve, 2))
        }
    },
}

function menu(id, filled = true) {
    return { id, inventoryStart: 2, slots: filled ? [{ name: 'gray_stained_glass_pane' }, null, { name: 'stone' }] : [null, null, { name: 'stone' }] }
}

function fakeBot() {
    const closed = []
    const bot = { username: 'AQAm', currentWindow: null }
    bot.closeWindow = window => { closed.push(window.id); bot.currentWindow = null }
    return { bot, closed }
}

const later = (ms, action) => setTimeout(action, ms)

test('a menu opened after Escape reopened the parent is the window after the parent, not the parent', async () => {
    const { bot, closed } = fakeBot()
    const menus = adaptMenus(context, bot, { timeoutMs: 500 })
    bot.currentWindow = menu(1)
    assert.equal((await menus.opened('adaptation menu')).id, 1)
    later(10, () => { bot.currentWindow = menu(2) })
    assert.equal((await menus.close('Escape returns to the skill menu')).id, 2)
    assert.deepEqual(closed, [1])
    later(10, () => { bot.currentWindow = menu(3) })
    assert.equal((await menus.opened('next adaptation menu')).id, 3)
})

test('a window counts as opened only once its contents arrived', async () => {
    const { bot } = fakeBot()
    const menus = adaptMenus(context, bot, { timeoutMs: 500 })
    const window = menu(4, false)
    bot.currentWindow = window
    later(10, () => { window.slots[0] = { name: 'lime_stained_glass_pane' } })
    assert.equal(await menus.opened('filled menu'), window)
    assert.equal(window.slots[0].name, 'lime_stained_glass_pane')
})

test('closing fails when Escape opens no parent menu, and when nothing is open', async () => {
    const { bot } = fakeBot()
    const menus = adaptMenus(context, bot, { timeoutMs: 30 })
    bot.currentWindow = menu(5)
    await assert.rejects(menus.close('parent menu'), /Timed out waiting for parent menu/)
    await assert.rejects(menus.close('parent menu'), /has no open menu/)
})
