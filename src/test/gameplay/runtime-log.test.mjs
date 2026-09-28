import test from 'node:test'
import assert from 'node:assert/strict'
import { appendFile, mkdtemp, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { errorsSince, logPosition, serverErrors } from './runtime-log.mjs'

test('runtime gate catches real event failures without mistaking Iris instrumentation notices for plugin errors', () => {
    const lines = [
        '[18:10:32] [Server thread/ERROR]: [Minecraft] Could not pass event BlockDropItemEvent to Adapt',
        "Caused by: java.lang.IllegalArgumentException: CARROTS isn't an item",
        '[18:06:41] [Server thread/INFO]: Note: JVM [Attach Listener/ERROR] [STDERR] warning lines are expected.',
        '[18:06:41] [Attach Listener/ERROR]: [Minecraft] [STDERR] [sun.instrument.InstrumentationImpl] WARNING: A Java agent has been loaded dynamically (plugins/Iris/agent.jar)',
        '[18:06:42] [Attach Listener/ERROR]: Unexpected instrumentation failure',
    ]
    assert.deepEqual(serverErrors(lines.join('\n')), [lines[0], lines[1], lines[4]])
})

test('case log evidence excludes previous errors and detects truncation', async () => {
    const directory = await mkdtemp(join(tmpdir(), 'adapt-runtime-'))
    const file = join(directory, 'server.log')
    try {
        await writeFile(file, '[Server thread/ERROR]: previous case\n')
        const start = await logPosition(file)
        await appendFile(file, '[Server thread/INFO]: expected action\n[Server thread/ERROR]: current failure\n')
        assert.deepEqual((await errorsSince(start)).errors, ['[Server thread/ERROR]: current failure'])
        await writeFile(file, '')
        await assert.rejects(errorsSince(start), /rotated/)
    } finally { await rm(directory, { recursive: true, force: true }) }
})
