plugins {
    id("idea")
    id("net.minecraftforge.gradle") version "[6.0,6.2)"
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("java-library")
}

version = "${project.property("mod_version")}+forge-${project.property("minecraft_version")}"
group = project.property("mod_group").toString()

base {
    archivesName = "${project.property("mod_id")}-${project.property("minecraft_version")}-forge"
}

java.toolchain.languageVersion = JavaLanguageVersion.of(17)

minecraft {
    mappings channel: "official", version: project.property("minecraft_version").toString()

    runs {
        client {
            workingDirectory project.file("run")
            property "forge.logging.markers", "REGISTRIES"
            property "forge.logging.console.level", "debug"
            mods {
                create("tcxaero") {
                    source sourceSets.main
                }
            }
        }
    }
}

repositories {
    mavenCentral()
    maven {
        name = "Xaero"
        url = uri("https://chocolateminecraft.com/maven/")
    }
    maven {
        name = "KotlinForForge"
        url = uri("https://thedarkcolour.github.io/KotlinForForge/")
    }
    flatDir {
        dirs = listOf(file("libs"))
    }
}

dependencies {
    minecraft("net.minecraftforge:forge:${project.property("minecraft_version")}-${project.property("forge_version")}")

    implementation("org.jetbrains.kotlin:kotlin-stdlib:${project.property("kotlin_version")}")

    // TouchController 0.3.1 alpha17 (1.20.1 Forge) 与 Xaero's Minimap 以本地 jar 形式提供。
    // 用 compileOnly + runtimeOnly，不打包进产物。
    compileOnly(files("libs/touchcontroller-common.jar"))
    compileOnly(files("libs/touchcontroller-api.jar"))
    compileOnly(files("libs/touchcontroller-1-20-1-forge.jar"))
    compileOnly(files("libs/combine-common.jar"))
    compileOnly(files("libs/kondor-core.jar"))
    compileOnly(files("libs/kotlinx-collections-immutable.jar"))
    compileOnly(files("libs/xaerominimap-forge-1.20.1-26.6.0.jar"))
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.named<ProcessResources>("processResources") {
    val props = mapOf(
        "mod_id" to project.property("mod_id"),
        "mod_name" to project.property("mod_name"),
        "mod_version" to project.property("mod_version"),
        "mod_group" to project.property("mod_group"),
        "minecraft_version" to project.property("minecraft_version"),
        "forge_version" to project.property("forge_version"),
    )
    inputs.properties(props)
    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) {
        expand(props)
    }
}

jar {
    manifest {
        attributes(
            "Specification-Title" to project.property("mod_id"),
            "Specification-Vendor" to "tcxaero",
            "Specification-Version" to "1",
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "tcxaero",
        )
    }
    // 把 kotlin-stdlib 之外的运行时依赖排除；KotlinForForge 环境已提供 stdlib。
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
