package org.elbe.relations.mobile.model

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.Index
import android.arch.persistence.room.PrimaryKey
import java.io.Serializable

/**
 * Model for relations between items.
 *
 * Created by lbenno on 02.03.2018.
 */
@Entity(tableName = "tblRelation",
        indices = [Index(name = "idxRelation_01", value = arrayOf("nType1", "nItem1")),
            Index(name = "idxRelation_02", value = arrayOf("nType2", "nItem2"))])
data class Relation(@PrimaryKey(autoGenerate = true)
                    @ColumnInfo(name = "RelationID") var id: Long,
                    @ColumnInfo(name = "nType1") var type1: Int,
                    @ColumnInfo(name = "nItem1") var item1: Long,
                    @ColumnInfo(name = "nType2") var type2: Int,
                    @ColumnInfo(name = "nItem2") var item2: Long) : Serializable {
}