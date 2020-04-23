package com.randolphledesma.gad

import io.vertx.core.Vertx
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.ext.web.Router
import javax.inject.Inject

abstract class Controller(val handlers: Router.() -> Unit) {
    abstract val vertx: Vertx
    abstract val dbClient: JDBCClient
    abstract val router: Router

    fun create(): Router {
        return router.apply {
            handlers()
        }
    }
}

class MainController @Inject constructor(override val vertx: Vertx, override val dbClient: JDBCClient, override val router: Router) : Controller({
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