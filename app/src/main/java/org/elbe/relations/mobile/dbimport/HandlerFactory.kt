package org.elbe.relations.mobile.dbimport

/**
 *  Factory for entry handlers modelled as enum.
 */
enum class HandlerFactory(private val entryType: String, private val nodeName: String, private val producer: () -> EntryHandler) {
    TERMS("TermEntries", "TermEntry", { TermHandler() }),
    TEXTS("TextEntries", "TextEntry", { TextHandler() }),
    PERSONS("PersonEntries", "PersonEntry", { PersonHandler() }),
    RELATIONS("RelationEntries", "RelationEntry", { RelationHandler() });

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
     * @return EntryHandler the for this table's entries
     */
    fun createHandler(): EntryHandler {
        return producer.invoke()
    }

}