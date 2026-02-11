package com.unirhy.e2e.support

import java.sql.Connection
import java.sql.DriverManager

data class EphemeralPostgresDatabase(
    val host: String,
    val port: Int,
    val user: String,
    val password: String,
    val adminDatabase: String,
    val databaseName: String,
) {
    val jdbcUrl: String = "jdbc:postgresql://$host:$port/$databaseName"
    private val adminJdbcUrl: String = "jdbc:postgresql://$host:$port/$adminDatabase"

    fun create() {
        withAdminConnection { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE DATABASE \"$databaseName\"")
            }
        }
    }

    fun dropQuietly() {
        runCatching {
            withAdminConnection { connection ->
                connection.createStatement().use { statement ->
                    statement.execute(
                        "SELECT pg_terminate_backend(pid) " +
                                "FROM pg_stat_activity " +
                                "WHERE datname = '$databaseName' AND pid <> pg_backend_pid()",
                    )
                    statement.execute("DROP DATABASE IF EXISTS \"$databaseName\"")
                }
            }
        }
    }

    private fun withAdminConnection(block: (Connection) -> Unit) {
        DriverManager.getConnection(adminJdbcUrl, user, password).use { connection ->
            connection.autoCommit = true
            block(connection)
        }
    }
}
