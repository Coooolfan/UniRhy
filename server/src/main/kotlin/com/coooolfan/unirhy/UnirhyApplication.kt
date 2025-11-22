package com.coooolfan.unirhy

import org.babyfish.jimmer.client.EnableImplicitApi
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@EnableImplicitApi
@SpringBootApplication
class UnirhyApplication

fun main(args: Array<String>) {
    runApplication<UnirhyApplication>(*args)
}
