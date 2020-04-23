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
    val port = mainController.applicationContext.configuration.getInteger(ConfigurationKeyList.APP_PORT.name, 8080)
    vertx
      .createHttpServer()
      .requestHandler { router.accept(it) }
      .listen(port) { http ->
        if (http.succeeded()) {
          startFuture.complete()
          LOG.info("HTTP server started on port $port")
        } else {
          startFuture.fail(http.cause());
        }
      }
  }
}