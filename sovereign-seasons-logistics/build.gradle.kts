plugins {
    `java-library`
    id("com.gradleup.shadow") version "8.3.11"
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
    // Core and Pantry relocate their own FoliaLib copies, so Logistics shades and relocates a separate one
    implementation("com.github.TechnicallyCoded:FoliaLib:0.4.4")
    compileOnly(project(":sovereign-seasons-core"))
    // Optional integration: only used when Pantry is installed
    compileOnly(project(":sovereign-seasons-pantry"))

    testImplementation("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    testImplementation(project(":sovereign-seasons-core"))
    testImplementation(project(":sovereign-seasons-pantry"))
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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

    jar {
        // The plugin JAR is the shadow JAR; the plain JAR gets a classifier so the two never overwrite each other
        archiveClassifier.set("plain")
    }

    shadowJar {
        archiveClassifier.set("")
        relocate("com.tcoded.folialib", "com.sovereignseasons.logistics.libs.folialib")
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
