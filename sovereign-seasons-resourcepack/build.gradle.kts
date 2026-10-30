plugins {
    `java-library`
    id("com.gradleup.shadow") version "8.3.6"
}

group = "com.sovereignseasons"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    // Core and the other modules relocate their own FoliaLib copies, so this plugin shades and relocates a separate one
    implementation("com.github.TechnicallyCoded:FoliaLib:0.4.4")
    compileOnly(project(":sovereign-seasons-core"))

    testImplementation("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    testImplementation(project(":sovereign-seasons-core"))
    // The pack's crate models must match the custom model data that Logistics gives its crates
    testImplementation(project(":sovereign-seasons-logistics"))
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

// One zip per pack folder in src/pack, reproducible (fixed timestamps and order) so its SHA-1 only changes
// when its content does: clients keep their cached copy across restarts and rebuilds.
val packNames = listOf("base", "spring", "autumn", "winter")
val packZips = packNames.map { name ->
    tasks.register<Zip>("zip${name.replaceFirstChar { it.uppercase() }}Pack") {
        from("src/pack/$name")
        archiveFileName.set("sovereign-seasons-$name.zip")
        destinationDirectory.set(layout.buildDirectory.dir("packs"))
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    compileTestJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
        // The packs are bundled in the plugin under packs/
        packZips.forEach { zip -> from(zip) { into("packs") } }
    }

    jar {
        // The plugin JAR is the shadow JAR; the plain JAR gets a classifier so the two never overwrite each other
        archiveClassifier.set("plain")
    }

    shadowJar {
        archiveClassifier.set("")
        relocate("com.tcoded.folialib", "com.sovereignseasons.resourcepack.libs.folialib")
    }

    build {
        dependsOn(shadowJar)
    }

    test {
        useJUnitPlatform()
        // Pack validation tests read the pack sources directly
        systemProperty("sovereignseasons.packSources", file("src/pack").absolutePath)
    }
}
