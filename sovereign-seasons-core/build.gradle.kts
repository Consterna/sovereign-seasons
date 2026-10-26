plugins {
    `java-library`
    `maven-publish`
    id("com.gradleup.shadow") version "8.3.11"
}

group = "com.sovereignseasons"
version = "1.0.0"

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "com.sovereignseasons"
            artifactId = "sovereign-seasons-core"
            version = "1.0.0"

            pom {
                name.set("Sovereign Seasons Core")
                description.set("Advanced Minecraft seasonal mechanics plugin for Paper/Folia")
                url.set("https://github.com/Consterna/sovereign-seasons")
                licenses {
                    license {
                        name.set("GNU General Public License v3.0")
                        url.set("https://www.gnu.org/licenses/gpl-3.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("Consterna")
                        name.set("Cristian")
                        email.set("cristianguerrero1958@outlook.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/Consterna/sovereign-seasons.git")
                    developerConnection.set("scm:git:ssh://github.com:Consterna/sovereign-seasons.git")
                    url.set("https://github.com/Consterna/sovereign-seasons")
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Consterna/sovereign-seasons")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    implementation("com.github.TechnicallyCoded:FoliaLib:0.4.4")

    testImplementation("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    testImplementation("com.github.TechnicallyCoded:FoliaLib:0.4.4")
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
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
        // FoliaLib can be shaded if needed
        relocate("com.tcoded.folialib", "com.sovereignseasons.core.libs.folialib")
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
