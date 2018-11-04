@file:Suppress("NAME_SHADOWING")
package org.elbe.relations.mobile.data

import android.arch.persistence.room.Database
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.arch.persistence.room.TypeConverters
import android.content.Context
import org.elbe.relations.mobile.model.Person
import org.elbe.relations.mobile.model.Relation
import org.elbe.relations.mobile.model.Term
import org.elbe.relations.mobile.model.Text

/**
 * The application's database.
 */
@Database(entities = [Term::class, Text::class, Person::class, Relation::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class RelationsDataBase : RoomDatabase() {
    abstract fun termDAO(): TermDAO
    abstract fun textDAO(): TextDAO
    abstract fun personDAO(): PersonDAO

    abstract fun relationDAO(): RelationDAO
    abstract fun relationTerm1DAO(): RelationTerm1DAO
    abstract fun relationTerm2DAO(): RelationTerm2DAO
    abstract fun relationText1DAO(): RelationText1DAO
    abstract fun relationText2DAO(): RelationText2DAO
    abstract fun relationPerson1DAO(): RelationPerson1DAO
    abstract fun relationPerson2DAO(): RelationPerson2DAO

    companion object {
        private var INSTANCE: RelationsDataBase? = null

        fun getInstance(context: Context?): RelationsDataBase? {
            context?.let {context ->
                if (INSTANCE == null) {
                    synchronized(RelationsDataBase::class) {
                        INSTANCE = Room.databaseBuilder(context.applicationContext,
                                RelationsDataBase::class.java, "Relations.db").build()
                    }
                }
            }
            return INSTANCE
        }

        fun destroyInstance() {
            INSTANCE = null
        }
    }

}