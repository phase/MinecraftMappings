package provider

import TSrgUtil
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.stream.JsonReader
import com.opencsv.CSVReader
import net.techcable.srglib.FieldData
import net.techcable.srglib.JavaType
import net.techcable.srglib.MethodData
import net.techcable.srglib.format.MappingsFormat
import net.techcable.srglib.mappings.Mappings
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.*
import java.util.zip.ZipInputStream
import kotlin.system.exitProcess

fun downloadMcpMappings(srgMappings: Mappings, mappingsVersion: String): Mappings {
    val cacheFile = File("cache/mcp_$mappingsVersion/mcp.json")
    val fieldNames = HashMap<String, String>()
    val methodNames = HashMap<String, String>()
    if (!cacheFile.exists()) {
        cacheFile.parentFile.mkdirs()
        check(cacheFile.createNewFile())
        // Validate and compute the mapping version information
        val mappingsVersions: Map<String, Map<String, List<Int>>> =
            JsonReader(URL("http://export.mcpbot.bspk.rs/versions.json").openStream().reader()).use { reader ->
                val result = HashMap<String, MutableMap<String, MutableList<Int>>>()
                // We have to parse this by hand or else things don't work
                reader.beginObject()
                while (reader.hasNext()) {
                    val version = reader.nextName()
                    val byChannel = HashMap<String, MutableList<Int>>()
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val channelName = reader.nextName()
                        val channelVersions = ArrayList<Int>()
                        reader.beginArray()
                        while (reader.hasNext()) {
                            channelVersions.add(reader.nextInt())
                        }
                        reader.endArray()
                        byChannel[channelName] = channelVersions
                    }
                    reader.endObject()
                    result[version] = byChannel
                }
                reader.endObject()
                result
            }
        val mappingsChannel = mappingsVersion.substring(0, mappingsVersion.indexOf('_'))
        val isNodoc = "_nodoc_" in mappingsVersion
        val fullMappingsChannel = if (isNodoc) mappingsChannel + "_nodoc" else mappingsChannel
        val mappingsId = mappingsVersion.substring(mappingsVersion.lastIndexOf('_') + 1).toInt()
        var minecraftVersion: String? = null
        for ((version, byChannel) in mappingsVersions.entries) {
            if (mappingsChannel !in byChannel) {
                System.err.println("Unknown channel $mappingsChannel for version $version")
                exitProcess(1)
            }
            if (mappingsId in byChannel[mappingsChannel]!!) {
                println("Found mappings $mappingsId for version $version")
                minecraftVersion = version
                break
            }
        }
        if (minecraftVersion == null) {
            System.err.println("Unable to find mappings: $mappingsVersion")
            exitProcess(1)
        }
        // Parse the mappings data files
        try {
            val url =
                URL("http://export.mcpbot.bspk.rs/mcp_$fullMappingsChannel/$mappingsId-$minecraftVersion/mcp_$fullMappingsChannel-$mappingsId-$minecraftVersion.zip")
            println("Downloading MCP mappings from: $url")
            ZipInputStream(url.openStream()).use {
                var entry = it.nextEntry
                do {
                    when (entry.name) {
                        "fields.csv" -> {
                            CSVReader(it.reader()).forEachLine {
                                val original = it[0]
                                val renamed = it[1]
                                fieldNames[original] = renamed
                            }
                        }
                        "methods.csv" -> {
                            CSVReader(it.reader()).forEachLine {
                                val original = it[0]
                                val renamed = it[1]
                                methodNames[original] = renamed
                            }
                        }
                    }
                    entry = it.nextEntry
                } while (entry != null)
            }
            if (fieldNames.isEmpty() || methodNames.isEmpty()) {
                System.err.println("Unable to download MCP mappings $mappingsVersion: Unable to locate info in the zip file")
                exitProcess(1)
            }
        } catch (e: IOException) {
            System.err.println("Unable to download MCP mappings $mappingsVersion:")
            e.printStackTrace()
            exitProcess(1)
        }
        val json = JsonObject()
        json["fields"] = Gson().toJsonTree(fieldNames)
        json["methods"] = Gson().toJsonTree(methodNames)
        cacheFile.writeText(json.toString())
    } else {
        println("Reading cache $cacheFile")
        JsonReader(cacheFile.reader()).use {
            it.beginObject()
            while (it.hasNext()) {
                val name = it.nextName()
                if (name == "fields" || name == "methods") {
                    it.beginObject()
                    while (it.hasNext()) {
                        val original = it.nextName()
                        val renamed = it.nextString()
                        when (name) {
                            "fields" -> fieldNames[original] = renamed
                            "methods" -> methodNames[original] = renamed
                        }
                    }
                    it.endObject()
                }
            }
            it.endObject()
        }
    }
    return Mappings.createRenamingMappings(
        { oldType: JavaType -> oldType },
        { srgMethod: MethodData -> methodNames[srgMethod.name] ?: srgMethod.name },
        { srgField: FieldData -> fieldNames[srgField.name] ?: srgField.name }
    ).transform(srgMappings.inverted())
}

fun downloadSrgMappings(minecraftVersion: String): Mappings {
    val cacheFile = File("cache/mcp-$minecraftVersion-joined.srg")
    if (!cacheFile.exists()) {
        cacheFile.parentFile.mkdirs()
        try {
            val url =
                URL("http://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp/$minecraftVersion/mcp-$minecraftVersion-srg.zip")
            ZipInputStream(url.openStream()).use { zipStream ->
                var entry = zipStream.nextEntry
                while (entry != null) {
                    if (entry.name == "joined.srg") {
                        check(cacheFile.createNewFile())
                        cacheFile.outputStream().use { output -> zipStream.copyTo(output) }
                    }
                    entry = zipStream.nextEntry
                }
            }
            if (!cacheFile.exists()) {
                System.err.println("Unable to download SRG mappings for $minecraftVersion: Unable to locate joined.srg in the zip file")
                exitProcess(1)
            }
        } catch (e: IOException) {
            System.err.println("Unable to download SRG mappings for $minecraftVersion:")
            e.printStackTrace()
            exitProcess(1)
        }
    }
    return MappingsFormat.SEARGE_FORMAT.parseFile(cacheFile)
}

// use MinecraftForge/MCPConfig to mappings > 1.13

fun getMCPConfigMappings(minecraftVersion: String): Mappings {
    val cacheFolder = File("cache/")

    val obf2srgFile = File(cacheFolder, "mcpconfig-$minecraftVersion-joined.srg")

    if (!obf2srgFile.exists()) {
        println("mcpconfig $minecraftVersion: generating srg from tsrg")
        val tsrgFile = File("MCPConfig/versions/release/$minecraftVersion/joined.tsrg")
        TSrgUtil.toSrg(tsrgFile, obf2srgFile)
    }

    return MappingsFormat.SEARGE_FORMAT.parseFile(obf2srgFile)
}
