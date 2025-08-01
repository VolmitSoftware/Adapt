plugins {
    java
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
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
    rename { "com/volmit/adapt/$it" }
    expand(inputs.properties)
}

sourceSets.main {
    java.srcDir(generateTemplates.map { it.outputs })
}