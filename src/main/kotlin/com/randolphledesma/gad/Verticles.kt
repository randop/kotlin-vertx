package com.randolphledesma.gad

import com.randolphledesma.gad.util.ConfigurationKeyList
import com.randolphledesma.gad.util.logger
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import javax.inject.Inject

class HttpVerticle @Inject constructor(private val mainController: MainController): AbstractVerticle() {
  private val LOG by logger()

  @Suppress("accept,complete,fail", "DEPRECATION")
  override fun start(startFuture: Future<Void>) {
    val router = mainController.create()
    val applicationContext = mainController.applicationContext
    val host = applicationContext.configuration.getString(ConfigurationKeyList.APP_HOST.name, "0.0.0.0")
    val port = applicationContext.configuration.getInteger(ConfigurationKeyList.APP_PORT.name, 8080)
    vertx
      .createHttpServer()
      .requestHandler { router.accept(it) }
      .listen(port, host) { http ->
        if (http.succeeded()) {
          startFuture.complete()
          LOG.info("HTTP server started on port $host:$port")
        } else {
          startFuture.fail(http.cause());
        }
      }
  }
}