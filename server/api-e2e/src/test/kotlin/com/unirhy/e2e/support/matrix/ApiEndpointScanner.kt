package com.unirhy.e2e.support.matrix

import cn.dev33.satoken.annotation.SaCheckLogin
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController
import java.lang.reflect.Method

object ApiEndpointScanner {
    private const val BASE_PACKAGE = "com.coooolfan.unirhy.controller"

    fun scan(): List<ApiEndpoint> {
        val scanner = ClassPathScanningCandidateComponentProvider(false).apply {
            addIncludeFilter(AnnotationTypeFilter(RestController::class.java))
        }
        val endpoints = mutableListOf<ApiEndpoint>()

        scanner.findCandidateComponents(BASE_PACKAGE)
            .asSequence()
            .mapNotNull { it.beanClassName }
            .sorted()
            .map { Class.forName(it) }
            .forEach { controllerClass ->
                val classPaths = extractPaths(controllerClass.getAnnotation(RequestMapping::class.java))
                val classRequiresLogin = controllerClass.isAnnotationPresent(SaCheckLogin::class.java)
                controllerClass.declaredMethods.forEach { method ->
                    val mappings = extractMappings(method)
                    if (mappings.isEmpty()) {
                        return@forEach
                    }
                    val methodRequiresLogin = classRequiresLogin || method.isAnnotationPresent(SaCheckLogin::class.java)
                    val owner = "${controllerClass.simpleName}#${method.name}"
                    mappings.forEach { mapping ->
                        classPaths.forEach { classPath ->
                            mapping.paths.forEach { methodPath ->
                                endpoints += ApiEndpoint(
                                    method = mapping.httpMethod,
                                    path = normalizePath(classPath, methodPath),
                                    condition = mapping.headerCondition,
                                    requiresLogin = methodRequiresLogin,
                                    controllerMethod = owner,
                                )
                            }
                        }
                    }
                }
            }

        return endpoints.sortedWith(
            compareBy<ApiEndpoint>({ it.path }, { it.method }, { it.condition }, { it.controllerMethod }),
        )
    }

    private fun extractMappings(method: Method): List<MethodMapping> {
        val requestMappings = method.getAnnotationsByType(RequestMapping::class.java)
            .flatMap { requestMapping ->
                val methods = requestMapping.method.map { it.name }
                if (methods.isEmpty()) {
                    emptyList()
                } else {
                    methods.map { httpMethod ->
                        MethodMapping(
                            httpMethod = httpMethod,
                            paths = extractPaths(requestMapping.path, requestMapping.value),
                            headerCondition = normalizeHeaders(requestMapping.headers),
                        )
                    }
                }
            }

        val getMappings = method.getAnnotationsByType(GetMapping::class.java).map { mapping ->
            MethodMapping(
                httpMethod = RequestMethod.GET.name,
                paths = extractPaths(mapping.path, mapping.value),
                headerCondition = normalizeHeaders(mapping.headers),
            )
        }
        val postMappings = method.getAnnotationsByType(PostMapping::class.java).map { mapping ->
            MethodMapping(
                httpMethod = RequestMethod.POST.name,
                paths = extractPaths(mapping.path, mapping.value),
                headerCondition = normalizeHeaders(mapping.headers),
            )
        }
        val putMappings = method.getAnnotationsByType(PutMapping::class.java).map { mapping ->
            MethodMapping(
                httpMethod = RequestMethod.PUT.name,
                paths = extractPaths(mapping.path, mapping.value),
                headerCondition = normalizeHeaders(mapping.headers),
            )
        }
        val deleteMappings = method.getAnnotationsByType(DeleteMapping::class.java).map { mapping ->
            MethodMapping(
                httpMethod = RequestMethod.DELETE.name,
                paths = extractPaths(mapping.path, mapping.value),
                headerCondition = normalizeHeaders(mapping.headers),
            )
        }
        val patchMappings = method.getAnnotationsByType(PatchMapping::class.java).map { mapping ->
            MethodMapping(
                httpMethod = RequestMethod.PATCH.name,
                paths = extractPaths(mapping.path, mapping.value),
                headerCondition = normalizeHeaders(mapping.headers),
            )
        }

        return requestMappings + getMappings + postMappings + putMappings + deleteMappings + patchMappings
    }

    private fun extractPaths(mapping: RequestMapping?): List<String> {
        if (mapping == null) {
            return listOf("")
        }
        return extractPaths(mapping.path, mapping.value)
    }

    private fun extractPaths(path: Array<String>, value: Array<String>): List<String> {
        val paths = (path.toList() + value.toList())
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("") }
        return paths.distinct()
    }

    private fun normalizePath(classPath: String, methodPath: String): String {
        val left = classPath.trim().removeSuffix("/")
        val right = methodPath.trim().removePrefix("/")
        val merged = when {
            left.isBlank() && right.isBlank() -> "/"
            left.isBlank() -> "/$right"
            right.isBlank() -> left
            else -> "$left/$right"
        }
        return merged
            .replace(Regex("/{2,}"), "/")
            .let { if (it.isBlank()) "/" else it }
    }

    private fun normalizeHeaders(headers: Array<String>): String {
        return headers
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" && ")
    }
}

data class ApiEndpoint(
    val method: String,
    val path: String,
    val condition: String,
    val requiresLogin: Boolean,
    val controllerMethod: String,
)

data class ApiEndpointKey(
    val method: String,
    val path: String,
    val condition: String,
)

fun ApiEndpoint.toKey(): ApiEndpointKey {
    return ApiEndpointKey(
        method = method,
        path = path,
        condition = condition,
    )
}

private data class MethodMapping(
    val httpMethod: String,
    val paths: List<String>,
    val headerCondition: String,
)
