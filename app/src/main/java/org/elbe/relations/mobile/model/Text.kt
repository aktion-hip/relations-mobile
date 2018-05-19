package org.elbe.relations.mobile.model

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.Index
import android.arch.persistence.room.PrimaryKey
import android.content.res.Resources
import org.apache.lucene.document.Document
import org.apache.lucene.index.IndexWriter
import org.elbe.relations.mobile.biblio.BiblioBuilder
import java.util.*

/**
 * Model for text items (type 2).
 *
 * Created by lbenno on 02.03.2018.
 */

const val NL = "<br />"

@Entity(tableName = "tblText",
        indices = [Index(name = "idxText", value = arrayOf("sAuthor", "sCoAuthors"))])
data class Text(@PrimaryKey(autoGenerate = true)
                @ColumnInfo(name = "TextID") private val id: Long,
                @ColumnInfo(name = "sTitle", index = true) private var title: String,
                @ColumnInfo(name = "sText") var text: String,
                @ColumnInfo(name = "sAuthor") var author: String?,
                @ColumnInfo(name = "sCoAuthors") var coAuthors: String?,
                @ColumnInfo(name = "sSubtitle") var subtitle: String?,
                @ColumnInfo(name = "sYear") var year: String?,
                @ColumnInfo(name = "sPublication") var publication: String?,
                @ColumnInfo(name = "sPages") var pages: String?,
                @ColumnInfo(name = "nVolume") var volume: Int?,
                @ColumnInfo(name = "nNumber") var number: Int?,
                @ColumnInfo(name = "sPublisher") var publisher: String?,
                @ColumnInfo(name = "sPlace") var place: String?,
                @ColumnInfo(name = "nType") var textType: Int?,
                @ColumnInfo(name = "dtCreation", index = true) private var creationDate: Date = Date(0),
                @ColumnInfo(name = "dtMutation", index = true) private var mutationDate: Date = Date(0)) : Item {

    override fun getId() = id
    override fun getTitle() = title
    override fun getCreationDate() = creationDate
    override fun getMutationDate() = mutationDate
    override fun getType(): Type = Type.TEXT
    
    override fun getDetailText(r: Resources): String {
        return "<i>${getBiblioFormatted()}</i>$NL$NL$text"
    }

    private fun getBiblioFormatted(): String {
        when (textType) {
            0 -> return bookBuiler()
            1 -> return articleBuiler()
            2 -> return contributionBuiler()
            3 -> return webpageBuiler()
            else -> return bookBuiler()
        }
    }

    //[auth] and [coauth]\n[year]. [tit]. [subtit]. [place]: [publisher].
    private fun bookBuiler(): String {
        return BiblioBuilder().add(BiblioBuilder.getAuthorCoAuthor(this.author ?:"", this.coAuthors ?:"", " and "), "", "%s"+NL)
                .down("", "")
                .add(this.year ?:"", "", "")
                .add(this.title, ". ", "")
                .add(this.subtitle ?:"", ". ", "")
                .add(this.place ?:"", ". ", "")
                .add(this.publisher ?:"", ": ", "")
                .up()
                .render(".")
    }

    //[auth] and [coauth]\n[year]. "[tit]". [publication] [vol]:[nr], [page].
    private fun articleBuiler(): String {
        return BiblioBuilder().add(BiblioBuilder.getAuthorCoAuthor(this.author ?:"", this.coAuthors ?:"", " and "), "", "%s"+NL)
                .down("", "")
                .add(this.year ?:"", "", "")
                .add(this.title, ". ", "\"%s\"")
                .add(this.publication ?:"", ". ", "")
                .add(this.volume?.toString() ?:"", ". ", "")
                .add(this.number?.toString() ?:"", ":", "")
                .add(this.pages ?:"", ", ", "")
                .up()
                .render(".")
    }

    //[auth]\n[year]. "[tit]", in [publication]. Eds. [coauth], pp. [page]. [place]: [publisher].
    private fun contributionBuiler(): String {
        return BiblioBuilder().add(this.author ?:"", "", "%s"+NL)
                .down("", "")
                .add(this.year ?:"", "", "")
                .add(this.title, ". ", "\"%s\"")
                .add(this.publication ?:"", ", in ", "")
                .add(this.coAuthors ?:"", ". Eds. ", "")
                .add(this.pages ?:"", ", pp. ", "")
                .add(this.place ?:"", ". ", "")
                .add(this.publisher ?:"", ": ", "")
                .up()
                .render(".")
    }

    //[auth] and [coauth]\n[year]. "[tit]. [subtit]", [publication]. (accessed [place])
    private fun webpageBuiler(): String {
        return BiblioBuilder().add(BiblioBuilder.getAuthorCoAuthor(this.author ?:"", this.coAuthors ?:"", " and "), "", "%s"+NL)
                .down("", "")
                .add(this.year ?:"", "", "")
                .down(". ", "\"%s\"")
                .add(this.title, "", "")
                .add(this.subtitle ?:"", ". ", "")
                .up()
                .add(this.publication ?:"", ", ", "")
                .add(this.place ?:"", ". ", "(accessed %s)")
                .up()
                .render("")
    }

    override fun indexContent(writer: IndexWriter) {
        val document = Document()

        document.add(getFieldUniqueID())
        document.add(getFieldItemType())
        document.add(getFieldItemID())
        document.add(getFieldTitle(getTitle()))

        val fullText = getFullTextHelper(text)
        fullText.add(getTitle()).add(author).add(coAuthors).add(number?.toString())
                .add(place).add(publication).add(publisher).add(subtitle).add(volume?.toString())
                .add(year)
        document.add(getFieldText(fullText))

        addFieldCreatedModified(document)
        writer.addDocument(document)
    }
}