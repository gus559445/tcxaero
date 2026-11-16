plugins {
    id("idea")
    id("net.minecraftforge.gradle") version "[6.0,6.2)"
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("java-library")
}

val modVersion: String by project
val modGroup: String by project
val modId: String by project
val modName: String by project
val minecraftVersion: String by project
val forgeVersion: String by project
val kotlinVersion: String by project

version = "$modVersion+forge-$minecraftVersion"
group = modGroup

base {
    archivesName = "$modId-$minecraftVersion-forge"
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

minecraft {
    mappings("official", minecraftVersion)

    runs {
        create("client") {
            workingDirectory = project.file("run")
            property("forge.logging.markers", "REGISTRIES")
            property("forge.logging.console.level", "debug")
            mods {
                create("tcxaero") {
                    source(sourceSets.main.get())
                }
            }
        }
    }
}

repositories {
    mavenCentral()
    maven {
        name = "KotlinForForge"
        url = uri("https://thedarkcolour.github.io/KotlinForForge/")
    }
}

dependencies {
    minecraft("net.minecraftforge:forge:$minecraftVersion-$forgeVersion")

    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

    // TouchController 0.3.1 alpha17 (1.20.1 Forge) 与 Xaero's Minimap 以本地 jar 提供，
    // compileOnly：运行时由这两个模组自己提供，不打进本模组产物。
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
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.named<ProcessResources>("processResources") {
    val props = mapOf(
        "mod_id" to modId,
        "mod_name" to modName,
        "mod_version" to modVersion,
        "mod_group" to modGroup,
        "minecraft_version" to minecraftVersion,
        "forge_version" to forgeVersion,
    )
    inputs.properties(props)
    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) {
        expand(props)
    }
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            mapOf(
                "Specification-Title" to modId,
                "Specification-Vendor" to "tcxaero",
                "Specification-Version" to "1",
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Implementation-Vendor" to "tcxaero",
            )
        )
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
