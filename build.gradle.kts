import java.net.URI

plugins {
    kotlin("multiplatform") version "1.8.21"
    kotlin("plugin.serialization") version "1.8.0"
    id("maven-publish")
    id("dev.petuska.npm.publish") version "3.3.1"
}

val ktorVersion = "2.3.0"
val coroutinesVersion = "1.7.1"

group = "io.github.mfrancza"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenLocal()
    maven {
        url = uri("https://maven.pkg.github.com/mfrancza/jwt-revocation-rules")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
    mavenCentral()
}

kotlin {
    jvmToolchain(11)
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform{
                excludeTags("integration")
            }
        }

        testRuns.create("integrationTest") {
            this.setExecutionSourceFrom(classpath = testRuns["test"].executionSource.classpath, testRuns["test"].executionSource.testClassesDirs)
        }.executionTask.configure {
            useJUnitPlatform{
                includeTags("integration")
            }
        }
    }
    js {
        compilations["main"].packageJson {}
        binaries.library()
        nodejs()
        browser()
        generateTypeScriptDefinitions()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.github.mfrancza:jwt-revocation-rules:1.2.0-SNAPSHOT")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
                implementation("io.ktor:ktor-client-auth:$ktorVersion")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.ktor:ktor-client-mock:$ktorVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
            }
        }
        val jvmMain by getting
        val jvmTest by getting
        val jsMain by getting
        val jsTest by getting
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = URI("https://maven.pkg.github.com/mfrancza/jwt-revocation-rules")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
        maven {
            name = "OSSRH"
            url = URI("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_PASSWORD")
            }
        }
    }
}

npmPublish {
    registries {
        register("npmjs") {
            uri.set("https://registry.npmjs.org")
            authToken.set(System.getenv("NPM_AUTH_TOKEN"))
        }
    }
}

rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension>().apply {
        lockFileDirectory = project.rootDir.resolve("kotlin-js-store")
    }
    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension>().apply {
        versions.webpack.version = "5.76.0"
    }
}