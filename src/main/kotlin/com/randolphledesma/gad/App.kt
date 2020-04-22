package com.randolphledesma.gad

import kotlinx.coroutines.*

import javax.inject.Inject

import io.vertx.core.Verticle
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.awaitResult
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.Future

import java.util.*

fun main(args: Array<String>) {
    DaggerApplicationComponent.builder()
            .fruitStoreModule(FruitStoreModule)
            .vertxModule(VertxModule)
            .daggerVerticleFactoryModule(DaggerVerticleFactoryModule)
            .fruitVerticleModule(FruitVerticleModule)
            .storePrintVerticleModule(StorePrintVerticleModule)
            .mysqlModule(MysqlModule)
            .httpVerticleModule(HttpVerticleModule)
            .vertxWebClientModule(VertxWebClientModule)
            .build()
            .application()
            .start()
}

class HttpVerticle : AbstractVerticle() {
  override fun start(startFuture: Future<Void>) {
    vertx
      .createHttpServer()
      .requestHandler { req ->
        req.response()
          .putHeader("content-type", "text/plain")
          .end("Hello from Vert.x!")        
      }
      .listen(8888) { http ->
        if (http.succeeded()) {
          startFuture.complete()
          println("HTTP server started on port 8888")
        } else {
          startFuture.fail(http.cause());
        }
      }
  }
}