package com.unirhy.e2e.support

fun newAdminSession(baseUrl: String): E2eAdminSession {
    val runtime = E2eRuntime.context
    return E2eAdminSession(
        api = E2eHttpClient(baseUrl),
        runtime = runtime,
        credentials = runtime.admin,
    )
}

fun bootstrapAdminSession(baseUrl: String): E2eAdminSession {
    val state = newAdminSession(baseUrl)
    ensureSystemInitialized(state)
    loginAsAdmin(state)
    return state
}

data class E2eAdminSession(
    val api: E2eHttpClient,
    val runtime: E2eRunContext,
    val credentials: AdminCredentials,
)

fun ensureSystemInitialized(state: E2eAdminSession) {
    val statusResponse = state.api.get("/api/system/config/status")
    E2eAssert.status(statusResponse, 200, "[prepare] status endpoint should be reachable")
    val initialized = E2eJson.mapper.readTree(statusResponse.body()).path("initialized").asBoolean(false)
    if (initialized) {
        return
    }

    val initResponse = state.api.post(
        path = "/api/system/config",
        json = systemInitRequest(state),
    )
    E2eAssert.status(initResponse, 201, "[prepare] system initialization should succeed")
}

fun loginAsAdmin(state: E2eAdminSession) {
    val loginResponse = state.api.post(
        path = "/api/tokens",
        json = mapOf(
            "email" to state.credentials.email,
            "password" to state.credentials.password,
        ),
    )
    E2eAssert.status(loginResponse, 200, "[prepare] admin login should succeed")
}

fun systemInitRequest(state: E2eAdminSession): Map<String, Any> {
    return linkedMapOf(
        "adminAccountName" to state.credentials.name,
        "adminPassword" to state.credentials.password,
        "adminAccountEmail" to state.credentials.email,
        "storageProviderPath" to state.runtime.scanWorkspace.toAbsolutePath().toString(),
    )
}
