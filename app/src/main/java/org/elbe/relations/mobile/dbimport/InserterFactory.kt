package org.elbe.relations.mobile.dbimport

import java.util.function.Supplier

/**
 * Factory for inserters modelled as enum.
 */
enum class InserterFactory(val entryType: String,
                           val nodeName: String,
                           val producer: () -> DBImport.IEntryInserter) {
    TERMS("TermEntries", "TermEntry", { DBImport.TermInserter() }),
    TEXTS("TextEntries", "TextEntry", { DBImport.TextInserter() }),
    PERSONS("PersonEntries", "PersonEntry", { DBImport.PersonInserter() }),
    RELATIONS("RelationEntries", "RelationEntry", { DBImport.RelationInserter() });

    /**
     * Checks whether the specified type is a table node, e.g. <TermEntries>
     */
    fun checkType(type: String?): Boolean {
        return entryType.equals(type, true)
    }

    /**
     * Checks whether the specified node is an entry node, e.g. <TermEntry>
     */
    fun checkNode(name: String?): Boolean {
        return nodeName.equals(name, true)
    }

    /**
     * @return DBImport.IEntryInserter the inserter for this table's entries
     */
    fun createInserter(entryName: String?): DBImport.IEntryInserter {
        return producer.invoke()
    }
}