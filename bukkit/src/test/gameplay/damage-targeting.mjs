import tragoul from './tragoul-targeting.mjs'
import area from './area-targeting.mjs'
import projectile from './projectile-targeting.mjs'

export default {
    name: 'adapt-damage-targeting',
    description: 'Validate configured passive-mob targeting and permanent summon protection across area, chain and projectile adaptations.',
    async run(context) {
        for (const suite of [tragoul, area, projectile]) await suite.run(context)
    },
}
