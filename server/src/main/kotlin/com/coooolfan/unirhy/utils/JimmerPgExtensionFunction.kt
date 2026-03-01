package com.coooolfan.unirhy.utils

import org.babyfish.jimmer.sql.kt.ast.expression.KNonNullExpression
import org.babyfish.jimmer.sql.kt.ast.expression.KNonNullPropExpression
import org.babyfish.jimmer.sql.kt.ast.expression.sql

fun arrayToString(expression: KNonNullPropExpression<List<String>>): KNonNullExpression<String> {
    return sql(String::class, "array_to_string(%e, ',')") { expression(expression) }
}