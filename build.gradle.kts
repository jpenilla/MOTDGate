plugins {
  alias(libs.plugins.indra)
  alias(libs.plugins.shadow)
  alias(libs.plugins.resourceFactoryBukkit)
  alias(libs.plugins.resourceFactoryPaper)
  alias(libs.plugins.runPaper)
  alias(libs.plugins.modPublishPlugin)
  alias(libs.plugins.immaculate)
}

repositories {
  mavenCentral {
    mavenContent { releasesOnly() }
  }
  maven("https://central.sonatype.com/repository/maven-snapshots/") {
    mavenContent { snapshotsOnly() }
  }
  maven("https://repo.papermc.io/repository/maven-public/") {
    mavenContent {
      includeGroup("io.papermc")
      includeGroup("io.papermc.paper")
      includeModule("com.mojang", "brigadier")
      includeModule("net.md-5", "bungeecord-chat")
    }
  }
}
dependencies {
  compileOnly(libs.paper.api)
  compileOnly(libs.jspecify)
  implementation(libs.paper.trail)
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)
}

indra {
  javaVersions {
    target(21)
    minimumToolchain(21)
  }
  mitLicense()
  github("jpenilla", "MOTDGate") {
    ci(true)
  }
}

val minecraftVersion = libs.versions.minecraft.get()

immaculate {
  workflows.register("java") {
    java()
    palantir()
  }
}

paperPluginYaml {
  name = "MOTDGate"
  main = "xyz.jpenilla.motdgate.MOTDGate"
  apiVersion = "1.21.8"
  foliaSupported = true
  prefix = "MOTDGate"
  authors = listOf("jmp")
  website = "https://github.com/jpenilla/MOTDGate"
}

bukkitPluginYaml {
  main = "motdgate.io.papermc.papertrail.RequiresPaperPlugins"
  name = "MOTDGate"
  prefix = "MOTDGate"
  authors = listOf("jmp")
  website = "https://github.com/jpenilla/MOTDGate"
  apiVersion = "1.13"
  foliaSupported = true
}

tasks {
  test {
    useJUnitPlatform()
  }

  shadowJar {
    relocate("io.papermc.papertrail", "motdgate.io.papermc.papertrail")
    mergeServiceFiles()
    filesMatching("META-INF/services/**") {
      duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
  }

  assemble {
    dependsOn(shadowJar)
  }

  runServer {
    minecraftVersion(minecraftVersion)
  }
}

publishMods {
  modrinth {
    projectId = "c22V7fS9"
    file = tasks.shadowJar.flatMap { it.archiveFile }
    type = if (project.version.toString().contains("beta", ignoreCase = true)) BETA else STABLE
    minecraftVersions = listOf(
      "1.21.8",
      "1.21.9",
      "1.21.10",
      "1.21.11",
      "26.1",
      "26.1.1",
      "26.1.2",
      "26.2",
    )
    modLoaders.addAll("paper", "folia")
    changelog = providers.environmentVariable("RELEASE_NOTES")
    accessToken = providers.environmentVariable("MODRINTH_TOKEN")
  }
}
