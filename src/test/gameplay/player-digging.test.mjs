import assert from 'node:assert/strict'
import test from 'node:test'
import { diggingEnchantments, synchronizeDiggingEnchantments } from './player-digging.mjs'

const item = (type, enchantments) => ({ type, componentMap: new Map([
    ['enchantments', { data: { enchantments } }],
]) })

test('component enchantments retain actual tool and helmet levels', () => {
    const registry = { enchantments: { 2: { name: 'efficiency' }, 8: { name: 'unbreaking' }, 3: { name: 'aqua_affinity' } } }
    const bot = { registry }
    assert.deepEqual(diggingEnchantments(bot, item(100, [{ id: 2, level: 5 }, { id: 8, level: 3 }])),
        [{ name: 'efficiency', lvl: 5 }, { name: 'unbreaking', lvl: 3 }])
    assert.deepEqual(diggingEnchantments(bot, item(101, [{ id: 3, level: 1 }])), [{ name: 'aqua_affinity', lvl: 1 }])
    assert.deepEqual(diggingEnchantments(bot, item(100, [])), [])
    assert.deepEqual(diggingEnchantments(bot, null), [])
    assert.throws(() => diggingEnchantments(bot, item(100, [{ id: 7, level: 1 }])), /Invalid digging enchantment/)
})

test('dig calculation preserves real tool, environment and effects and cleanup restores Mineflayer', () => {
    const originalDigTime = () => 999
    const effects = { 3: { amplifier: 1 } }
    const bot = {
        digTime: originalDigTime,
        registry: { enchantments: { 2: { name: 'efficiency' }, 3: { name: 'aqua_affinity' } } },
        heldItem: item(100, [{ id: 2, level: 5 }]),
        inventory: { slots: [item(101, [{ id: 3, level: 1 }])] },
        getEquipmentDestSlot: () => 0,
        game: { gameMode: 'survival' },
        _getBlockAtEyeLevel: () => ({ name: 'water' }),
        entity: { onGround: false, effects },
    }
    const stop = synchronizeDiggingEnchantments(bot)
    let argumentsSeen
    assert.equal(bot.digTime({ digTime(...args) { argumentsSeen = args; return 150 } }), 150)
    assert.deepEqual(argumentsSeen, [100, false, true, true,
        [{ name: 'efficiency', lvl: 5 }, { name: 'aqua_affinity', lvl: 1 }], effects])
    stop()
    assert.equal(bot.digTime, originalDigTime)
})
