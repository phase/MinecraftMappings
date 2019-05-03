package provider

import cuchaz.enigma.ProgressListener
import cuchaz.enigma.translation.mapping.serde.MappingFormat
import net.techcable.srglib.format.MappingsFormat
import net.techcable.srglib.mappings.Mappings
import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

fun String.runCommand(workingDir: File) {
    ProcessBuilder(*split(" ").toTypedArray())
        .directory(workingDir)
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
        .waitFor(60, TimeUnit.MINUTES)
}

fun getYarnVersion(minecraftVersion: String): String {
    val url = URL("https://meta.fabricmc.net/v1/versions/mappings/$minecraftVersion")
    val versionJson = url.loadJson().asJsonArray[0].asJsonObject
    return versionJson.get("version").asString
}

fun downloadYarn(yarnVersion: String, file: File) {
    URL("https://maven.fabricmc.net/net/fabricmc/yarn/$yarnVersion/yarn-$yarnVersion-tiny.gz").downloadTo(file)
}

fun getYarnMappings(minecraftVersion: String): Map<String, Mappings> {
    val yarnMavenVersion = getYarnVersion(minecraftVersion)
    val yarnZip = File("cache/yarn-$yarnMavenVersion.gz")
    if (!yarnZip.exists()) {
        downloadYarn(yarnMavenVersion, yarnZip)
    }
    val tinyMappings = tiny.Mappings()
    GZIPInputStream(yarnZip.inputStream()).use { zip ->
        var namespaces = listOf<String>()
        zip.reader().readLines().forEach { line ->
            val parts = line.split("\t")
            when (true) {
                line.startsWith("v1") -> {
                    namespaces = parts.subList(2, parts.size)
                    tinyMappings.namespaces = namespaces.toMutableList()
                }
                line.startsWith("CLASS\t") -> {
                    val clazz = tinyMappings.getClass(parts[1])
                    parts.forEachIndexed { index, mapped ->
                        if (index >= 2) {
                            val namespace = namespaces[index - 2]
                            if (namespace != "official") {
                                clazz.add(namespace, mapped)
                            }
                        }
                    }
                }
                line.startsWith("FIELD\t") -> {
                    val field = tinyMappings.getField(parts[1], parts[3], parts[2])
                    parts.forEachIndexed { index, mapped ->
                        if (index >= 4) {
                            val namespace = namespaces[index - 4]
                            if (namespace != "official") {
                                field.add(namespace, mapped)
                            }
                        }
                    }
                }
                line.startsWith("METHOD\t") -> {
                    val method = tinyMappings.getMethod(parts[1], parts[3], parts[2])
                    parts.forEachIndexed { index, mapped ->
                        if (index >= 4) {
                            val namespace = namespaces[index - 4]
                            if (namespace != "official") {
                                try {
                                    method.add(namespace, mapped)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    println(line)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    tinyMappings.toMappings().forEach { namespace, mappings ->
        println("yarn $minecraftVersion $namespace: parsed class=${mappings.classes().size} method=${mappings.fields().size} field=${mappings.methods().size}")
    }
    return tinyMappings.toMappings()
}

fun getYarnMappingsFromSubmodule(minecraftVersion: String): Mappings {
    val yarnFolder = File("yarn")
    val mappingsFile = File("cache/yarn-$minecraftVersion.srg")
    if (mappingsFile.exists()) {
        println("yarn $minecraftVersion: yarn-$minecraftVersion.srg already exists")
        return MappingsFormat.SEARGE_FORMAT.parseFile(mappingsFile)
    }

    println("yarn $minecraftVersion: checking out branch $minecraftVersion")
    "git checkout $minecraftVersion".runCommand(yarnFolder)

    println("yarn $minecraftVersion: reading mappings directory")
    val entryTree = MappingFormat.ENIGMA_DIRECTORY.read(File(yarnFolder, "mappings").toPath(), ProgressListener.VOID)
    println("yarn $minecraftVersion: writing mappings to srg")
    MappingFormat.SRG_FILE.write(entryTree, mappingsFile.toPath(), ProgressListener.VOID)

    // TODO: fix these
    val brokenClasses = listOf(
        "<init>", "WoodlandMansionGenerator",
        "VoxelSet", "ParticleManager", "PointOfInterestDebugRenderer", "NumberRange", "ServerLightingProvider",
        "SpellcastingIllagerEntity", "NetherFortressGenerator", "TextureUtil"
    )
    mappingsFile.writeText(mappingsFile.readLines().filter {
        brokenClasses.map { b -> !it.contains(b) }.foldRight(true) { a, b -> a && b }
    }.joinToString("\n"))

    return MappingsFormat.SEARGE_FORMAT.parseFile(mappingsFile)
}
