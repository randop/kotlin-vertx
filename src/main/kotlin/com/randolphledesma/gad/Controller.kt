package com.randolphledesma.gad

import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.core.json.json

import javax.inject.Inject
import java.util.UUID

class MainController @Inject constructor(val applicationContext: ApplicationContext) {
    val LOG by logger()

    fun create(): Router = Router.router(applicationContext.vertx).apply {
        route().last().handler { context ->
            with(context.response()) {
                statusCode = HttpStatus.NotFound.code
                end()
            }
        }

        get("/").handler { context ->
            with(context.response()) {
                statusCode = HttpStatus.OK.code
                end( applicationContext.configuration.getString(ConfigurationKeyList.APP_GREETING.name) )
            }
        }

        get("/uuid").coroutineHandler { ctx -> getUUID(ctx) }

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
    }

    private suspend fun getUUID(ctx: RoutingContext) {
        ctx.response().endWithJson(json{ obj("uuid" to UUID.randomUUID())})

    }
}