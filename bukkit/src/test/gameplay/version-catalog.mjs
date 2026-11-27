import { existsSync, readFileSync } from 'node:fs'
import { basename } from 'node:path'
import { fileURLToPath } from 'node:url'
import { contentSince, serverVersion } from './version-ids.mjs'

// Per-version adaptation catalog (generated from core's catalog of disabled and emulated adaptations). Formats:
// JSON  {"<minecraft version>": [{"name": "<adaptation>", "status": "disabled|emulated|partial", "reason": "<text>"}, ...], ...}
// TSV   header "minecraft<TAB>adaptation<TAB>status<TAB>reason", one row per adaptation and version.
// Core  core/src/test/resources/platform-support/<minecraft version>.tsv as PlatformSupportCatalogTest writes it: header
//       "skill<TAB>adaptation<TAB>status<TAB>reason", version from the file name, "works" rows ignored. Used by default
//       when ADAPT_QA_VERSION_CATALOG is unset and core has a file for the server version (set it empty to opt out).
// disabled: the adaptation does not exist on that version, its contracts report N/A with the reason.
// emulated: the adaptation runs its normal contracts against the emulated behaviour; the reason is printed as a note.
// partial: the adaptation runs its normal contracts; the reason names the part that version lacks, printed as a note.

const STATUSES = new Set(['disabled', 'emulated', 'partial'])
const NAME = /^[a-z0-9_.-]+:[a-z0-9_.\/-]+$/

function entry(version, name, status, reason, where) {
  if (!/^\d+\.\d+(\.\d+)?$/.test(version ?? '')) throw new Error(`${where}: invalid Minecraft version ${JSON.stringify(version)}`)
  if (!NAME.test(name ?? '')) throw new Error(`${where}: invalid adaptation name ${JSON.stringify(name)}`)
  if (!STATUSES.has(status)) throw new Error(`${where}: status must be disabled, emulated or partial, got ${JSON.stringify(status)}`)
  if (typeof reason !== 'string' || !reason.trim()) throw new Error(`${where}: ${name} needs a reason`)
  return { version, name, status, reason: reason.trim() }
}

const CORE_CATALOGS = fileURLToPath(new URL('../../../../core/src/test/resources/platform-support/', import.meta.url))

/** Core's golden catalog for a version, or undefined when core has none (26.x). */
export function coreCatalogPath(version, directory = CORE_CATALOGS) {
  const path = `${directory.replace(/\/$/, '')}/${version}.tsv`
  return existsSync(path) ? path : undefined
}

/** Parses catalog text (JSON, TSV or core TSV with its version) into entries; rejects duplicates and malformed rows. */
export function parseVersionCatalog(text, format, version) {
  const entries = []
  if (format === 'json') {
    const data = JSON.parse(text)
    if (!data || typeof data !== 'object' || Array.isArray(data)) throw new Error('JSON catalog must be an object keyed by Minecraft version')
    for (const [version, rows] of Object.entries(data)) {
      if (!/^\d+\.\d+(\.\d+)?$/.test(version)) throw new Error(`invalid Minecraft version ${JSON.stringify(version)}`)
      if (!Array.isArray(rows)) throw new Error(`${version}: expected an array of adaptations`)
      rows.forEach((row, index) => entries.push(entry(version, row?.name, row?.status, row?.reason, `${version}[${index}]`)))
    }
  } else if (format === 'tsv') {
    const lines = text.split(/\r?\n/).filter(line => line.trim() && !line.startsWith('#'))
    if (lines[0]?.split('\t').join(',') !== 'minecraft,adaptation,status,reason') throw new Error('TSV catalog header must be: minecraft, adaptation, status, reason')
    lines.slice(1).forEach((line, index) => {
      const [version, name, status, ...reason] = line.split('\t')
      entries.push(entry(version, name, status, reason.join('\t'), `line ${index + 2}`))
    })
  } else if (format === 'core') {
    const lines = text.split(/\r?\n/).filter(line => line.trim())
    if (lines[0]?.split('\t').join(',') !== 'skill,adaptation,status,reason') throw new Error('Core catalog header must be: skill, adaptation, status, reason')
    lines.slice(1).forEach((line, index) => {
      const [, name, status, ...reason] = line.split('\t')
      if (status !== 'works') entries.push(entry(version, name, status, reason.join('\t'), `line ${index + 2}`))
    })
  } else throw new Error(`Unknown catalog format: ${format}`)
  const seen = new Set()
  for (const item of entries) {
    const key = `${item.version} ${item.name}`
    if (seen.has(key)) throw new Error(`Duplicate catalog entry: ${item.name} on ${item.version}`)
    seen.add(key)
  }
  return entries
}

/** Entries for one server version, keyed by adaptation name. */
export function catalogForVersion(entries, version) {
  return new Map(entries.filter(item => item.version === version).map(item => [item.name, item]))
}

/**
 * Reads the catalog for the given version: ADAPT_QA_VERSION_CATALOG when set (format from the extension, a .tsv with
 * core's header is read as core's file for the version in its name), else core's file for the version; empty when neither.
 */
export function loadVersionCatalog(version, path = process.env.ADAPT_QA_VERSION_CATALOG ?? coreCatalogPath(version)) {
  if (!path) return new Map()
  const text = path.endsWith('.json') || path.endsWith('.tsv') ? readFileSync(path, 'utf8') : undefined
  if (text === undefined) throw new Error(`ADAPT_QA_VERSION_CATALOG must be a .json or .tsv file: ${path}`)
  if (path.endsWith('.json')) return catalogForVersion(parseVersionCatalog(text, 'json'), version)
  if (!text.startsWith('skill\t')) return catalogForVersion(parseVersionCatalog(text, 'tsv'), version)
  return catalogForVersion(parseVersionCatalog(text, 'core', basename(path, '.tsv')), version)
}

/** The version catalog entry (disabled or emulated) of one adaptation on this server; undefined when it works natively. */
export function versionEntry(name, version = serverVersion(), catalog = loadVersionCatalog(version)) {
  return catalog.get(name)
}

/**
 * Why a scenario step cannot run on this server, or undefined when it can: a named adaptation the version catalog
 * disables, or vanilla content (version-ids.mjs contentSince) newer than the server.
 */
export function stepBlocker({ adaptations = [], content = [] }, version = serverVersion(), catalog = loadVersionCatalog(version)) {
  const reasons = adaptations.filter(name => catalog.get(name)?.status === 'disabled')
    .map(name => `${name} is disabled on Minecraft ${version} (${catalog.get(name).reason})`)
  for (const [kind, name] of content) {
    const since = contentSince(kind, name, version)
    if (since) reasons.push(`${kind} minecraft:${name} does not exist before Minecraft ${since}`)
  }
  return reasons.length ? reasons.join('; ') : undefined
}

/** Records a step that cannot run here: prints "[N/A] <id>: <reason>" (platform-run.sh lists it under CASES) and adds it to report.notApplicable. */
export function notApplicable(context, id, title, reason) {
  context.report.notApplicable ??= []
  context.report.notApplicable.push({ id, title, reason })
  process.stderr.write(`[N/A] ${id}: ${reason}\n`)
}

/** Runs a scenario step unless stepBlocker names a reason, which is reported through notApplicable. Returns true when the step ran. */
export async function versionStep(context, id, needs, title, action) {
  const blocker = stepBlocker(needs)
  if (blocker) {
    notApplicable(context, id, title, blocker)
    return false
  }
  await context.step(title, action)
  return true
}

/**
 * Decides how one matrix adaptation is handled on this server. Returns {run: true} or {run: false, note} for N/A.
 * Throws when the live catalog contradicts the version catalog (drift between core and the harness input).
 */
export function contractPlan(name, live, versionEntry, version) {
  if (versionEntry?.status === 'disabled') {
    if (live?.enabled) throw new Error(`${name} is disabled on ${version} by the version catalog but the server enables it`)
    return { run: false, note: `disabled on Minecraft ${version}: ${versionEntry.reason}` }
  }
  if (!live) return { run: false }
  return versionEntry ? { run: true, note: `${versionEntry.status} on Minecraft ${version}: ${versionEntry.reason}` } : { run: true }
}
