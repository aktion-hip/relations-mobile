package org.elbe.relations.mobile.data

import android.arch.persistence.room.*
import org.elbe.relations.mobile.model.Text

/**
 * The DAO interface for the table tblText.
 */
@Dao
interface TextDAO {
    @Query("SELECT * FROM tblText ORDER BY sTitle")
    fun getAll(): List<Text>

    @Query("SELECT Count(TextId) FROM tblText")
    fun getCount(): Int

    @Query("SELECT * FROM tblText WHERE TextId=:id LIMIT 1")
    fun findById(id: Long): Text

    @Query("DELETE FROM tblText")
    fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(text: Text)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun update(text: Text)

    @Delete
    fun delete(text: Text)

    @Query("DELETE FROM tblText WHERE TextId=:id")
    fun delete(id: Long)

}