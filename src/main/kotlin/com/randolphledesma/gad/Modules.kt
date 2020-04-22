package com.randolphledesma.gad

import dagger.Module
import dagger.Provides
import dagger.Component
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey

import kotlinx.coroutines.*

import javax.inject.Inject
import javax.inject.Singleton
import javax.inject.Provider

import io.vertx.core.Verticle
import io.vertx.core.spi.VerticleFactory
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.kotlin.coroutines.awaitResult
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.core.eventbus.Message
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.Future
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.ext.web.client.predicate.ResponsePredicate

import io.vertx.kotlin.sqlclient.*
import io.vertx.mysqlclient.MySQLConnectOptions
import io.vertx.mysqlclient.MySQLPool
import io.vertx.sqlclient.Pool
import io.vertx.sqlclient.PoolOptions

import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * The application itself.
 */
class Application @Inject constructor(private val vertx: Vertx, private val dbClient: MySQLPool) {
    private val LOG by logger()

    fun start() {
        runBlocking(vertx.dispatcher()) {                        
            try {
                startHttpVerticle()
            } catch(error: Throwable) {                
                LOG.error("HttpServer Component Failed", error)
                System.exit(-1)
            }
        }
    }

    private suspend fun startHttpVerticle() {
        awaitResult<String> { vertx.deployVerticle("dagger:${HttpVerticle::class.java.name}", it) }
        LOG.info("Main verticle deployed successful")
    }
}

/**
 * Component which configures all needed Dagger modules to run the application.
 */
@Singleton
@Component(modules = [
        VertxModule::class, 
        DaggerVerticleFactoryModule::class, 
        HttpVerticleModule::class,
        VertxWebClientModule::class,
        MysqlModule::class
    ])
interface ApplicationComponent {
    fun application(): Application    
    fun database(): MySQLPool
}

class DaggerVerticleFactory(private val verticleMap: Map<String, Provider<Verticle>>) : VerticleFactory {

    private val LOG by logger()

    override fun createVerticle(verticleName: String, classLoader: ClassLoader): Verticle {
        val verticle = verticleMap.getOrElse(sanitizeVerticleClassName(verticleName), { throw IllegalStateException("No provider for verticle type $verticleName found") }).get()
        LOG.info("Verticle for type: $verticleName created")
        return verticle
    }

    private fun sanitizeVerticleClassName(verticleName: String): String = verticleName.substring(verticleName.lastIndexOf(":") + 1)

    override fun prefix(): String = "dagger"
}

@Module
object DaggerVerticleFactoryModule {
    @JvmSuppressWildcards
    @Provides
    @Singleton
    fun provideVerticleFactory(verticleMap: Map<String, Provider<Verticle>>): VerticleFactory = DaggerVerticleFactory(verticleMap)
}

@Module
object HttpVerticleModule {
    @Provides    
    @Singleton
    @IntoMap
    @StringKey("com.randolphledesma.gad.HttpVerticle")
    fun provideHttpVerticle(router: Router): Verticle = HttpVerticle(MainController(router))
}

@Module
object VertxModule {

    @Provides    
    @Singleton
    fun provideVertx(verticleFactory: VerticleFactory): Vertx {        
        val vertx = Vertx.vertx()        
        vertx.registerVerticleFactory(verticleFactory)
        return vertx
    }

    @Provides
    @Singleton
    fun provideRouter(vertx: Vertx): Router {
        return Router.router(vertx)
    }
}

@Module
object VertxWebClientModule {

    @Provides    
    @Singleton
    fun provideVertxWebClient(vertx: Vertx): WebClient {        
        val options = WebClientOptions().setTcpKeepAlive(true).setUserAgent("Tindango/1.0")
        val client = WebClient.create(vertx, options)
        println("provideVertxWebClient: ${client}")
        return client
    }
}

@Module
object MysqlModule {
    private val LOG by logger()

    @Provides
    @Singleton
    fun provideMysqlPool(vertx: Vertx): MySQLPool {
        lateinit var client: MySQLPool        
        try {
            val connectOptions = MySQLConnectOptions()
                    .setPort(3306)
                    .setHost("192.168.186.192")
                    .setDatabase("gateway")
                    .setUser("root")
                    .setPassword("mysql")
                    .setCharset("utf8mb4")
                    .setCollation("utf8mb4_unicode_ci") 
            client = MySQLPool.pool(vertx, connectOptions, PoolOptions().setMaxSize(5))
            client.query("SELECT CURRENT_TIMESTAMP()").execute({ ar ->
                if (ar.succeeded()) {                    
                    LOG.info("Database Connection Succeeded: ${ar.result().first().getTemporal(0)}")
                } else {                                        
                    LOG.error(ar.cause().message)
                    System.exit(-1)                    
                }
            })
            return client
        } catch(error: Throwable) {         
            LOG.error("Database Connection Failed: ${error.message}")   
            System.exit(-1)
        }
        return client
    }
}