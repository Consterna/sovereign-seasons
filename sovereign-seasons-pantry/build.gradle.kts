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
    // Core relocates its own FoliaLib copy, so Pantry must shade and relocate a separate one
    implementation("com.github.TechnicallyCoded:FoliaLib:0.4.4")
    compileOnly(project(":sovereign-seasons-core"))

    testImplementation("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    testImplementation(project(":sovereign-seasons-core"))
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.7")
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

    shadowJar {
        archiveClassifier.set("")
        relocate("com.tcoded.folialib", "com.sovereignseasons.pantry.libs.folialib")
    }

    build {
        dependsOn(shadowJar)
    }

    test {
        useJUnitPlatform()
    }

    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
}
