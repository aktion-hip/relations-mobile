package org.elbe.relations.mobile.data

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.OnConflictStrategy
import android.arch.persistence.room.Query
import org.elbe.relations.mobile.model.Term

/**
 * The DAO interface for the table tblTerm.
 */
@Dao
interface TermDAO {
    @Query("SELECT * FROM tblTerm ORDER BY sTitle")
    fun getAll(): List<Term>

    @Query("SELECT Count(TermId) FROM tblTerm")
    fun getCount(): Int

    @Query("SELECT * FROM tblTerm WHERE TermId=:id LIMIT 1")
    fun findById(id: Long): Term

    @Query("DELETE FROM tblTerm")
    fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(term: Term)
}