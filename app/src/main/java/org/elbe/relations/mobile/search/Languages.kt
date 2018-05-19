package org.elbe.relations.mobile.search

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.ar.ArabicAnalyzer
import org.apache.lucene.analysis.bg.BulgarianAnalyzer
import org.apache.lucene.analysis.br.BrazilianAnalyzer
import org.apache.lucene.analysis.ca.CatalanAnalyzer
import org.apache.lucene.analysis.cz.CzechAnalyzer
import org.apache.lucene.analysis.da.DanishAnalyzer
import org.apache.lucene.analysis.de.GermanAnalyzer
import org.apache.lucene.analysis.el.GreekAnalyzer
import org.apache.lucene.analysis.es.SpanishAnalyzer
import org.apache.lucene.analysis.eu.BasqueAnalyzer
import org.apache.lucene.analysis.fa.PersianAnalyzer
import org.apache.lucene.analysis.fi.FinnishAnalyzer
import org.apache.lucene.analysis.fr.FrenchAnalyzer
import org.apache.lucene.analysis.gl.GalicianAnalyzer
import org.apache.lucene.analysis.hi.HindiAnalyzer
import org.apache.lucene.analysis.hu.HungarianAnalyzer
import org.apache.lucene.analysis.hy.ArmenianAnalyzer
import org.apache.lucene.analysis.id.IndonesianAnalyzer
import org.apache.lucene.analysis.it.ItalianAnalyzer
import org.apache.lucene.analysis.lv.LatvianAnalyzer
import org.apache.lucene.analysis.nl.DutchAnalyzer
import org.apache.lucene.analysis.no.NorwegianAnalyzer
import org.apache.lucene.analysis.pt.PortugueseAnalyzer
import org.apache.lucene.analysis.ro.RomanianAnalyzer
import org.apache.lucene.analysis.ru.RussianAnalyzer
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.sv.SwedishAnalyzer
import org.apache.lucene.analysis.th.ThaiAnalyzer
import org.apache.lucene.analysis.tr.TurkishAnalyzer
import org.apache.lucene.util.Version

/**
 * The lucene analyzer languages.
 */
enum class Languages(val isoLanguage: String, val title: String, val analyzer: Analyzer) {
    AR("ar", "Arabic", ArabicAnalyzer(LUCENE_VERSION)),
    BG("bg", "Bulgarian", BulgarianAnalyzer(LUCENE_VERSION)),
    BR("br", "Brazil", BrazilianAnalyzer(LUCENE_VERSION)),
    CA("ca", "Catalan", CatalanAnalyzer(LUCENE_VERSION)),
    CZ("cz", "Czech", CzechAnalyzer(LUCENE_VERSION)),
    DA("da", "Danish", DanishAnalyzer(LUCENE_VERSION)),
    DE("de", "Deutsch", GermanAnalyzer(LUCENE_VERSION)),
    EL("el", "Greek", GreekAnalyzer(LUCENE_VERSION)),
    EN("en", "English", StandardAnalyzer(LUCENE_VERSION)),
    ES("es", "Spanish", SpanishAnalyzer(LUCENE_VERSION)),
    EU("eu", "Basque", BasqueAnalyzer(LUCENE_VERSION)),
    FA("fa", "Persian", PersianAnalyzer(LUCENE_VERSION)),
    FI("fi", "Finnish", FinnishAnalyzer(LUCENE_VERSION)),
    FR("fr", "French", FrenchAnalyzer(LUCENE_VERSION)),
    GL("gl", "Galician", GalicianAnalyzer(LUCENE_VERSION)),
    HI("hi", "Hindi", HindiAnalyzer(LUCENE_VERSION)),
    HU("hu", "Hungarian", HungarianAnalyzer(LUCENE_VERSION)),
    HY("hy", "Armenian", ArmenianAnalyzer(LUCENE_VERSION)),
    ID("id", "Indonesian", IndonesianAnalyzer(LUCENE_VERSION)),
    IT("it", "Italian", ItalianAnalyzer(LUCENE_VERSION)),
    LV("lv", "Latvian", LatvianAnalyzer(LUCENE_VERSION)),
    NL("nl", "Dutch", DutchAnalyzer(LUCENE_VERSION)),
    NO("no", "Norwegian", NorwegianAnalyzer(LUCENE_VERSION)),
    PT("pt", "Portuguese", PortugueseAnalyzer(LUCENE_VERSION)),
    RO("ro", "Romanian", RomanianAnalyzer(LUCENE_VERSION)),
    RU("ru", "Russian", RussianAnalyzer(LUCENE_VERSION)),
    SV("sv", "Swedish", SwedishAnalyzer(LUCENE_VERSION)),
    TH("th", "Thai", ThaiAnalyzer(LUCENE_VERSION)),
    TR("tr", "Turkish", TurkishAnalyzer(LUCENE_VERSION));

    //---

    companion object {
        /**
         * Returns the analyzer for the specified language.
         *
         * @param language: String
         * @return Analyzer
         */
        fun getAnalyzer(language: String) : Analyzer {
            Languages.values().forEach {
                if (it.isoLanguage == language) {
                    return it.analyzer
                }
            }
            return Languages.EN.analyzer
        }
    }
}