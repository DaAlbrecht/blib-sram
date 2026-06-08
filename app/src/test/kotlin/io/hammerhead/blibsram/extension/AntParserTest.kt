package io.hammerhead.blibsram.extension

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AntParserTest {

    @Test
    fun testParseRightPress() {
        var lastCount = -1
        val parser = AntParser { lastCount }

        val line = "Rx [A4][11][4E][01][02][09][02][01][02][01][01][00][C0][0A][97][22][05][20][B0][B0][64][B5]"
        
        val result = parser.parseAntPacket(line)
        
        assertEquals(BlibAction.RIGHT_PRESS, result?.action)
        assertEquals(0x09, result?.eventCount)
    }

    @Test
    fun testParseLeftPress() {
        var lastCount = -1
        val parser = AntParser { lastCount }
        
        val line = "Rx [A4][11][4E][01][02][0A][01][01]"
        
        val result = parser.parseAntPacket(line)
        
        assertEquals(BlibAction.LEFT_PRESS, result?.action)
        assertEquals(0x0A, result?.eventCount)
    }

    @Test
    fun testDebounce() {
        var lastCount = 0x09
        val parser = AntParser { lastCount }
        
        val line = "Rx [A4][11][4E][01][02][09][02][01]"
        
        val result = parser.parseAntPacket(line)
        
        assertNull(result)
    }

    @Test
    fun testIgnoreOtherPages() {
        var lastCount = -1
        val parser = AntParser { lastCount }
        
        // Page 79 (4F) instead of Page 02
        val line = "Rx [A4][11][4E][01][4F][33][35][14]"
        
        val result = parser.parseAntPacket(line)
        
        assertNull(result)
    }

    @Test
    fun testConcatenatedPackets() {
        var lastCount = -1
        val parser = AntParser { lastCount }

        val line = "Rx [A4][03][40][00][01][02][C8][B5][A4][11][4E][01][02][B0][01][01]"
        
        val result = parser.parseAntPacket(line)
        
        assertEquals(BlibAction.LEFT_PRESS, result?.action)
        assertEquals(0xB0, result?.eventCount)
    }
}
