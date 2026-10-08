package app.forgetit.domain

/** Extra reminder lead times stored as text, such as "7,3,0" in the database or "7;3;0" in CSV. */
object Offsets {
    fun parse(text: String, sep: Char = ','): List<Int> = text.split(sep).mapNotNull { it.trim().toIntOrNull() }.distinct()
    fun format(list: List<Int>, sep: Char = ','): String = list.distinct().joinToString(sep.toString())
}
