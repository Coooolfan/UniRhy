package com.unirhy.e2e.support

import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper

object E2eJson {
    val mapper: ObjectMapper = JsonMapper()
}
