package com.randolphledesma.gad

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
import io.vertx.ext.sql.SQLConnection
import io.vertx.ext.web.Route
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.kotlin.ext.sql.closeAwait
import io.vertx.kotlin.ext.sql.commitAwait
import io.vertx.kotlin.ext.sql.setAutoCommitAwait
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import redis.clients.jedis.Jedis
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.time.*

fun <T : Any> T.logger(): Lazy<Logger> {
    return lazy { LoggerFactory.getLogger(
        System.getProperty("vertx.logger-delegate-factory-class-name")?: "java.util.logging.LogManager"
    )}
}

fun ByteArray.gzip(): ByteArray {
    val bos = ByteArrayOutputStream()
    val gzipOS = GZIPOutputStream(bos)
    gzipOS.write(this)
    gzipOS.close()
    return bos.toByteArray()
}

fun ByteArray.ungzip(): String =
    GZIPInputStream(this.inputStream()).bufferedReader(UTF_8).use { it.readText() }

fun String.parseJson(): Future<JsonObject> {
    val promise: Promise<JsonObject> = Promise.promise()
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

fun String.toZuluDateTime() = ZonedDateTime.parse(this, DateTimeFormatter.ISO_DATE_TIME)

fun ZonedDateTime.toPhilippines() = this.withZoneSameInstant(ZoneId.of("Asia/Manila"))

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

suspend fun Jedis.acquireLockWithTimeout(lockName: String, acquireTimeout: Long, lockTimeout: Long): String? {
    val identifier = UUID.randomUUID().toString()
    val lockKey = "lock:$lockName"
    val lockExpire = lockTimeout.toInt() / 1000

    val end = System.currentTimeMillis() + acquireTimeout
    while (System.currentTimeMillis() < end) {
        if (this.setnx(lockKey, identifier) == 1L){
            this.expire(lockKey, lockExpire)
            return identifier
        }
        if (this.ttl(lockKey) == -1L) {
            this.expire(lockKey, lockExpire)
        }

        try {
            delay(1)
        } catch(error: Throwable){
            //TODO("")
        }
    }

    // null indicates that the lock was not acquired
    return null
}

fun Jedis.releaseLock(lockName: String, identifier: String): Boolean {
    val lockKey = "lock:$lockName"

    while (true){
        this.watch(lockKey)
        if (identifier == this.get(lockKey)){
            val trans = this.multi()
            trans.del(lockKey)
            trans.exec() ?: continue
            return true
        }

        this.unwatch()
        break
    }

    return false
}

suspend inline fun SQLConnection.inTransaction(func: SQLConnection.() -> Unit) {
    setAutoCommitAwait(false)
    func()
    commitAwait()
    closeAwait()
}