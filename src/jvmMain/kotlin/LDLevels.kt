import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.PrintStream

const val mask = """([\x00-\x09]{416})\x00{39}([A-Z \x00]{38})""" //

data class LevelMeta(
    val name: String,
    val map: List<String>
)

fun main(args: Array<String>) {

    val pattern = Regex(mask)

    val file = File(args[0])
    val reader = file.bufferedReader(Charsets.ISO_8859_1)

//    reader.
    val data = DataInputStream(BufferedInputStream(file.inputStream()))
    val text = data.readAllBytes().toString(Charsets.ISO_8859_1)
    val res = mutableListOf<LevelMeta>()

    pattern.findAll(text).forEach {
        val name = it.groupValues[2].trim()
        val map =
            it.groupValues[1].chunked(26).map { line ->
            line.map { char ->
                when (char.code) {
                    0 -> " "
                    1 -> "#"
                    2 -> "@"
                    3 -> "H"
                    4 -> "-"
                    5 -> "X"
                    6 -> "S"
                    7 -> "$"
                    8 -> "0"
                    9 -> "&"
                    else -> throw IllegalArgumentException(char.code.toString(10))
                }
            }.joinToString("")
        }

        res.add( LevelMeta(name, map) )
    }


    (if ( args.size > 1 ) {
        PrintStream(args[1], Charsets.UTF_8)
    } else System.out).use { out ->

        out.println("""
            {
                "levels": {
                    "name": "${args[0]}",
                    "total": ${res.size},
        """.trimIndent())
        out.println(
            res.mapIndexed { index: Int, l: LevelMeta ->
                """
                "${if ( l.name[0].code != 0 ) l.name else "level-${index+1}" }": [
                    ${l.map.joinToString(",\n") { "\"$it\"" }}
                ]
                """.trimIndent()
            }.joinToString(",\n")
        )
        out.println("}}")
    }

}
