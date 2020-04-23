package com.randolphledesma.gad

import io.vertx.core.Vertx
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.ext.web.Router
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject

abstract class Controller(val handlers: Router.() -> Unit) {
    abstract val applicationContext: ApplicationContext

    fun create(): Router {
        return applicationContext.router.apply {
            handlers()
        }
    }
}

class MainController @Inject constructor(override val applicationContext: ApplicationContext) : Controller({
    val LOG by logger()

    route().last().handler { context ->
        with(context.response()) {
            statusCode = HttpStatus.NotFound.code
            end()
        }
    }

    get("/").handler { context ->
        with(context.response()) {
            statusCode = HttpStatus.OK.code
            end("Hello!!!")
        }
    }

    get("/rfl").handler { context ->
        with(context.response()) {
            statusCode = HttpStatus.OK.code
            end( applicationContext.configuration.encode() )
        }
    }

    route().last().failureHandler { errorContext ->
        val e: Throwable? = errorContext.failure()
        if (e != null) {
            LOG.error(e.message, e)
        }
        with(errorContext.response()) {
            statusCode = HttpStatus.InternalServerError.code
            end()
        }
    }
})