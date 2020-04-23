package com.randolphledesma.gad

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

inline suspend fun <T: String> T.parseJson(): Future<JsonObject?> {
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