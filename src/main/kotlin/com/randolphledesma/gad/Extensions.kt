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
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.Json
import io.vertx.ext.web.Route
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

fun <T : Any> T.logger(): Lazy<Logger> {
    return lazy { LoggerFactory.getLogger(
        System.getProperty("vertx.logger-delegate-factory-class-name")?: "java.util.logging.LogManager"
    )}
}

fun String.parseJson(): Future<JsonObject?> {
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

/**
 * An extension method for simplifying coroutines usage with Vert.x Web routers
 */
fun Route.coroutineHandler(fn: suspend (RoutingContext) -> Unit) {
    handler { ctx ->
        GlobalScope.launch(ctx.vertx().dispatcher()) {
            try {
                fn(ctx)
            } catch (e: Exception) {
                ctx.fail(e)
            }
        }
    }
}

/**
 * Extension to the HTTP response to output JSON objects.
 */
fun HttpServerResponse.endWithJson(obj: Any) {
    this.putHeader("Content-Type", "application/json; charset=utf-8").end(Json.encode(obj))
}