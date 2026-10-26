import java.io.File

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
