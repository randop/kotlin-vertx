package com.example.starter

import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise

import io.vertx.kotlin.sqlclient.*
import io.vertx.mysqlclient.MySQLConnectOptions
import io.vertx.mysqlclient.MySQLPool
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.PoolOptions

class MainVerticle : AbstractVerticle() {

  override fun start(startPromise: Promise<Void>) {
    val router = Router.router(vertx)
    setupRoutes(router)

    vertx
      .createHttpServer()
      .requestHandler { req ->
        req.response()
          .putHeader("content-type", "text/plain")
          .end("Hello from Vert.x!")
      }
      .listen(8888) { http ->
        if (http.succeeded()) {
          startPromise.complete()
          println("HTTP server started on port 8888")

          val connectOptions = MySQLConnectOptions()
              .setPort(3306)
              .setHost("192.168.186.192")
              .setDatabase("gateway")
              .setUser("root")
              .setPassword("mysql")
              .setCharset("utf8mb4")
              .setCollation("utf8mb4_unicode_ci")

          val client = MySQLPool.pool(vertx, connectOptions, PoolOptions().setMaxSize(5))
          client.query("SELECT CURRENT_TIMESTAMP()", { ar ->
            if (ar.failed()) {
              System.err.println("Cannot connect")                
              ar.cause().printStackTrace()         
              throw java.lang.RuntimeException(ar.cause())
            }
            if (ar.succeeded()) {
              println("Database Connected: ${ar.result().first()}")                
            } else {
              println("Database Connection Failure: ${ar.cause()}")
            }
          })

        } else {
          startPromise.fail(http.cause());
        }
      }
  }

  private fun setupRoutes(router: Router) {
    router.route("/").handler(indexRouteHandler())    
  }

  private fun indexRouteHandler() = { ctx: RoutingContext ->     
    with(ctx.response()) {
      putHeader("content-type", "text/plain")
      end("Hello World!")
    }    
  }
  
}
