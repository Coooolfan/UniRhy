package com.coooolfan.unirhy.model

import org.babyfish.jimmer.sql.Serialized

@Serialized
data class AccountPreferences(
    val preferredAssetFormat: String = DEFAULT_PREFERRED_ASSET_FORMAT,
) {
    companion object {
        const val DEFAULT_PREFERRED_ASSET_FORMAT: String = "audio/opus"
    }
}
