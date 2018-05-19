package org.elbe.relations.mobile.data

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Query
import org.elbe.relations.mobile.model.Term

/**
 * Term: JOIN on item 2, Filter on item 1
 */
@Dao
interface RelationTerm1DAO {

    @Query("SELECT * " +
            "FROM tblRelation INNER JOIN tblTerm ON tblRelation.nItem2 = tblTerm.TermId " +
            "WHERE tblRelation.nType2 = 1 AND tblRelation.nType1 = :type AND tblRelation.nItem1 = :id")
    fun getTermsOf(id: Long, type: Int): MutableList<Term>

}