package provider

import com.google.gson.JsonParser
import net.techcable.srglib.format.MappingsFormat
import net.techcable.srglib.mappings.Mappings
import org.cadixdev.lorenz.MappingSet
import org.cadixdev.lorenz.io.proguard.ProGuardFormat
import org.cadixdev.lorenz.io.srg.SrgWriter
import java.io.*
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Reads the client mappings from the URLs stored in the client json.
 *
 * Written in Java and translated to Kotlin
 *
 * @author phase
 */
object MojangMappings {

    fun getMappings(version: String): Mappings {
        val cacheDir = File("cache/mojang/")
        cacheDir.mkdirs()
        download(version, cacheDir)
        val clientMappingFile = File(cacheDir, version + "_client.srg")
        return MappingsFormat.SEARGE_FORMAT.parseLines(clientMappingFile.readLines().filter {
            !it.contains("package-info")
                    && !".*(\\$\\d+).*".toRegex().matches(it)
        })
    }

    fun download(version: String, dir: File?) {
        val clientMappingFile = File(dir, version + "_client.srg")
        val serverMappingFile = File(dir, version + "_server.srg")

        if (clientMappingFile.exists() && serverMappingFile.exists()) return;

        val mappings = readMappings(version)

        val clientWriter = PrintWriter(FileWriter(clientMappingFile))
        SrgWriter(clientWriter).write(mappings.clientMappings)
        clientWriter.close()

        val serverWriter = PrintWriter(FileWriter(serverMappingFile))
        SrgWriter(serverWriter).write(mappings.serverMappings)
        serverWriter.close()
    }

    fun readMappings(version: String): LorenzMappings {
        val versionJsonFile =
            File(minecraftFolder, "/versions/$version/$version.json")
        val versionJson =
            JsonParser().parse(FileReader(versionJsonFile)).asJsonObject
        val downloads = versionJson.getAsJsonObject("downloads")
        val clientMappingUrl =
            URL(downloads.getAsJsonObject("client_mappings")["url"].asString)
        val serverMappingUrl =
            URL(downloads.getAsJsonObject("server_mappings")["url"].asString)
        return LorenzMappings(readUrl(clientMappingUrl), readUrl(serverMappingUrl))
    }

    fun readUrl(url: URL): MappingSet {
        println("Downloading from $url")
        val conn = url.openConnection()
        BufferedReader(
            InputStreamReader(
                conn.getInputStream(),
                StandardCharsets.UTF_8
            )
        ).use { reader -> return ProGuardFormat().createReader(reader).read().reverse() }
    }

    /**
     * @return .minecraft folder for the current OS
     */
    val minecraftFolder: File
        get() {
            val os = System.getProperty("os.name").toLowerCase()
            return if (os.contains("win")) {
                File(File(System.getenv("APPDATA")), ".minecraft")
            } else if (os.contains("mac")) {
                File(
                    File(System.getProperty("user.home")),
                    "Library/Application Support/minecraft"
                )
            } else if (os.contains("linux")) {
                File(File(System.getProperty("user.home")), ".minecraft/")
            } else {
                throw RuntimeException("Failed to determine Minecraft directory for OS: $os")
            }
        }


    data class LorenzMappings(
        val clientMappings: MappingSet,
        val serverMappings: MappingSet
    )
}