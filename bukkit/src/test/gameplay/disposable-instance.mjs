const RUNNER_INSTANCE = /^[a-z0-9]+-adapt-(paper|folia|fabric|forge|neoforge)-[0-9]+(-s[0-9]+)?-[0-9]+(-[0-9]+)?$/

export function disposableInstance(instance, ...prefixes) {
    return typeof instance === 'string' && (RUNNER_INSTANCE.test(instance) || prefixes.some(prefix => instance.startsWith(prefix)))
}
