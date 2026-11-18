import { existsSync } from 'node:fs'
import { join } from 'node:path'

export function adaptConfigDirectory(server) {
    const plugin = join(server, 'plugins', 'Adapt')
    return existsSync(plugin) ? plugin : join(server, 'config', 'adapt')
}
