package com.randolphledesma.gad

import io.vertx.core.http.HttpHeaders.CONTENT_TYPE
import io.vertx.core.http.HttpMethod.*
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.CorsHandler
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.core.json.json
import java.text.SimpleDateFormat
import java.util.*

import javax.inject.Inject

class MainController @Inject constructor(val applicationContext: ApplicationContext) {
    private val redis = applicationContext.redis
    private val LOG by logger()

    fun create(): Router = Router.router(applicationContext.vertx).apply {
        val cors = CorsHandler.create("*")
            .allowedHeader(CONTENT_TYPE.toString())
            .allowedMethods(setOf(GET, POST, PUT, PATCH, DELETE, OPTIONS))
        route().handler(cors)

        route().last().handler { context ->
            with(context.response()) {
                statusCode = HttpStatus.NotFound.code
                end()
            }
        }

        get("/").handler { context ->
            val now = SimpleDateFormat("MM-dd", Locale.getDefault()).format( Date() )
            if (now == "04-01") {
                with(context.response()) {
                    statusCode = HttpStatus.IAmATeapot.code
                    end("April Fools: I'm a teapot")
                }
            } else {
                with(context.response()) {
                    statusCode = HttpStatus.OK.code
                    end(applicationContext.configuration.getString(ConfigurationKeyList.APP_GREETING.name))
                }
            }
        }

        get("/uuid").coroutineHandler { ctx -> getUUID(ctx) }

        route("/health").coroutineHandler { ctx -> health(ctx) }

        route("/locking").coroutineHandler { ctx -> locking(ctx) }

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
        var uuid = redis.get("uuid")
        if (uuid.isNullOrBlank()) {
            uuid = UUID.randomUUID().toString()
            redis.set("uuid", uuid)
        }
        ctx.response().endWithJson(json {
            obj("uuid" to uuid )
        })
    }

    private suspend fun health(ctx: RoutingContext) {
        val now = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format( Date() )
        ctx.response().end("$now")
    }

    private suspend fun locking(ctx: RoutingContext) {
        try {
            val lockID = redis.acquireLockWithTimeout("test", 1000, 15000)
            println(lockID)

            val lockID2 = redis.acquireLockWithTimeout("test", 1000, 15000)
            println(lockID2)
        }
        catch(e: RuntimeException) {
            println(e)
        }
        ctx.response().end()
    }
}