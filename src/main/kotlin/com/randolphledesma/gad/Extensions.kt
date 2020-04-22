package com.randolphledesma.gad

import io.vertx.core.logging.Logger
import io.vertx.core.logging.LoggerFactory

fun <T : Any> T.logger(): Lazy<Logger> {
    return lazy { LoggerFactory.getLogger(
        System.getProperty("vertx.logger-delegate-factory-class-name")?: "java.util.logging.LogManager"
    )}
}