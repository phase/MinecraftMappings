package provider

import cuchaz.enigma.ProgressListener
import cuchaz.enigma.translation.mapping.serde.MappingFormat
import net.techcable.srglib.format.MappingsFormat
import net.techcable.srglib.mappings.Mappings
import java.io.File
import java.util.concurrent.TimeUnit

fun String.runCommand(workingDir: File) {
    ProcessBuilder(*split(" ").toTypedArray())
        .directory(workingDir)
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
        .waitFor(60, TimeUnit.MINUTES)
}

fun getYarnMappings(minecraftVersion: String): Mappings {
    return MappingsFormat.SEARGE_FORMAT.parseFile(generateYarnMappings(minecraftVersion))
}

fun generateYarnMappings(minecraftVersion: String): File {
    val yarnFolder = File("yarn")
    val mappingsFile = File("cache/yarn-$minecraftVersion.srg")
    if (mappingsFile.exists()) {
        println("yarn $minecraftVersion: yarn-$minecraftVersion.srg already exists")
        return mappingsFile
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

    return mappingsFile
}
