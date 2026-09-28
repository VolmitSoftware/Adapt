import test from 'node:test'
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { captureEvidenceManifest, writeEvidenceManifest } from './evidence-manifest.mjs'

test('evidence manifests identify deployed artifacts and harness revisions independently', async () => {
    const root = await mkdtemp(join(tmpdir(), 'adapt-evidence-'))
    try {
        const harnessDirectory = join(root, 'harness')
        await mkdir(join(root, 'plugins/Adapt'), { recursive: true })
        await mkdir(harnessDirectory)
        for (const [path, contents] of Object.entries({ 'server.jar': 'paper', '.server-source': 'isolated=true',
            'server.properties': 'online-mode=false', 'plugins/Adapt.jar': 'production',
            'plugins/AdaptGameplayFixture.jar': 'observer', 'plugins/Adapt/adapt.toml': 'enabled=true',
            'harness/case.mjs': 'first trigger' })) await writeFile(join(root, path), contents)
        const first = await captureEvidenceManifest(root, { harnessDirectory, scope: { cases: ['crafting-deconstruction'] } })
        assert.equal(first.artifacts.find(file => file.path === 'plugins/Adapt.jar').sha256,
            createHash('sha256').update('production').digest('hex'))
        assert.deepEqual(first.scope.cases, ['crafting-deconstruction'])
        await writeFile(join(harnessDirectory, 'case.mjs'), 'corrected natural trigger')
        const retry = await captureEvidenceManifest(root, { harnessDirectory })
        assert.deepEqual(retry.artifacts, first.artifacts)
        assert.notDeepEqual(retry.harness, first.harness)
        await writeFile(join(root, 'plugins/Adapt.jar'), 'different production')
        const changed = await captureEvidenceManifest(root, { harnessDirectory })
        assert.notDeepEqual(changed.artifacts, retry.artifacts)
        const destination = join(root, 'manifest.json')
        const reference = await writeEvidenceManifest(destination, changed)
        assert.equal(reference.sha256, createHash('sha256').update(await readFile(destination)).digest('hex'))
        await rm(join(root, 'server.jar'))
        await assert.rejects(captureEvidenceManifest(root, { harnessDirectory }), /ENOENT/)
    } finally { await rm(root, { recursive: true, force: true }) }
})
