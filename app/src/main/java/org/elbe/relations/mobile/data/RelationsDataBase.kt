package org.elbe.relations.mobile.data

import android.arch.persistence.db.SupportSQLiteDatabase
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
@Database(entities = [Term::class, Text::class, Person::class, Relation::class], version = 1)
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
        const val FTS_TABLE_NAME: String = "fts"
        const val COL_ID: String = "item_id"
        const val COL_TYPE: String = "item_type"
        const val COL_CONTENT: String = "content"
        const val FTS_TABLE_CREATE: String = "CREATE VIRTUAL TABLE $FTS_TABLE_NAME USING fts3 ($COL_ID, $COL_TYPE, $COL_CONTENT)"
//        const val FTS_TABLE_CREATE: String = "CREATE VIRTUAL TABLE $FTS_TABLE_NAME USING fts3 ($COL_ID, $COL_TYPE, $COL_CONTENT, tokenize=icu de_DE)"
        const val FTS_TABLE_DROP: String = "DROP TABLE IF EXISTS $FTS_TABLE_NAME"

        private var INSTANCE: RelationsDataBase? = null

        fun getInstance(context: Context): RelationsDataBase? {
            if (INSTANCE == null) {
                synchronized(RelationsDataBase::class) {
                    INSTANCE = Room.databaseBuilder(context.applicationContext,
                            RelationsDataBase::class.java, "Relations.db")
                            .addCallback(CALLBACK).build()
                }
            }
            return INSTANCE
        }

        fun destroyInstance() {
            INSTANCE = null
        }

        private val CALLBACK = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                db.execSQL(FTS_TABLE_DROP)
                db.execSQL(FTS_TABLE_CREATE)
            }
        }
    }

    /**
     * @return SupportSQLiteDatabase?
     */
    fun getWritable(context: Context): SupportSQLiteDatabase? {
        return getInstance(context)?.openHelper?.writableDatabase
    }

    /**
     * @return SupportSQLiteDatabase?
     */
    fun getReadable(context: Context): SupportSQLiteDatabase? {
        return getInstance(context)?.openHelper?.readableDatabase
    }
}