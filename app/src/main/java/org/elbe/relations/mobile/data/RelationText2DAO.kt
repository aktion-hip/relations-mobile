package org.elbe.relations.mobile.data

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Query
import org.elbe.relations.mobile.model.Text

/**
 * Text: JOIN on item 1, Filter on item 2
 */
@Dao
interface RelationText2DAO {

    @Query("SELECT * " +
            "FROM tblRelation INNER JOIN tblText ON tblRelation.nItem1 = tblText.TextID " +
            "WHERE tblRelation.nType1 = 2 AND tblRelation.nType2 = :type AND tblRelation.nItem2 = :id")
    fun getTextsOf(id: Long, type: Int): List<Text>
}