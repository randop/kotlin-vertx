package com.randolphledesma.gad

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.ext.web.Router
import io.vertx.config.ConfigRetriever

import javax.inject.Inject

class HttpVerticle @Inject constructor(val mainController: MainController): AbstractVerticle() {
  private val LOG by logger()

  override fun start(startFuture: Future<Void>) {   
    val retriever = ConfigRetriever.create(vertx)
    retriever.getConfig({ ar ->
      if (ar.failed()) {
        LOG.error("failed")
      } else {
        var config = ar.result()        
      }
    })


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

abstract class Controller(val handlers: Router.() -> Unit) {
    abstract val router: Router
    fun create(): Router {
        return router.apply {
            handlers()
        }
    }
}

class MainController @Inject constructor(override val router: Router) : Controller({
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