package org.elbe.relations.mobile.data

import android.arch.persistence.room.*
import org.elbe.relations.mobile.model.Relation

/**
 * The DAO interface to delete all entries in the Relations table.
 */
@Dao
interface RelationDAO {

    @Query("DELETE FROM tblRelation")
    fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(relation: Relation)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(relation: Relation)

    @Query("SELECT Count(RelationID) FROM tblRelation")
    fun getCount(): Int

    @Query("DELETE FROM tblRelation WHERE RelationID=:id")
    fun delete(id: Long)

    @Query("SELECT * FROM tblRelation WHERE RelationID=:id LIMIT 1")
    fun findById(id: Long): Relation

}