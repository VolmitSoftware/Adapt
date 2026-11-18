import conservation from './demolition-transform-conservation.mjs'

export default {
  name: 'adapt-demolition-transform-profile',
  description: 'Verify transformed-ground tool conservation with the complete 21-adaptation learned profile.',
  async run(context) {
    context.report.demolitionProfile = true
    await conservation.run(context)
  },
}
