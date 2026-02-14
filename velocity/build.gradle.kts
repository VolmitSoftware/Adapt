import io.github.slimjar.func.slimjarHelper

plugins {
    java
}

dependencies {
    compileOnly(libs.velocity)
    annotationProcessor(libs.velocity)

    compileOnly(slimjarHelper("velocity"))
    compileOnly(libs.lettuce)
    compileOnly(libs.toml4j)
    compileOnly(libs.fastutil)
}

val templateSource = file("src/main/templates")
val templateDest = layout.buildDirectory.dir("generated/sources/templates")
val generateTemplates = tasks.register<Copy>("generateTemplates") {
    inputs.properties(
        "id" to rootProject.name.lowercase(),
        "name" to rootProject.name,
        "version" to rootProject.version,
    )

    from(templateSource)
    into(templateDest)
    rename { "art/arcane/adapt/$it" }
    expand(inputs.properties)
}

sourceSets.main {
    java.srcDir(generateTemplates.map { it.outputs })
}
