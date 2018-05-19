package org.elbe.relations.mobile.biblio

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Created by lbenno on 14.03.2018.
 */
class BiblioBuilderUnitTest {
    private val NL: String = System.getProperty("line.separator")

    private val full: TestText = TestText("Muster, A.", "Minder, B.", "2018", "Testing Handbook", "How-to for beginners",
            "Palacington", "Springer", "Testing Series", "20", "42", "15-22")
    private val minimal: TestText = TestText("Muster, A.", "", "", "Testing Handbook", "",
            "", "", "", "", "", "")
    private val text1: TestText = TestText("Muster, A.", "Minder, B.", "2018", "Testing Handbook", "",
            "", "", "", "20", "42", "15-22")
    private val text2: TestText = TestText("Muster, A.", "", "2018", "Testing Handbook", "How-to for beginners",
            "", "", "", "20", "", "")
    private val text3: TestText = TestText("Muster, A.", "", "2018", "Testing Handbook", "How-to for beginners",
            "Palacington", "", "", "20", "", "")
    private val text4: TestText = TestText("Muster, A.", "", "2018", "Testing Handbook", "How-to for beginners",
            "", "Springer", "", "20", "", "")
    private val text5: TestText = TestText("Muster, A.", "", "2018", "Testing Handbook", "How-to for beginners",
            "", "Springer", "Testing Series", "20", "", "")
    private val text6: TestText = TestText("Muster, A.", "", "2018", "Testing Handbook", "How-to for beginners",
            "", "", "Testing Series", "20", "", "")

    @Test
    fun testGetAuthorCoAuthor() {
        assertEquals("Riese, Muster", BiblioBuilder.getAuthorCoAuthor("Riese", "Muster", ", "))
        assertEquals("Riese and Muster", BiblioBuilder.getAuthorCoAuthor("Riese", "Muster", " and "))
        assertEquals("Riese", BiblioBuilder.getAuthorCoAuthor("Riese", "  ", ", "))
        assertEquals("Riese", BiblioBuilder.getAuthorCoAuthor("Riese", "  ", " and "))
    }

    @Test
    fun testBook() {
        assertEquals("Muster, A. and Minder, B." + NL + "2018. Testing Handbook. How-to for beginners. Palacington: Springer.", bookBuiler(full))
        assertEquals("Muster, A." + NL + "Testing Handbook.", bookBuiler(minimal))
        assertEquals("Muster, A. and Minder, B." + NL + "2018. Testing Handbook.", bookBuiler(text1))
        assertEquals("Muster, A." + NL + "2018. Testing Handbook. How-to for beginners.", bookBuiler(text2))
        assertEquals("Muster, A." + NL + "2018. Testing Handbook. How-to for beginners. Palacington.", bookBuiler(text3))
        assertEquals("Muster, A." + NL + "2018. Testing Handbook. How-to for beginners: Springer.", bookBuiler(text4))
        assertEquals("Muster, A." + NL + "2018. Testing Handbook. How-to for beginners: Springer.", bookBuiler(text5))
        assertEquals("Muster, A." + NL + "2018. Testing Handbook. How-to for beginners.", bookBuiler(text6))
    }

    //[auth] and [coauth]\n[year]. [tit]. [subtit]. [place]: [publisher].
    private fun bookBuiler(text: TestText): String {
        return BiblioBuilder().add(BiblioBuilder.getAuthorCoAuthor(text.auth, text.coauth, " and "), "", "%s"+NL)
                .down("", "")
                .add(text.year, "", "")
                .add(text.title, ". ", "")
                .add(text.subtit, ". ", "")
                .add(text.place, ". ", "")
                .add(text.publisher, ": ", "")
                .up()
                .render(".")
    }

    @Test
    fun testArticle() {
        assertEquals("Muster, A. and Minder, B." + NL + "2018. \"Testing Handbook\". Testing Series. 20:42, 15-22.", articleBuiler(full))
        assertEquals("Muster, A." + NL + "\"Testing Handbook\".", articleBuiler(minimal))
        assertEquals("Muster, A. and Minder, B." + NL + "2018. \"Testing Handbook\". 20:42, 15-22.", articleBuiler(text1))
        assertEquals("Muster, A." + NL + "2018. \"Testing Handbook\". 20.", articleBuiler(text2))
        assertEquals("Muster, A." + NL + "2018. \"Testing Handbook\". 20.", articleBuiler(text3))
        assertEquals("Muster, A." + NL + "2018. \"Testing Handbook\". 20.", articleBuiler(text4))
        assertEquals("Muster, A." + NL + "2018. \"Testing Handbook\". Testing Series. 20.", articleBuiler(text5))
        assertEquals("Muster, A." + NL + "2018. \"Testing Handbook\". Testing Series. 20.", articleBuiler(text6))
    }

    //[auth] and [coauth]\n[year]. "[tit]". [publication] [vol]:[nr], [page].
    private fun articleBuiler(text: TestText): String {
        return BiblioBuilder().add(BiblioBuilder.getAuthorCoAuthor(text.auth, text.coauth, " and "), "", "%s"+NL)
                .down("", "")
                .add(text.year, "", "")
                .add(text.title, ". ", "\"%s\"")
                .add(text.publication, ". ", "")
                .add(text.vol, ". ", "")
                .add(text.nr, ":", "")
                .add(text.page, ", ", "")
                .up()
                .render(".")
    }

    @Test
    fun testContribution() {
        assertEquals("Muster, A." + NL + "2018. \"Testing Handbook\", in Testing Series. Eds. Minder, B., pp. 15-22. Palacington: Springer.", contributionBuiler(full))
        assertEquals("Muster, A." + NL + "\"Testing Handbook\".", contributionBuiler(minimal))
        assertEquals("Muster, A." + NL + "2018. \"Testing Handbook\". Eds. Minder, B., pp. 15-22.", contributionBuiler(text1))
        assertEquals("Muster, A." + NL + "2018. \"Testing Handbook\".", contributionBuiler(text2))
        assertEquals("Muster, A." + NL + "2018. \"Testing Handbook\". Palacington.", contributionBuiler(text3))
        assertEquals("Muster, A." + NL + "2018. \"Testing Handbook\": Springer.", contributionBuiler(text4))
        assertEquals("Muster, A." + NL + "2018. \"Testing Handbook\", in Testing Series: Springer.", contributionBuiler(text5))
        assertEquals("Muster, A." + NL + "2018. \"Testing Handbook\", in Testing Series.", contributionBuiler(text6))
    }

    //[auth]\n[year]. "[tit]", in [publication]. Eds. [coauth], pp. [page]. [place]: [publisher].
    private fun contributionBuiler(text: TestText): String {
        return BiblioBuilder().add(text.auth, "", "%s"+NL)
                .down("", "")
                .add(text.year, "", "")
                .add(text.title, ". ", "\"%s\"")
                .add(text.publication, ", in ", "")
                .add(text.coauth, ". Eds. ", "")
                .add(text.page, ", pp. ", "")
                .add(text.place, ". ", "")
                .add(text.publisher, ": ", "")
                .up()
                .render(".")
    }

    @Test
    fun testWebpage() {
        assertEquals("Muster, A. and Minder, B." + NL + "2018. \"Testing Handbook. How-to for beginners\", Testing Series. (accessed Palacington)", webpageBuiler(full))
        assertEquals("Muster, A." + NL + "\"Testing Handbook\"", webpageBuiler(minimal))
        assertEquals("Muster, A. and Minder, B." + NL + "2018. \"Testing Handbook\"", webpageBuiler(text1))
        assertEquals("Muster, A." + NL + "2018. \"Testing Handbook. How-to for beginners\"", webpageBuiler(text2))
        assertEquals("Muster, A." + NL + "2018. \"Testing Handbook. How-to for beginners\". (accessed Palacington)", webpageBuiler(text3))
        assertEquals("Muster, A." + NL + "2018. \"Testing Handbook. How-to for beginners\"", webpageBuiler(text4))
        assertEquals("Muster, A." + NL + "2018. \"Testing Handbook. How-to for beginners\", Testing Series", webpageBuiler(text5))
        assertEquals("Muster, A." + NL + "2018. \"Testing Handbook. How-to for beginners\", Testing Series", webpageBuiler(text6))
    }

    //[auth] and [coauth]\n[year]. "[tit]. [subtit]", [publication]. (accessed [place])
    private fun webpageBuiler(text: TestText): String {
        return BiblioBuilder().add(BiblioBuilder.getAuthorCoAuthor(text.auth, text.coauth, " and "), "", "%s"+NL)
                .down("", "")
                .add(text.year, "", "")
                .down(". ", "\"%s\"")
                .add(text.title, "", "")
                .add(text.subtit, ". ", "")
                .up()
                .add(text.publication, ", ", "")
                .add(text.place, ". ", "(accessed %s)")
                .up()
                .render("")
    }

//    ---

    private data class TestText(val auth: String, val coauth: String, val year: String, val title: String, val subtit: String, val place: String, val publisher: String, val publication: String, val vol: String, val nr: String, val page: String) {

    }

}