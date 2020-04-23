package com.randolphledesma.gad

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

import io.vertx.core.logging.Logger
import io.vertx.core.logging.LoggerFactory

import io.vertx.core.Future
import io.vertx.core.json.JsonObject
import io.vertx.core.parsetools.JsonParser
import io.vertx.core.buffer.Buffer
import io.vertx.core.Promise

fun <T : Any> T.logger(): Lazy<Logger> {
    return lazy { LoggerFactory.getLogger(
        System.getProperty("vertx.logger-delegate-factory-class-name")?: "java.util.logging.LogManager"
    )}
}

fun <T: String> T.parseJson(): Future<JsonObject?> {
    val promise: Promise<JsonObject?> = Promise.promise()
    val parser = JsonParser.newParser()
    val buffer = Buffer.buffer()
    buffer.appendString(this)
    parser.objectValueMode()
    parser.handler { event ->
        promise.complete(event.objectValue())
    }
    parser.exceptionHandler { er ->
        promise.fail(er.cause)
    }
    parser.write(buffer)
    parser.end()
    return promise.future()
}

fun Int.toYear(): Year {
    return Year.of(this)
}

fun LocalDateTime?.isoDateFormat(): String? {
    return this?.format(DateTimeFormatter.ISO_DATE)
}

fun LocalDateTime?.isoTimeFormat(): String? {
    return this?.format(DateTimeFormatter.ISO_TIME)
}

fun LocalDateTime?.isoDateTimeFormat(): String? {
    return this?.format(DateTimeFormatter.ISO_DATE_TIME)
}

fun Long.toLocalDateTime(): LocalDateTime {
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())
}

fun LocalDateTime.toMilliSeconds(): Long {
    return this.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}

@Throws(DateTimeParseException::class)
fun String.toLocalDate(pattern: String): LocalDate {
    return LocalDate.parse(this, DateTimeFormatter.ofPattern(pattern))
}