plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    id("maven-publish")
    id("signing")
    alias(libs.plugins.publish)
}

import org.gradle.plugin.compatibility.compatibility

group = "education.cccp"
version = libs.plugins.newpipe.get().version

// Utilisation de la toolchain comme dans ton modèle
kotlin.jvmToolchain(11)

java {
    withJavadocJar()
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
    gradlePluginPortal()
}

// Définition du SourceSet FunctionalTest
val functionalTest by sourceSets.creating {
    kotlin.srcDir("src/functionalTest/kotlin")
    resources.srcDir("src/functionalTest/resources")
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

// Configuration pour l'héritage des dépendances
configurations {
    val functionalTestImplementation by getting {
        extendsFrom(configurations.implementation.get())
        extendsFrom(configurations.testImplementation.get())
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(gradleApi())

    implementation(libs.bundles.newpipe)
    implementation(libs.bundles.coroutines)

    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Correction du doublon logback-test.xml
tasks.named<ProcessResources>("processFunctionalTestResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

gradlePlugin {
    testSourceSets(functionalTest)
    plugins {
        create("newpipePlugin") {
            id = "education.cccp.newpipe"
            implementationClass = "com.cheroliv.newpipe.DownloaderPlugin"
            displayName = "NewPipe YouTube Music Downloader"
            description = "Downloads music from YouTube and converts to MP3 with ID3 tags. Supports OAuth2 authentication for member-only videos, age-restricted content, and private playlists. Pure JVM — no Python/yt-dlp dependency. Uses NewPipeExtractor + FFmpeg (local or Docker)."
            tags.set(listOf("youtube", "music", "mp3", "downloader", "newpipe", "ffmpeg", "docker", "oauth2", "kotlin-dsl", "audio"))
            compatibility {
                features {
                    configurationCache = false
                }
            }
        }
    }
    website = "https://cheroliv.com"
    vcsUrl = "https://github.com/cccp-education/newpipe-gradle.git"
}

val functionalTestTask = tasks.register<Test>("functionalTest") {
    description = "Runs functional tests."
    group = "verification"
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    useJUnitPlatform()
}

tasks.check { dependsOn(functionalTestTask) }

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        withType<MavenPublication> {
            if (name == "pluginMaven") {
                pom {
                    name.set(gradlePlugin.plugins.getByName("newpipePlugin").displayName)
                    description.set(gradlePlugin.plugins.getByName("newpipePlugin").description)
                    url.set(gradlePlugin.website.get())
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("cccp-education")
                            name.set("CCCP Education")
                            email.set("cccp.edu@gmail.com")
                        }
                    }
                    scm {
                        connection.set(gradlePlugin.vcsUrl.get())
                        developerConnection.set(gradlePlugin.vcsUrl.get())
                        url.set(gradlePlugin.vcsUrl.get())
                    }
                }
            }
        }
    }
}

signing {
    if (System.getenv("CI") != "true" && !version.toString().endsWith("-SNAPSHOT")) {
        sign(publishing.publications)
    }
    useGpgCmd()
}
