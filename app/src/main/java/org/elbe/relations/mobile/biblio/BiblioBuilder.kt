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

    fun author(author: String, coAuthor: String, concat: String): BiblioBuilder {
        return this
    }

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
        val prefix = builder.prefix
        if (this.builder.parent != null) {
            this.builder = this.builder.parent!!
            this.builder.addPart(part, prefix, "")
        }
        return this
    }

    fun render(end: String?): String {
        return builder.render(end)
    }

//    ---

    private class InternalBuilder {
        var parent: InternalBuilder? = null
        var prefix: String = ""
        var template: String = ""
        private var rendered: String = ""

        constructor(parent: InternalBuilder?) {
            this.parent = parent
        }

        fun addPart(value: String, separator: String, template: String) : InternalBuilder {
            if (value.isNotBlank()) {
                if (rendered.isNotBlank()) {
                    rendered += separator
                }
                if (template.isEmpty()) {
                    rendered += value
                } else {
                    rendered += String.format(template, value)
                }
            }
            return this
        }

        fun render(end: String?): String {
            if (!end.isNullOrBlank()) {
                rendered += end
            }
            return if (template.isEmpty()) rendered else String.format(template, rendered)
        }

        fun addPrefix(prefix: String): InternalBuilder {
            this.prefix = prefix
            return this
        }

        fun addTemplate(template: String): InternalBuilder {
            this.template = template
            return this
        }

    }
}