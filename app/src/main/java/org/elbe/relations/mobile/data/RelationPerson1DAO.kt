package org.elbe.relations.mobile.data

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Query
import org.elbe.relations.mobile.model.Person

/**
 * Person: JOIN on item 2, Filter on item 1
 */
@Dao
interface RelationPerson1DAO {

    @Query("SELECT * " +
            "FROM tblRelation INNER JOIN tblPerson ON tblRelation.nItem2 = tblPerson.PersonID " +
            "WHERE tblRelation.nType2 = 3 AND tblRelation.nType1 = :type AND tblRelation.nItem1 = :id")
    fun getPersonsOf(id: Long, type: Int): List<Person>
}