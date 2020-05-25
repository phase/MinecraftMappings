import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

val GLOBAL_FOLDER = File("mappings")

fun main() {
    val time = System.currentTimeMillis()
    GLOBAL_FOLDER.mkdirs()
//    MinecraftVersion.values().map {
//        GlobalScope.launch {
//            println("Starting ${it.mcVersion}")
//            it.write(GLOBAL_FOLDER)
//        }
//    }.forEach { runBlocking { it.join() } }
    MinecraftVersion.V1_15_2.write(GLOBAL_FOLDER)
    val elapsed = (System.currentTimeMillis() - time) / 1000.0
    println("Done. Took ${elapsed / 60}m (${elapsed}s)")
}
