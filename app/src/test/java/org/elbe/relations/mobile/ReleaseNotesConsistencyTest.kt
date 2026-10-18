package org.elbe.relations.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

private const val MAX_PLAY_CHANGELOG_LENGTH = 500
private const val BOM = '\uFEFF'

/**
 * Checks that the store changelogs (fastlane) and the in-app "What's new" text (About dialog) are the same
 * for the current versionCode. The unit tests run in the app module directory.
 */
class ReleaseNotesConsistencyTest {
    private val locales = mapOf("en-US" to "values", "de-DE" to "values-de")

    @Test
    fun testChangelogsMatchWhatsNew() {
        locales.forEach { (locale, valuesDir) ->
            val changelog = readChangelog(locale)
            val whatsNew = readWhatsNew(valuesDir)
            assertNotNull("No whats_new_text in $valuesDir/strings.xml", whatsNew)
            assertEquals("Store changelog ($locale) and in-app release notes ($valuesDir) differ",
                    normalize(changelog), normalize(decodeAndroidEscapes(whatsNew!!)))
        }
    }

    @Test
    fun testChangelogLimits() {
        locales.keys.forEach { locale ->
            val changelog = readChangelog(locale)
            assertFalse("${changelogFile(locale)} starts with a BOM", changelog.startsWith(BOM))
            val text = normalize(changelog)
            assertTrue("${changelogFile(locale)} is empty", text.isNotEmpty())
            val length = text.codePointCount(0, text.length)
            assertTrue("${changelogFile(locale)} has $length characters, Google Play allows $MAX_PLAY_CHANGELOG_LENGTH",
                    length <= MAX_PLAY_CHANGELOG_LENGTH)
        }
    }

    private fun changelogFile(locale: String) =
            File("../fastlane/metadata/android/$locale/changelogs/${BuildConfig.VERSION_CODE}.txt")

    private fun readChangelog(locale: String): String {
        val file = changelogFile(locale)
        assertTrue("Missing store changelog ${file.path} for versionCode ${BuildConfig.VERSION_CODE}", file.isFile)
        return file.readText(Charsets.UTF_8)
    }

    private fun readWhatsNew(valuesDir: String): String? {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File("src/main/res/$valuesDir/strings.xml"))
        val strings = document.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val element = strings.item(i) as Element
            if (element.getAttribute("name") == "whats_new_text") {
                return element.textContent
            }
        }
        return null
    }

    private fun normalize(text: String) = text.replace("\r\n", "\n").trim()

    /** Decodes the escapes of Android string resources (\n, \', \", \\). */
    private fun decodeAndroidEscapes(text: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                out.append(when (val next = text[i + 1]) {
                    'n' -> '\n'
                    't' -> '\t'
                    else -> next
                })
                i += 2
            } else {
                out.append(c)
                i += 1
            }
        }
        return out.toString()
    }
}
