import { runAdaptationSuite } from './all-adaptations.mjs'

export default {
  name: 'adapt-adaptation-smoke',
  description: 'Run catalog learning contracts and implemented activation cases, reporting incomplete coverage explicitly.',
  run: context => runAdaptationSuite(context, { requireComplete: false, caseNames: context.options.command?.split(',') }),
}
