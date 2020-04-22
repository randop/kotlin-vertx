package com.randolphledesma.gad

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import javax.inject.Inject

class HttpVerticle: AbstractVerticle() {
  private val LOG by logger()

  override fun start(startFuture: Future<Void>) {    
    vertx
      .createHttpServer()
      .requestHandler { req ->
        req.response()
          .putHeader("content-type", "text/plain")
          .end("Hello World")
      }
      .listen(8080) { http ->
        if (http.succeeded()) {
          startFuture.complete()
          LOG.info("HTTP server started on port 8080")
        } else {
          startFuture.fail(http.cause());
        }
      }
  }
}