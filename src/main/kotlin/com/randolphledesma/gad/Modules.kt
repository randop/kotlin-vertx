package com.randolphledesma.gad

import dagger.Module
import dagger.Provides
import dagger.Component
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey

import kotlinx.coroutines.*
import io.vertx.kotlin.coroutines.*

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

import io.vertx.ext.jdbc.JDBCClient
import io.vertx.ext.sql.SQLConnection
import io.vertx.kotlin.ext.sql.executeAwait
import io.vertx.kotlin.ext.sql.queryAwait
import io.vertx.kotlin.ext.sql.getConnectionAwait

import io.vertx.kotlin.core.json.array
import io.vertx.kotlin.core.json.get
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj

import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * The application itself.
 */
class Application @Inject constructor(private val vertx: Vertx, private val dbClient: JDBCClient) {
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
        SqlModule::class
    ])
interface ApplicationComponent {
    fun application(): Application    
    fun database(): JDBCClient
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
object SqlModule {
    private val LOG by logger()

    @Provides
    @Singleton
    fun provideJdbcClient(vertx: Vertx): JDBCClient {
        lateinit var client: JDBCClient
        val sql = "SELECT CURRENT_TIMESTAMP(), @@character_set_database, @@collation_database"        
        runBlocking(vertx.dispatcher()) {
            try {
                var config = json {
                    obj(
                        "url" to "jdbc:mysql://192.168.0.197:3306/gateway?useUnicode=true&character_set_server=utf8mb4&collation_database=utf8mb4_unicode_ci",
                        "driver_class" to "com.mysql.cj.jdbc.Driver",
                        "max_pool_size" to 30,
                        "user" to "gateway",
                        "password" to "password"                                            
                    )
                }
                client = JDBCClient.createShared(vertx, config)
                withTimeout<Unit>(30000) {
                    val connection = client.getConnectionAwait()
                    val res = connection.queryAwait(sql)
                    val jsonResult = res.rows.get(0).toString().parseJson().await()
                    val ts = jsonResult!!.getString("CURRENT_TIMESTAMP()")
                    LOG.info("${res.rows.get(0)}")
                    LOG.info("Database Startup Connection Succeeded: $ts")
                    connection.close()
                }                
            } catch(error: Throwable) {
                LOG.error(error.message, error)
                System.exit(-1)
            }
        }        
        return client
    }
}