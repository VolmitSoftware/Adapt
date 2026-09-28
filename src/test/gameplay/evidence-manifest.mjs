import { createHash } from 'node:crypto'
import { createReadStream } from 'node:fs'
import { readdir, stat, writeFile } from 'node:fs/promises'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

async function files(directory, accept, recursive = true) {
    let entries
    try { entries = await readdir(directory, { withFileTypes: true }) }
    catch (error) { if (error.code === 'ENOENT') return []; throw error }
    const result = []
    for (const entry of entries) {
        const path = join(directory, entry.name)
        if (entry.isDirectory() && recursive) result.push(...await files(path, accept))
        else if (!entry.isDirectory() && accept(entry.name)) result.push(path)
    }
    return result.sort()
}

async function digest(path, root) {
    const before = await stat(path)
    const hash = createHash('sha256')
    for await (const chunk of createReadStream(path)) hash.update(chunk)
    const after = await stat(path)
    if (before.size !== after.size || before.mtimeMs !== after.mtimeMs || before.ino !== after.ino) {
        throw new Error(`Evidence input changed while hashing: ${path}`)
    }
    return { path: relative(root, path), bytes: after.size, sha256: hash.digest('hex') }
}

export async function captureEvidenceManifest(serverDirectory, { scope = {}, harnessDirectory = dirname(fileURLToPath(import.meta.url)) } = {}) {
    const server = resolve(serverDirectory)
    const jars = [join(server, 'server.jar'), ...await files(join(server, 'versions'), name => name.endsWith('.jar')),
        ...await files(join(server, 'plugins'), name => name.endsWith('.jar'), false)]
    const configuration = [join(server, '.server-source'), join(server, 'server.properties'),
        ...await files(join(server, 'plugins/Adapt'), name => name.endsWith('.toml')),
        ...await files(join(server, 'config'), name => name.endsWith('.yml'))]
    const sources = await files(harnessDirectory, name => /\.(mjs|json|java|yml|sh)$/.test(name))
    const artifacts = []
    const settings = []
    const harness = []
    for (const path of jars.sort()) artifacts.push(await digest(path, server))
    for (const path of configuration.sort()) settings.push(await digest(path, server))
    for (const path of sources) harness.push(await digest(path, harnessDirectory))
    return { schema: 1, capturedAt: new Date().toISOString(), scope, serverDirectory: server,
        node: process.version, artifacts, configuration: settings, harness,
        limits: 'File digests identify observed inputs. They do not prove unchanged loaded classes, client rendering, or audible sound.' }
}

export async function writeEvidenceManifest(destination, manifest) {
    const bytes = JSON.stringify(manifest, null, 2)
    await writeFile(destination, bytes)
    return { file: destination instanceof URL ? fileURLToPath(destination) : resolve(destination),
        sha256: createHash('sha256').update(bytes).digest('hex') }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    const [serverDirectory, destination, phase] = process.argv.slice(2)
    if (!serverDirectory || !destination || !phase) throw new Error('Usage: evidence-manifest.mjs <server-directory> <output.json> <phase>')
    await writeEvidenceManifest(destination, await captureEvidenceManifest(serverDirectory, { scope: { phase } }))
}
