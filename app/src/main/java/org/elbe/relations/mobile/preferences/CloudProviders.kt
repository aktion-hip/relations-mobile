package org.elbe.relations.mobile.preferences

import android.content.res.Resources
import org.elbe.relations.mobile.R
import org.xmlpull.v1.XmlPullParser

/**
 * Helper class to retrieve the configured cloud providers, see res\xml\cloud_provider.xml
 */
class CloudProviders(resources: Resources) {
    val mProviders: List<ProviderModel> by lazy { retrieve(resources)}

    private fun retrieve(resources: Resources): List<ProviderModel> {
        val providers = mutableListOf<ProviderModel>()
        val parser = resources.getXml(R.xml.cloud_provider)
        parser.use {parser ->
            var event = parser.getEventType()
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    if (parser.name == "CloudProvider") {
                        providers.add(ProviderModel(
                                parser.getAttributeValue(null, "name"),
                                parser.getAttributeValue(null, "id"),
                                parser.getAttributeValue(null, "class"),
                                parser.getAttributeValue(null, "hint")))
                    }
                }
                event = parser.next()
            }
        }
        return providers
    }

    /**
     * Returns the list of cloud providers configured in <code>res\xml\cloud_provider.xml</code>
     *
     * @return List<ProviderModel>
     */
    fun getProviders(): List<ProviderModel> {
        return mProviders
    }

    data class ProviderModel(val name: String, val id: String, val className: String, val hint: String) { }

}