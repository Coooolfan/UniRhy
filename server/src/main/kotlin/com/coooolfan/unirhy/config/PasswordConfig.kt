package com.coooolfan.unirhy.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
class PasswordConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}

fun PasswordEncoder.encodePassword(rawPassword: String): String {
    return encode(rawPassword) ?: error("password encode failed")
}
