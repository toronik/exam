package io.github.adven27.concordion.extensions.exam.core.json

import com.fasterxml.jackson.core.JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN
import com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER
import com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_JAVA_COMMENTS
import com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_SINGLE_QUOTES
import com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES
import com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_YAML_COMMENTS
import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS
import com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import net.javacrumbs.jsonunit.providers.Jackson2ObjectMapperProvider

open class DefaultObjectMapperProvider : Jackson2ObjectMapperProvider {
    private val mapper: ObjectMapper = JsonMapper.builder()
        .enable(USE_BIG_DECIMAL_FOR_FLOATS, FAIL_ON_TRAILING_TOKENS)
        .enable(WRITE_BIGDECIMAL_AS_PLAIN)
        .build()

    private val lenientMapper: ObjectMapper = JsonMapper.builder()
        .enable(USE_BIG_DECIMAL_FOR_FLOATS, FAIL_ON_TRAILING_TOKENS)
        .enable(WRITE_BIGDECIMAL_AS_PLAIN)
        .enable(
            ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER,
            ALLOW_UNQUOTED_FIELD_NAMES,
            ALLOW_JAVA_COMMENTS,
            ALLOW_YAML_COMMENTS,
            ALLOW_SINGLE_QUOTES
        )
        .build()

    override fun getObjectMapper(lenient: Boolean) = if (lenient) lenientMapper else mapper
}
