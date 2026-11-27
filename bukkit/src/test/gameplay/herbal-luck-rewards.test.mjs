import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = readFileSync(new URL('../../../../content/src/main/java/art/arcane/adapt/content/adaptation/herbalism/HerbalismLuck.java', import.meta.url), 'utf8')

function pool(name) {
    const definition = source.match(new RegExp(`\\b${name}\\s*=\\s*List\\.of\\(([\\s\\S]*?)\\);`))
    assert.ok(definition, `${name} reward pool exists`)
    return [...definition[1].matchAll(/Id\.minecraft\("([a-z_]+)"\)/g)].map(match => match[1].toUpperCase())
}

test('Herbalist Luck food reward definitions use harvested items instead of crop blocks', () => {
    assert.deepEqual(pool('LUCK_FOOD').sort(), ['APPLE', 'BEETROOT', 'CARROT', 'POTATO'])
})

test('Herbalist Luck grass reward definitions retain plantable seed items', () => {
    assert.deepEqual(pool('LUCK_SEEDS').sort(), ['COCOA_BEANS', 'MELON_SEEDS', 'PUMPKIN_SEEDS'])
})
