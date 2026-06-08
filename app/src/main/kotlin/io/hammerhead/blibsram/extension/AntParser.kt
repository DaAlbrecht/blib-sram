package io.hammerhead.blibsram.extension

class AntParser(private val lastEventCountProvider: () -> Int) {
    data class ParseResult(val action: BlibAction, val eventCount: Int)

    fun parseAntPacket(line: String): ParseResult? {
        return try {
            val up = line.uppercase()
            val start = up.indexOf("[4E]")
            // Check if the line has at least enough characters to be a button press event.
            if (start == -1 || start + 20 > up.length) return null

            // SRAM button evens come on Page 02.
            val page = up.substring(start + 9, start + 11)
            if (page == "02") {
                val eventCount = up.substring(start + 13, start + 15).toIntOrNull(16) ?: return null
                val status = up.substring(start + 17, start + 19).toIntOrNull(16) ?: return null

                if (eventCount != lastEventCountProvider()) {
                    val action = when (status) {
                        0x01 -> BlibAction.LEFT_PRESS
                        0x02 -> BlibAction.RIGHT_PRESS
                        else -> null
                    }
                    if (action != null) {
                        return ParseResult(action, eventCount)
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
