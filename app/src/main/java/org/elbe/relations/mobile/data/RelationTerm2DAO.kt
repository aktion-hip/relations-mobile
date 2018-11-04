package org.elbe.relations.mobile.data

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Query
import org.elbe.relations.mobile.model.Term

/**
 * Term: JOIN on item 1, Filter on item 2
 */
@Dao
interface RelationTerm2DAO {

    @Query("SELECT TermId, sTitle, sText, dtCreation, dtMutation " +
            "FROM tblRelation INNER JOIN tblTerm ON tblRelation.nItem1 = tblTerm.TermId " +
            "WHERE tblRelation.nType1 = 1 AND tblRelation.nType2 = :type AND tblRelation.nItem2 = :id")
    fun getTermsOf(id: Long, type: Int): List<Term>

}