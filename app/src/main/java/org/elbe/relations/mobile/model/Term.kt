package org.elbe.relations.mobile.model

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey
import android.content.res.Resources
import org.apache.lucene.document.Document
import org.apache.lucene.index.IndexWriter
import org.elbe.relations.mobile.R
import java.io.Serializable
import java.util.*

/**
 * Model for term items (type 1).
 *
 * Created by lbenno on 02.03.2018.
 */
@Entity(tableName = "tblTerm")
data class Term(@PrimaryKey(autoGenerate = true)
                @ColumnInfo(name = "TermId") private val id: Long,
                @ColumnInfo(name = "sTitle", index = true) private var title: String,
                @ColumnInfo(name = "sText") var text: String,
                @ColumnInfo(name = "dtCreation", index = true) private var creationDate: Date = Date(0),
                @ColumnInfo(name = "dtMutation", index = true) private var mutationDate: Date = Date(0)) : Item {
    override fun getId() = id
    override fun getTitle() = title
    override fun getDetailText(r: Resources) = text
    override fun getCreationDate() = creationDate
    override fun getMutationDate() = mutationDate
    override fun getType(): Type = Type.TERM

    override fun indexContent(writer: IndexWriter) {
        val document = Document()
        document.add(getFieldUniqueID())
        document.add(getFieldItemType())
        document.add(getFieldItemID())
        document.add(getFieldTitle(getTitle()))

        document.add(getFieldText(getFullTextHelper(text).add(getTitle())))

        addFieldCreatedModified(document)
        writer.addDocument(document)
    }
}