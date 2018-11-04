package org.elbe.relations.mobile.biblio

/**
 * Created by lbenno on 13.03.2018.
 */
class BiblioBuilder {
    private var builder: InternalBuilder = InternalBuilder(null)

    companion object {
        fun getAuthorCoAuthor(author: String, coAuthor: String, concat: String):String {
            return if (coAuthor.isBlank()) author else "$author$concat$coAuthor"
        }
    }

//    fun author(author: String, coAuthor: String, concat: String): BiblioBuilder {
//        return this
//    }

    fun add(value: String, separator: String, template: String): BiblioBuilder {
        builder.addPart(value, separator, template)
        return this
    }

    fun down(separator: String, template: String): BiblioBuilder {
        builder = InternalBuilder(builder).addPrefix(separator).addTemplate(template)
        return this
    }

    fun up(): BiblioBuilder {
        val part = builder.render(null)
        val prefix = builder.mPrefix
        if (this.builder.mParent != null) {
            this.builder = this.builder.mParent!!
            this.builder.addPart(part, prefix, "")
        }
        return this
    }

    fun render(end: String?): String {
        return builder.render(end)
    }

//    ---

    private class InternalBuilder(parent: InternalBuilder?) {
        var mParent= parent
        var mPrefix: String = ""
        var mTemplate: String = ""
        private var mRendered: String = ""

        fun addPart(value: String, separator: String, template: String) : InternalBuilder {
            if (value.isNotBlank()) {
                if (mRendered.isNotBlank()) {
                    mRendered += separator
                }
                if (template.isEmpty()) {
                    mRendered += value
                } else {
                    mRendered += String.format(template, value)
                }
            }
            return this
        }

        fun render(end: String?): String {
            if (!end.isNullOrBlank()) {
                mRendered += end
            }
            return if (mTemplate.isEmpty()) mRendered else String.format(mTemplate, mRendered)
        }

        fun addPrefix(prefix: String): InternalBuilder {
            this.mPrefix = prefix
            return this
        }

        fun addTemplate(template: String): InternalBuilder {
            this.mTemplate = template
            return this
        }

    }
}