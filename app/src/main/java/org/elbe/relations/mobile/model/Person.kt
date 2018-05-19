package org.elbe.relations.mobile.model

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.Index
import android.arch.persistence.room.PrimaryKey
import android.content.res.Resources
import org.apache.lucene.document.Document
import org.apache.lucene.index.IndexWriter
import org.elbe.relations.mobile.R
import java.util.*

/**
 * Model for person items (type 3).
 *
 * Created by lbenno on 02.03.2018.
 */
@Entity(tableName = "tblPerson",
        indices = [Index(name = "idxPerson_01", value = arrayOf("sName", "sFirstname")),
                Index(name = "idxPerson_02", value = arrayOf("sFrom", "sTo"))])
data class Person(@PrimaryKey(autoGenerate = true)
                  @ColumnInfo(name = "PersonID") private val id: Long,
                  @ColumnInfo(name = "sName") var name: String,
                  @ColumnInfo(name = "sFirstname") var firstname: String?,
                  @ColumnInfo(name = "sText") var text: String,
                  @ColumnInfo(name = "sFrom") var from: String?,
                  @ColumnInfo(name = "sTo") var to: String?,
                  @ColumnInfo(name = "dtCreation", index = true) private var creationDate: Date,
                  @ColumnInfo(name = "dtMutation", index = true) private var mutationDate: Date) : Item {
    override fun getId() = id
    override fun getTitle() = if (firstname == null) name else "$name, $firstname"
    override fun getCreationDate() = creationDate
    override fun getMutationDate() = mutationDate
    override fun getType(): Type = Type.PERSON
    override fun getDetailText(r: Resources): String {
        var out = ""
        if (from!!.isNotBlank()) {
            out = ("${r.getString(R.string.person_from)}: $from" + if (to!!.isNotBlank()) ", ${r.getString(R.string.person_to)}: $to" else "") + "<br />"
        }
        out += text
        return out
    }

    override fun indexContent(writer: IndexWriter) {
        val document = Document()
        document.add(getFieldUniqueID())
        document.add(getFieldItemType())
        document.add(getFieldItemID())
        document.add(getFieldTitle(String.format("%s %s", name, firstname)))

        val fullText = getFullTextHelper(text)
        fullText.add(name)
        fullText.add(firstname)
        fullText.add(from)
        fullText.add(to)
        document.add(getFieldText(fullText))

        addFieldCreatedModified(document)
        writer.addDocument(document)
    }
}