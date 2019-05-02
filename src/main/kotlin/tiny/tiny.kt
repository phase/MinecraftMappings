package tiny

import net.techcable.srglib.mappings.Mappings

/**
 * Merge multiple srglib.Mappings into one tiny.Mappings
 */

class Mappings(
    val namespaces: MutableList<String>,
    val classes: MutableList<ClassMapping>,
    val fields: MutableList<FieldMapping>,
    val methods: MutableList<MethodMapping>
) {

    constructor() : this(mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf())

    fun addMappings(namespace: String, mappings: Mappings) {
        namespaces.add(namespace)
        println("tiny: starting conversion for $namespace")
        mappings.forEachClass { obf, mapped ->
            getClass(obf.name).add(namespace, mapped.name)
        }
        mappings.forEachField { obf, mapped ->
            getField(obf.declaringType.name, obf.name, "Lunk;").add(namespace, mapped.name)
        }
        mappings.forEachMethod { obf, mapped ->
            getMethod(obf.declaringType.name, obf.name, obf.signature.descriptor).add(namespace, mapped.name)
        }
    }

    fun getClass(source: String): ClassMapping {
        var maybeClass = classes.firstOrNull { it.source == source }
        if (maybeClass == null) {
            maybeClass = ClassMapping(source, mutableMapOf())
            classes.add(maybeClass)
        }
        return maybeClass
    }

    fun getField(sourceClass: String, source: String, desc: String): FieldMapping {
        var maybeField = fields.firstOrNull { it.sourceClass == sourceClass && it.source == source && it.desc == desc }
        if (maybeField == null) {
            maybeField = FieldMapping(sourceClass, source, desc, mutableMapOf())
            fields.add(maybeField)
        }
        return maybeField
    }

    fun getMethod(sourceClass: String, source: String, desc: String): MethodMapping {
        var maybeMethod =
            methods.firstOrNull { it.sourceClass == sourceClass && it.source == source && it.desc == desc }
        if (maybeMethod == null) {
            maybeMethod = MethodMapping(sourceClass, source, desc, mutableMapOf())
            methods.add(maybeMethod)
        }
        return maybeMethod
    }

    fun toStrings(): List<String> {
        val entryMappings = mutableListOf("v1\t${namespaces.joinToString("\t")}")
        entryMappings.addAll(classes.map { it.toString(namespaces) })
        entryMappings.addAll(fields.map { it.toString(namespaces) })
        entryMappings.addAll(methods.map { it.toString(namespaces) })
        return entryMappings
    }
}

interface EntryMapping {
    val mappings: MutableMap<String, String>
    val source: String

    fun add(namespace: String, value: String): EntryMapping {
        mappings[namespace] = value
        return this
    }

    operator fun get(namespace: String): String? = mappings[namespace]

    fun toString(namespaces: List<String>): String {
        val line = namespaces.joinToString("\t") { get(it) ?: source }
        val kind = when (this) {
            is ClassMapping -> "CLASS"
            is FieldMapping -> "FIELD"
            is MethodMapping -> "METHOD"
            else -> "UNKNOWN"
        }
        return "$kind\t${toString()}\t$line"
    }
}

class ClassMapping(
    override val source: String,
    override val mappings: MutableMap<String, String>
) : EntryMapping {
    override fun toString(): String = source
}

class FieldMapping(
    val sourceClass: String,
    override val source: String,
    val desc: String,
    override val mappings: MutableMap<String, String>
) : EntryMapping {
    override fun toString(): String = "$sourceClass\t$desc\t$source"
}

class MethodMapping(
    val sourceClass: String,
    override val source: String,
    val desc: String,
    override val mappings: MutableMap<String, String>
) : EntryMapping {
    override fun toString(): String = "$sourceClass\t$desc\t$source"
}
