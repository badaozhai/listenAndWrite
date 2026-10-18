package app.listenandwrite.domain

import app.listenandwrite.data.chineseTextbook
import app.listenandwrite.data.englishDrills
import app.listenandwrite.data.englishTextbook
import java.util.zip.Inflater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeSheetTest {
    @Test
    fun clearWrongCharacterIsNotAStrokeProblem() {
        val hit = gradeOne("势不可挡", chars("事不可挡"), chinese = true)
        assertEquals(GradeMark.WRONG, hit.mark)
        assertEquals(0, hit.spots.single().index)
        assertEquals('事', hit.spots.single().seen)
        assertEquals(GradeMark.WRONG, gradeOne("己", chars("已"), chinese = true).mark)
        assertEquals(GradeMark.WRONG, gradeOne("未", chars("末"), chinese = true).mark)
    }

    @Test
    fun rightAndUnsteadyAndShort() {
        assertEquals(GradeMark.RIGHT, gradeOne("桂花", chars("桂花"), chinese = true).mark)
        val shaky = gradeOne("桂花", listOf(SeenChar('桂', 0.9f), SeenChar('华', 0.2f)), chinese = true)
        assertEquals(GradeMark.STROKE, shaky.mark)
        assertEquals(GradeMark.STROKE, gradeOne("势不可挡", chars("势不可"), chinese = true).mark)
        assertEquals(GradeMark.MISSING, gradeOne("桂花", emptyList(), chinese = true).mark)
    }

    @Test
    fun alignsByQuestionNumber() {
        val lines = listOf(
            SheetLine("2. 故乡"),
            SheetLine("姓名：小明"),
            SheetLine("1. 桂花"),
        )
        val hits = gradeSheet(listOf("桂花" to true, "故乡" to true), lines)
        assertEquals(GradeMark.RIGHT, hits[0].mark)
        assertEquals(GradeMark.RIGHT, hits[1].mark)
        assertEquals("桂花", hits[0].seen)
        assertEquals("故乡", hits[1].seen)
    }

    @Test
    fun gradesGeneratedHandwriting() {
        val samples = handwritingSamples()
        assertEquals(listOf("wrong", "messy", "right", "sheet"), samples.map { it.kind })
        samples.forEach { sample ->
            val ink = inkPixels(sample.file)
            assertTrue("${sample.file} decoded empty or blank", ink > 200)
        }
        samples.filter { it.kind != "sheet" }.forEach { sample ->
            val seenChars = sample.seen.map { SeenChar(it, sample.confidence) }
            val hit = gradeOne(sample.expected, seenChars, chinese = true)
            val detail = gradeDetail(hit, chinese = true)
            println("HANDWRITE expected=${sample.expected} seen=${hit.seen} detail=$detail")
            val message = "expected=${sample.expected} seen=${hit.seen} detail=$detail"
            when (sample.kind) {
                "wrong" -> {
                    assertEquals(message, GradeMark.WRONG, hit.mark)
                    assertTrue(message, detail.contains("写成了") && detail.contains(sample.seen))
                }
                "messy" -> {
                    assertEquals(message, GradeMark.STROKE, hit.mark)
                    assertEquals(message, "笔画没到位", detail)
                }
                "right" -> {
                    assertEquals(message, GradeMark.RIGHT, hit.mark)
                    assertEquals(message, "写对", detail)
                }
            }
        }
    }

    @Test
    fun englishLetterDifferenceIsWrong() {
        assertEquals(GradeMark.WRONG, gradeOne("apple", chars("apply"), chinese = false).mark)
        assertEquals(GradeMark.RIGHT, gradeOne("Apple", chars("apple"), chinese = false).mark)
        assertEquals(GradeMark.STROKE, gradeOne("apple", chars("aple"), chinese = false).mark)
    }
}

private fun chars(text: String): List<SeenChar> = text.map { SeenChar(it) }

class ChineseCharSampleTest {
    @Test
    fun everyTextbookCharacterHasHandwritingSamples() {
        val wanted = chineseTextbook
            .flatMap { entry -> entry.text.filter { WordText.isHan(it) }.toList() }
            .toSet()
        val rows = characterCatalog()
        assertEquals(wanted, rows.map { it.char }.toSet())
        rows.forEach { row ->
            assertTrue("${row.char} 正字没有墨", inkOf(row.right) > 40)
            assertTrue("${row.char} 不规范样本没有墨", inkOf(row.messy) > 40)
            val right = gradeDetail(gradeOne(row.char.toString(), listOf(SeenChar(row.char, 0.95f)), true), true)
            assertEquals("${row.char} 正字", "写对", right)
            if (row.wrongChar.isEmpty()) return@forEach
            assertTrue("${row.char} 错字样本没有墨", inkOf(row.wrong) > 40)
            val hit = gradeOne(row.char.toString(), listOf(SeenChar(row.wrongChar[0], 0f)), true)
            val detail = gradeDetail(hit, true)
            assertEquals(row.char.toString(), GradeMark.WRONG, hit.mark)
            assertTrue(detail, detail.contains("写成了") && detail.contains(row.wrongChar))
        }
    }
}

private data class CharSample(
    val char: Char,
    val right: String,
    val messy: String,
    val wrong: String,
    val wrongChar: String,
)

class EnglishHandwritingTest {
    @Test
    fun everyShippedEnglishWordHasHandwriting() {
        val wanted = buildSet {
            (englishTextbook.map { it.text } + englishDrills.map { it.text }).forEach { text ->
                val key = text.filter { it.isLetter() }.lowercase()
                assertTrue("没有字母: $text", key.isNotEmpty())
                add(key)
            }
        }
        val rows = englishCatalog()
        assertEquals(wanted, rows.map { it.key }.toSet())
        println("ENGLISH-HANDWRITE spellings=${rows.size} items=${englishTextbook.size + englishDrills.size}")
        rows.forEach { row ->
            val files = listOf(row.print, row.script, row.messy, row.wrong)
            files.forEach { file ->
                assertTrue("$file 没有墨", inkOf("english-hands/$file") > 40)
            }
            val bytes = files.map { file ->
                EnglishHandwritingTest::class.java.classLoader!!
                    .getResourceAsStream("english-hands/$file")!!
                    .readBytes()
            }
            for (left in bytes.indices) {
                for (right in left + 1 until bytes.size) {
                    assertTrue(
                        "${row.key} 的两种手写是同一张",
                        !bytes[left].contentEquals(bytes[right]),
                    )
                }
            }
            val message = { detail: String, seen: String ->
                "expected=${row.example} seen=$seen detail=$detail"
            }
            listOf(0.95f, 0.9f).forEach { confidence ->
                val hit = gradeOne(row.example, row.key.map { SeenChar(it, confidence) }, chinese = false)
                val detail = gradeDetail(hit, chinese = false)
                assertEquals(message(detail, hit.seen), GradeMark.RIGHT, hit.mark)
                assertEquals(message(detail, hit.seen), "写对", detail)
            }
            val messyChars = row.messySeen.mapIndexed { index, letter ->
                SeenChar(letter, if (index == row.messyIndex) 0.2f else 0.95f)
            }
            val messyHit = gradeOne(row.example, messyChars, chinese = false)
            val messyDetail = gradeDetail(messyHit, chinese = false)
            assertEquals(message(messyDetail, messyHit.seen), GradeMark.STROKE, messyHit.mark)
            assertEquals(message(messyDetail, messyHit.seen), "没认清", messyDetail)
            val wrongHit = gradeOne(row.example, row.wrongSeen.map { SeenChar(it, 0f) }, chinese = false)
            val wrongDetail = gradeDetail(wrongHit, chinese = false)
            val substituted = row.wrongSeen[row.messyIndex].toString()
            assertEquals(message(wrongDetail, wrongHit.seen), GradeMark.WRONG, wrongHit.mark)
            assertTrue(message(wrongDetail, wrongHit.seen), wrongDetail.contains("写成了") && wrongDetail.contains(substituted))
        }
    }
}

private data class EnglishHand(
    val key: String,
    val example: String,
    val print: String,
    val script: String,
    val messy: String,
    val wrong: String,
    val messySeen: String,
    val wrongSeen: String,
    val messyIndex: Int,
)

private fun englishCatalog(): List<EnglishHand> {
    val text = EnglishHandwritingTest::class.java.classLoader!!
        .getResourceAsStream("english-hands/catalog.tsv")!!
        .bufferedReader(Charsets.UTF_8)
        .readText()
    return text.lineSequence().drop(1).map { it.trimEnd() }.filter { it.isNotEmpty() }.map { line ->
        val parts = line.split('\t')
        EnglishHand(
            key = parts[0],
            example = parts[1],
            print = parts[2],
            script = parts[3],
            messy = parts[4],
            wrong = parts[5],
            messySeen = parts[6],
            wrongSeen = parts[7],
            messyIndex = parts[8].toInt(),
        )
    }.toList()
}

private fun characterCatalog(): List<CharSample> {
    val text = GradeSheetTest::class.java.classLoader!!
        .getResourceAsStream("chinese-chars/catalog.tsv")!!
        .bufferedReader(Charsets.UTF_8)
        .readText()
    return text.lineSequence().drop(1).map { it.trimEnd() }.filter { it.isNotEmpty() }.map { line ->
        val parts = line.split('\t')
        CharSample(
            char = parts[0].single(),
            right = "chinese-chars/${parts[3]}",
            messy = "chinese-chars/${parts[4]}",
            wrong = parts.getOrElse(5) { "" }.let { if (it.isEmpty()) "" else "chinese-chars/$it" },
            wrongChar = parts.getOrElse(6) { "" },
        )
    }.toList()
}

private fun inkOf(resource: String): Int {
    val bytes = GradeSheetTest::class.java.classLoader!!.getResourceAsStream(resource)!!.readBytes()
    val raster = decodePng(bytes)
    var ink = 0
    for (pixel in raster.rgb) {
        val red = (pixel shr 16) and 0xff
        val green = (pixel shr 8) and 0xff
        val blue = pixel and 0xff
        if (red + green + blue < 680) ink += 1
    }
    return ink
}

private data class HandSample(
    val kind: String,
    val file: String,
    val expected: String,
    val seen: String,
    val confidence: Float,
)

private fun handwritingSamples(): List<HandSample> {
    val text = GradeSheetTest::class.java.classLoader!!
        .getResourceAsStream("handwriting/readings.tsv")!!
        .bufferedReader(Charsets.UTF_8)
        .readText()
    return text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { line ->
            val parts = line.split('\t')
            HandSample(
                kind = parts[0],
                file = parts[1],
                expected = parts.getOrElse(2) { "" },
                seen = parts.getOrElse(3) { "" },
                confidence = parts.getOrElse(4) { "" }.toFloatOrNull() ?: 0f,
            )
        }
        .toList()
}

private fun inkPixels(file: String): Int {
    val bytes = GradeSheetTest::class.java.classLoader!!.getResourceAsStream("handwriting/$file")!!.readBytes()
    val raster = decodePng(bytes)
    if (raster.width == 0 || raster.height == 0) return 0
    var ink = 0
    for (pixel in raster.rgb) {
        val red = (pixel shr 16) and 0xff
        val green = (pixel shr 8) and 0xff
        val blue = pixel and 0xff
        if (red + green + blue < 680) ink += 1
    }
    return ink
}

private class RgbRaster(val width: Int, val height: Int, val rgb: IntArray)

private fun decodePng(bytes: ByteArray): RgbRaster {
    val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
    require(bytes.copyOfRange(0, 8).contentEquals(signature)) { "not a png" }
    var pos = 8
    var width = 0
    var height = 0
    var bitDepth = 0
    var colorType = 0
    val idat = java.io.ByteArrayOutputStream()
    while (pos + 12 <= bytes.size) {
        val length = pngInt(bytes, pos)
        val type = bytes.copyOfRange(pos + 4, pos + 8).toString(Charsets.US_ASCII)
        val start = pos + 8
        when (type) {
            "IHDR" -> {
                width = pngInt(bytes, start)
                height = pngInt(bytes, start + 4)
                bitDepth = bytes[start + 8].toInt() and 0xff
                colorType = bytes[start + 9].toInt() and 0xff
            }
            "IDAT" -> idat.write(bytes, start, length)
            "IEND" -> break
        }
        pos = start + length + 4
    }
    require(bitDepth == 8) { "bit depth $bitDepth" }
    val channels = when (colorType) {
        0 -> 1
        2 -> 3
        6 -> 4
        else -> error("color type $colorType")
    }
    val inflater = Inflater()
    inflater.setInput(idat.toByteArray())
    val inflated = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (!inflater.finished()) {
        val count = inflater.inflate(buffer)
        if (count == 0) break
        inflated.write(buffer, 0, count)
    }
    inflater.end()
    val raw = inflated.toByteArray()
    val stride = width * channels
    val rgb = IntArray(width * height)
    var cursor = 0
    val previous = IntArray(stride)
    val row = IntArray(stride)
    for (y in 0 until height) {
        val filter = raw[cursor].toInt() and 0xff
        cursor += 1
        for (index in 0 until stride) {
            val value = raw[cursor].toInt() and 0xff
            cursor += 1
            val left = if (index >= channels) row[index - channels] else 0
            val up = previous[index]
            val upLeft = if (index >= channels) previous[index - channels] else 0
            row[index] = when (filter) {
                0 -> value
                1 -> (value + left) and 0xff
                2 -> (value + up) and 0xff
                3 -> (value + (left + up) / 2) and 0xff
                4 -> (value + pngPaeth(left, up, upLeft)) and 0xff
                else -> error("filter $filter")
            }
        }
        for (x in 0 until width) {
            val red: Int
            val green: Int
            val blue: Int
            when (channels) {
                1 -> {
                    red = row[x]
                    green = red
                    blue = red
                }
                3 -> {
                    red = row[x * 3]
                    green = row[x * 3 + 1]
                    blue = row[x * 3 + 2]
                }
                else -> {
                    red = row[x * 4]
                    green = row[x * 4 + 1]
                    blue = row[x * 4 + 2]
                }
            }
            rgb[y * width + x] = (red shl 16) or (green shl 8) or blue
        }
        for (index in previous.indices) previous[index] = row[index]
    }
    return RgbRaster(width, height, rgb)
}

private fun pngPaeth(left: Int, up: Int, upLeft: Int): Int {
    val estimate = left + up - upLeft
    val leftDistance = kotlin.math.abs(estimate - left)
    val upDistance = kotlin.math.abs(estimate - up)
    val diagonal = kotlin.math.abs(estimate - upLeft)
    return when {
        leftDistance <= upDistance && leftDistance <= diagonal -> left
        upDistance <= diagonal -> up
        else -> upLeft
    }
}

private fun pngInt(bytes: ByteArray, offset: Int): Int {
    return ((bytes[offset].toInt() and 0xff) shl 24) or
        ((bytes[offset + 1].toInt() and 0xff) shl 16) or
        ((bytes[offset + 2].toInt() and 0xff) shl 8) or
        (bytes[offset + 3].toInt() and 0xff)
}
