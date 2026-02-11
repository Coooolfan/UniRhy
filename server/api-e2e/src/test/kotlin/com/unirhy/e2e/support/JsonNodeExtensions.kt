package com.unirhy.e2e.support

import com.fasterxml.jackson.databind.JsonNode

fun JsonNode.findMediaIdByObjectKey(objectKey: String): Long? {
    if (isObject) {
        val objectKeyNode = get("objectKey")
        val idNode = get("id")
        if (objectKeyNode != null && idNode != null && objectKeyNode.asText() == objectKey && idNode.canConvertToLong()) {
            return idNode.asLong()
        }

        val names = fieldNames()
        while (names.hasNext()) {
            val child = get(names.next()) ?: continue
            val result = child.findMediaIdByObjectKey(objectKey)
            if (result != null) {
                return result
            }
        }
        return null
    }

    if (isArray) {
        for (child in this) {
            val result = child.findMediaIdByObjectKey(objectKey)
            if (result != null) {
                return result
            }
        }
    }
    return null
}
