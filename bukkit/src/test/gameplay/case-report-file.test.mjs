import test from 'node:test'
import assert from 'node:assert/strict'
import { caseReportFile } from './all-adaptations.mjs'

test('a namespaced case id names a plain file inside the case report folder', () => {
    const folder = new URL('file:///reports/cases-1-ab/')
    const file = new URL(caseReportFile('adapt:seaborne/storm_strike'), folder)
    assert.equal(file.protocol, 'file:')
    assert.equal(file.href, 'file:///reports/cases-1-ab/adapt_seaborne_storm_strike.json')
})

test('the summary keeps its name', () => {
    assert.equal(caseReportFile('summary'), 'summary.json')
})
