import java.io.File

buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        constraints {
            classpath("org.apache.logging.log4j:log4j-core:2.25.3")
            classpath("org.apache.logging.log4j:log4j-api:2.25.3")
            classpath("org.codehaus.plexus:plexus-utils:4.0.3")
        }
    }
}

plugins {
    `java-library`
}

allprojects {
    group = "com.sovereignseasons"
    version = "1.0.0-SNAPSHOT"

    repositories {
        val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
        if (isWindows) {
            val localM2 = File("C:/dev/tools/m2-repo")
            if (localM2.isDirectory) {
                maven {
                    name = "local-tools-m2"
                    url = localM2.toURI()
                }
            }
        }
        mavenCentral()
        maven {
            name = "papermc"
            url = uri("https://repo.papermc.io/repository/maven-public/")
        }
        maven {
            name = "sonatype"
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
        }
        maven {
            name = "jitpack"
            url = uri("https://jitpack.io")
        }
    }
}

subprojects {
    plugins.withType<JavaPlugin> {
        dependencies {
            constraints {
                add("implementation", "org.apache.commons:commons-lang3:3.21.0")
            }
        }
    }
}
