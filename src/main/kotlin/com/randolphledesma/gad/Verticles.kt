package com.randolphledesma.gad

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.ext.web.Router
import io.vertx.config.ConfigRetriever

import javax.inject.Inject

class HttpVerticle @Inject constructor(private val mainController: MainController): AbstractVerticle() {
  private val LOG by logger()

  override fun start(startFuture: Future<Void>) {   
    val router = mainController.create()
    vertx
      .createHttpServer()
      .requestHandler { router.accept(it) }
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