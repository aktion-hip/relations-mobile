package org.elbe.relations.mobile.data

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy
import android.arch.persistence.room.Query
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
}