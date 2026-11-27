import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile, readdir } from 'node:fs/promises'

const root = new URL('../../../', import.meta.url)
const upgradeRoot = '../content/src/main/java/art/arcane/adapt/content/'

async function javaFiles(directory) {
  const entries = await readdir(new URL(directory, root), { withFileTypes: true }).catch(error => {
    if (error.code === 'ENOENT') return []
    throw error
  })
  const files = await Promise.all(entries.map(entry => entry.isDirectory()
    ? javaFiles(`${directory}${entry.name}/`)
    : entry.name.endsWith('.java') ? [`${directory}${entry.name}`] : []))
  return files.flat()
}

async function skillFiles() {
  const entries = await readdir(new URL('./upgrades/', import.meta.url)).catch(error => {
    if (error.code === 'ENOENT') return []
    throw error
  })
  return entries.filter(name => name.endsWith('.json')).sort()
}

const originalIds = new Set()
for (const file of await javaFiles(`${upgradeRoot}adaptation/`)) {
  const code = await readFile(new URL(file, root), 'utf8')
  if (/extends\s+(?:Simple|Legacy)Adaptation</.test(code)) {
    const id = /\bsuper\(ID\)/.test(code)
      ? code.match(/\bpublic\s+static\s+final\s+Id\s+ID\s*=\s*Id\.adapt\("([^"]+)"\);/)?.[1]
      : code.match(/super\(Id\.adapt\("([^"]+)"\)\)/)?.[1]
    if (id) originalIds.add(`adapt:${id}`)
  }
}

test('every upgraded adaptation has one entry in its own skill file, and every entry matches its source', async () => {
  const concrete = new Map()
  for (const file of await javaFiles(`${upgradeRoot}adaptation/`)) {
    const code = await readFile(new URL(file, root), 'utf8')
    const name = code.match(/public\s+(?:final\s+)?class\s+(\w+)\s+extends\s+SkillUpgrade</)?.[1]
    if (!name) continue
    const id = /\bsuper\(ID\)/.test(code)
      ? code.match(/\bpublic\s+static\s+final\s+Id\s+ID\s*=\s*Id\.adapt\("([a-z0-9_\/]+)"\);/)?.[1]
      : code.match(/super\(Id\.adapt\("([a-z0-9_\/]+)"\)\)/)?.[1]
    assert.ok(id, `Missing literal upgrade id in ${file}`)
    concrete.set(`adapt:${id}`, { file, name, code })
  }

  const documented = new Set()
  for (const fileName of await skillFiles()) {
    const skill = fileName.replace(/\.json$/, '')
    const entry = JSON.parse(await readFile(new URL(`./upgrades/${fileName}`, import.meta.url), 'utf8'))
    assert.equal(entry.skill, skill, `${fileName} names another skill`)
    for (const upgrade of entry.upgrades) {
      assert.ok(upgrade.name.startsWith(`adapt:${skill}/upgrade/`), `${upgrade.name} is not a ${skill} upgrade`)
      assert.ok(!documented.has(upgrade.name), `Duplicate entry: ${upgrade.name}`)
      documented.add(upgrade.name)
      const source = concrete.get(upgrade.name)
      assert.ok(source, `No source for ${upgrade.name}`)
      assert.equal(upgrade.source, source.file)
      assert.equal(upgrade.class, source.name)
      for (const fused of upgrade.fuses) {
        assert.ok(originalIds.has(fused), `${upgrade.name} fuses unknown adaptation ${fused}`)
      }
      const triggers = [...source.code.matchAll(/@Handle[^\n]*\n\s*public\s+void\s+\w+\((\w+)\s/g)].map(match => match[1])
      assert.deepEqual([...new Set(triggers)].sort(), [...upgrade.triggers].sort(), `${upgrade.name} triggers`)
    }
  }
  assert.deepEqual([...documented].sort(), [...concrete.keys()].sort())
})
