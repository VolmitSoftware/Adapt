import { readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, join } from 'node:path'

const BOT_PROTOCOL_VERSION = '26.1'
const DIRECT_VERSIONS = new Set(['26.1.2'])
const KNOWN_PACK_FORMATS = new Map([['1.21.1', 48], ['1.20.1', 15]])
const FIRST_RANGED_PACK_FORMAT = 82

/** Splits a release version such as 1.20.1 or 26.3 into numbers; rejects snapshots and other formats. */
export function parseVersion(version) {
  if (!/^\d+\.\d+(\.\d+)?$/.test(version ?? '')) throw new Error(`Unsupported Minecraft version: ${version}`)
  const [major, minor, patch = 0] = version.split('.').map(Number)
  return { major, minor, patch }
}

/** Orders two release versions (negative when left is older). */
export function compareVersions(left, right) {
  const a = parseVersion(left)
  const b = parseVersion(right)
  return a.major - b.major || a.minor - b.minor || a.patch - b.patch
}

/** Java major the server runs on: 25 for 26.x, 21 from 1.20.5, 17 from 1.18 (Mojang minimums). */
export function serverJavaMajor(version) {
  const { major, minor, patch } = parseVersion(version)
  if (major >= 26) return 25
  if (major === 1 && (minor > 20 || (minor === 20 && patch >= 5))) return 21
  if (major === 1 && minor >= 18) return 17
  throw new Error(`No Java mapping for Minecraft ${version}`)
}

/** Data pack format: gradle/targets.json when it lists the version, else the vanilla value for the legacy targets. */
export function packFormat(version, targets = {}) {
  const configured = targets[version]?.dataPackFormat
  if (Number.isInteger(configured)) return configured
  if (KNOWN_PACK_FORMATS.has(version)) return KNOWN_PACK_FORMATS.get(version)
  throw new Error(`No data pack format for Minecraft ${version} in gradle/targets.json`)
}

/** pack.mcmeta for the fixture data pack; formats before 82 only understand pack_format. */
export function packMeta(version, targets = {}) {
  const format = packFormat(version, targets)
  const pack = { description: 'Adapt gameplay fixture dimensions' }
  if (format < FIRST_RANGED_PACK_FORMAT) pack.pack_format = format
  else Object.assign(pack, { min_format: format, max_format: format })
  return JSON.stringify({ pack })
}

/**
 * How the bots reach the server: 26.1 mineflayer clients, through ViaProxy on every version but 26.1.2 (same protocol).
 * Every scenario therefore sees 26.1 registry names and packet layouts on every server version; the raw packet decoders
 * (particles, attributes, input, velocity, passengers, windows) only understand 26.1.
 */
export function botRoute(version) {
  parseVersion(version)
  return { clientVersion: BOT_PROTOCOL_VERSION, proxy: !DIRECT_VERSIONS.has(version) }
}

const LOADER_LABELS = { fabric: 'Fabric', forge: 'Forge', neoforge: 'NeoForge' }

/** Version range a Bukkit jar name declares: `Adapt v<ver> [CraftBukkit] <low>[-<high>].jar`. */
export function bukkitJarRange(fileName) {
  const match = /^Adapt v\S+ \[CraftBukkit\] (\d+(?:\.\d+){1,2})(?:-(\d+(?:\.\d+){1,2}))?\.jar$/.exec(fileName)
  return match ? { low: match[1], high: match[2] ?? match[1] } : undefined
}

/** Newest Bukkit jar (by mtime) in the given directories whose declared range covers the version. */
export function selectBukkitJar(version, candidates) {
  return candidates
    .filter(candidate => {
      const range = bukkitJarRange(candidate.name)
      return range && compareVersions(range.low, version) <= 0 && compareVersions(version, range.high) <= 0
    })
    .sort((left, right) => right.mtimeMs - left.mtimeMs)[0]
}

/** True for `Adapt v<ver> [<Loader>] <mc>+<loader version>.jar` built for exactly this loader and version. */
export function isModJar(fileName, loader, version) {
  const escaped = version.replaceAll('.', '\\.')
  return new RegExp(`^Adapt v\\S+ \\[${LOADER_LABELS[loader]}\\] ${escaped}\\+\\S+\\.jar$`).test(fileName)
}

/** Loaders whose build directories may hold the jar a server runs. NeoForge 1.20.1 (47.1.x) loads the Forge 1.20.1 jar. */
export function modJarLoaders(platform, version) {
  if (!LOADER_LABELS[platform]) throw new Error(`Not a mod loader: ${platform}`)
  return platform === 'neoforge' && version === '1.20.1' ? ['neoforge', 'forge'] : [platform]
}

function listJars(directory) {
  try {
    return readdirSync(directory).filter(name => name.endsWith('.jar'))
      .map(name => ({ name, path: join(directory, name), mtimeMs: statSync(join(directory, name)).mtimeMs }))
  } catch {
    return []
  }
}

/**
 * Adapt and fixture jars for a run. Bukkit: the newest covering jar in build/libs, fixture beside it. Mods:
 * adapters/<loader>/build/<mc>/libs, fixture in the sibling gameplay directory.
 */
export function findArtifacts(platform, version, projectRoot) {
  if (platform === 'paper' || platform === 'folia') {
    const libs = join(projectRoot, 'build/libs')
    const jar = selectBukkitJar(version, listJars(libs))
    if (!jar) throw new Error(`No Bukkit Adapt jar covering ${version} in ${libs}`)
    return { adapt: jar.path, fixture: join(dirname(jar.path), 'AdaptGameplayFixture.jar') }
  }
  for (const loader of modJarLoaders(platform, version)) {
    const libs = join(projectRoot, 'adapters', loader, 'build', version, 'libs')
    const jar = listJars(libs).filter(candidate => isModJar(candidate.name, loader, version)).sort((left, right) => right.mtimeMs - left.mtimeMs)[0]
    if (jar) return { adapt: jar.path, fixture: join(dirname(libs), 'gameplay', 'AdaptGameplayFixture.jar') }
  }
  throw new Error(`No Adapt jar for ${platform} ${version} under adapters/{${modJarLoaders(platform, version).join(',')}}/build/${version}/libs`)
}

function readTargets(path) {
  return JSON.parse(readFileSync(path, 'utf8'))
}

/** Shell-friendly facts for platform-run.sh: KEY=value lines. */
export function shellProfile(version) {
  const route = botRoute(version)
  return [
    `profile_java_major=${serverJavaMajor(version)}`,
    `profile_client_version=${route.clientVersion}`,
    `profile_proxy=${route.proxy}`,
  ].join('\n')
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const [command, version, ...rest] = process.argv.slice(2)
  try {
    if (command === 'shell') console.log(shellProfile(version))
    else if (command === 'pack-meta') console.log(packMeta(version, readTargets(rest[0])))
    else if (command === 'artifacts') {
      const found = findArtifacts(rest[0], version, rest[1])
      console.log(`${found.adapt}\n${found.fixture}`)
    } else throw new Error('Usage: node version-profile.mjs shell <mc> | pack-meta <mc> <targets.json> | artifacts <mc> <platform> <project root>')
  } catch (error) {
    console.error(error.message)
    process.exitCode = 2
  }
}
