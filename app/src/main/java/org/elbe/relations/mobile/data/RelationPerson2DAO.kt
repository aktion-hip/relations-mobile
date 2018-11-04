package org.elbe.relations.mobile.data

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Query
import org.elbe.relations.mobile.model.Person

/**
 * Created by lbenno on 11.03.2018.
 */
@Dao
interface RelationPerson2DAO {

    @Query("SELECT PersonID, sName, sFirstname, sText, sFrom, sTo, dtCreation, dtMutation " +
            "FROM tblRelation INNER JOIN tblPerson ON tblRelation.nItem1 = tblPerson.PersonID " +
            "WHERE tblRelation.nType1 = 3 AND tblRelation.nType2 = :type AND tblRelation.nItem2 = :id")
    fun getPersonsOf(id: Long, type: Int): List<Person>

}