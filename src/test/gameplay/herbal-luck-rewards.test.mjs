import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = readFileSync(new URL('../../main/java/art/arcane/adapt/content/item/ItemListings.java', import.meta.url), 'utf8')

function pool(name) {
    const definition = source.match(new RegExp(`\\b${name}\\s*=\\s*new KList<>\\(([\\s\\S]*?)\\);`))
    assert.ok(definition, `${name} reward pool exists`)
    return [...definition[1].matchAll(/Material\.([A-Z_]+)/g)].map(match => match[1])
}

test('Herbalist Luck food reward definitions use harvested items instead of crop blocks', () => {
    assert.deepEqual(pool('herbalLuckFood').sort(), ['APPLE', 'BEETROOT', 'CARROT', 'POTATO'])
})

test('Herbalist Luck grass reward definitions retain plantable seed items', () => {
    assert.deepEqual(pool('herbalLuckSeeds').sort(), ['COCOA_BEANS', 'MELON_SEEDS', 'PUMPKIN_SEEDS'])
})
