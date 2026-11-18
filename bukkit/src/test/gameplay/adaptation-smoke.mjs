import { runAdaptationSuite } from './all-adaptations.mjs'
import { commandAcrossWorlds } from './fixture-json.mjs'

export default {
  name: 'adapt-adaptation-smoke',
  description: 'Run catalog learning contracts and implemented activation cases, reporting incomplete coverage explicitly.',
  async run(context) {
    const irisWorld = process.env.ADAPT_QA_IRIS_WORLD
    if (irisWorld) await commandAcrossWorlds(context, `/iris create ${irisWorld}`, /Successfully created your world/i, 180000)
    await runAdaptationSuite(context, { requireComplete: false, caseNames: context.options.command?.split(',') })
  },
}
