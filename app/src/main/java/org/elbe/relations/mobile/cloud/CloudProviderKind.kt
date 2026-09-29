package org.elbe.relations.mobile.cloud

/**
 * The cloud providers supported by the app, see res\xml\cloud_provider.xml.
 */
enum class CloudProviderKind(val id: String) {
    DROPBOX("dropbox"),
    MS_AZURE("ms_azure"),
    P2P("p2p");

    companion object {
        /** The provider preselected when no supported provider is stored. */
        const val DEFAULT_ID = "dropbox"

        /**
         * @param id String? the stored provider id
         * @return CloudProviderKind? the supported provider, or null if the id is unknown (e.g. the removed "google_drive")
         */
        fun fromId(id: String?): CloudProviderKind? = values().firstOrNull { it.id == id }

        /**
         * @param storedId String? the stored provider id
         * @param availableIds List<String> the ids of the configured providers
         * @return String the stored id if it is available, else the default provider's id
         */
        fun selectableId(storedId: String?, availableIds: List<String>): String =
                if (storedId != null && storedId in availableIds) storedId else DEFAULT_ID
    }
}
