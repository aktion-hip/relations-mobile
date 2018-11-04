package org.elbe.relations.mobile.data

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Query
import org.elbe.relations.mobile.model.Text

/**
 * Text: JOIN on item 2, Filter on item 1
 */
@Dao
interface RelationText1DAO {

    @Query("SELECT TextID, sTitle, sText, sAuthor, sCoAuthors, sSubtitle, sYear, sPublication, sPages, nVolume, nNumber, sPublisher, sPlace, nType, dtCreation, dtMutation " +
            "FROM tblRelation INNER JOIN tblText ON tblRelation.nItem2 = tblText.TextID " +
            "WHERE tblRelation.nType2 = 2 AND tblRelation.nType1 = :type AND tblRelation.nItem1 = :id")
    fun getTextsOf(id: Long, type: Int): List<Text>
}