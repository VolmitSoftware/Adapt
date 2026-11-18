import { open, readFile, stat } from 'node:fs/promises'
import { pathToFileURL } from 'node:url'

const FOREIGN_FOLIA_NOISE = [
    /\/ERROR\]: \[\] \[Gloss\] Gloss scheduler error\.$/,
    /^art\.arcane\.gloss\.condition\.RoleSnapshotPendingException: The owning region has not published this role value yet$/,
]

// Vanilla 26.2 WorldGenRegion check: the deep dark sculk patch spreads into a chunk two away from the one it decorates.
// Whether it fires depends on the order neighbouring chunks were decorated in; the fixture's exploration world hits it.
const VANILLA_WORLDGEN_NOISE = [
    /\/ERROR\]: \[net\.minecraft\.util\.Util\] Detected unsafe terrain read during worldgen: reading from chunk \[-?\d+, -?\d+\] while generating chunk \[-?\d+, -?\d+\] \(distance: 2, write radius: 1\), step: minecraft:features, currently generating: ResourceKey\[minecraft:worldgen\/placed_feature \/ minecraft:sculk_patch_deep_dark\]$/,
]

export function serverErrors(text) {
    return text.split(/\r?\n/).filter(line => /\/(ERROR|SEVERE)\]:|\[(ERROR|SEVERE)\]|^(Caused by:|[\w.$]+(?:Exception|Error):)/.test(line)
        && !/sun\.instrument\.InstrumentationImpl.*WARNING: A Java agent has been loaded dynamically/.test(line)
        && ![...FOREIGN_FOLIA_NOISE, ...VANILLA_WORLDGEN_NOISE].some(pattern => pattern.test(line.replace(/\u001b\[[0-9;]*m/g, ''))))
}

export async function logPosition(file) {
    const state = await stat(file)
    return { file, inode: state.ino, offset: state.size }
}

export async function errorsSince(position) {
    const handle = await open(position.file, 'r')
    try {
        const state = await handle.stat()
        if (state.ino !== position.inode || state.size < position.offset) throw new Error('Server log rotated during an adaptation case')
        const bytes = Buffer.alloc(state.size - position.offset)
        let read = 0
        while (read < bytes.length) {
            const result = await handle.read(bytes, read, bytes.length - read, position.offset + read)
            if (result.bytesRead === 0) throw new Error('Server log changed while reading case evidence')
            read += result.bytesRead
        }
        return { file: position.file, fromByte: position.offset, toByte: state.size, errors: serverErrors(bytes.toString('utf8')) }
    } finally { await handle.close() }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    const errors = serverErrors(await readFile(process.argv[2], 'utf8'))
    for (const error of errors) process.stdout.write(`${error}\n`)
    process.exitCode = errors.length ? 1 : 0
}
