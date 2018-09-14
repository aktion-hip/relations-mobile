package org.elbe.relations.mobile.data

import android.arch.persistence.room.*
import org.elbe.relations.mobile.model.Person

/**
 * The DAO interface for the table tblPerson.
 */
@Dao
interface PersonDAO {
    @Query("SELECT * FROM tblPerson ORDER BY sName")
    fun getAll(): List<Person>

    @Query("SELECT Count(PersonId) FROM tblPerson")
    fun getCount(): Int

    @Query("SELECT * FROM tblPerson WHERE PersonId=:id LIMIT 1")
    fun findById(id: Long): Person

    @Query("DELETE FROM tblPerson")
    fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(person: Person)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(person: Person)

    @Delete()
    fun delete(person: Person)

    @Query("DELETE FROM tblPerson WHERE PersonId=:id")
    fun delete(id: Long)

}