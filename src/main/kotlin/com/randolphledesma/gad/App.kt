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

/**
 * The application itself.
 */
class Application @Inject constructor(private val vertx: Vertx, private val dbClient: MySQLPool) {
    private val LOG by logger()

    fun start() {
        runBlocking(vertx.dispatcher()) {            
            startFruitVerticles()
            startStorePrintVerticle()
            try {
                startHttpVerticle()
            } catch(error: Throwable) {                
                LOG.error("HttpServer Component Failed")
                System.exit(-1)
            }

            sendAddFruitsEvents()
            sendPrintEvents()            
        }
    }

    private suspend fun startHttpVerticle() {
        awaitResult<String> { vertx.deployVerticle("dagger:${HttpVerticle::class.java.name}", it) }
        LOG.info("Main verticle deployed successful")
    }

    /**
     * Deployment of the verticle that print the fruit in the store
     */
    private suspend fun startStorePrintVerticle() {
        awaitResult<String> { vertx.deployVerticle("dagger:${StorePrintVerticle::class.java.name}", it) }
        LOG.info("Store print verticle deployed successful")
    }

    /**
     * Deployment of the fruit verticle. A verticle per [Fruit]
     */
    private suspend fun startFruitVerticles() {
        awaitResult<String> { vertx.deployVerticle("dagger:${FruitVerticle::class.java.name}", DeploymentOptions().setInstances(Fruit.values().size), it) }
        LOG.info("Fruit verticles deployed successful")
    }

    /**
     * Sends a print event in a period of 4 seconds
     */
    private fun sendPrintEvents() {
        LOG.info("Start sending print fruits in store event")
        vertx.eventBus().send(StorePrintVerticle.ADDRESS, null)
    }

    /**
     * Sends a add fruits event in a period of 3 seconds
     */
    private fun sendAddFruitsEvents() {
        LOG.info("Start publishing add fruits event")
        val random = Random()
        vertx.eventBus().send(FruitVerticle.buildAddress(Fruit.APPLE), random.nextInt(5))
        vertx.eventBus().send(FruitVerticle.buildAddress(Fruit.RASPBERRY), random.nextInt(5))
        vertx.eventBus().send(FruitVerticle.buildAddress(Fruit.BANANA), random.nextInt(5))
    }
}

/**
 * Component which configures all needed Dagger modules to run the application.
 */
@Singleton
@Component(modules = [
        VertxModule::class, 
        DaggerVerticleFactoryModule::class, 
        FruitStoreModule::class,
        FruitVerticleModule::class, 
        StorePrintVerticleModule::class, 
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

enum class Fruit {
    APPLE, RASPBERRY, BANANA
}


/**
 * Interface of the store
 */
interface FruitStore {
    /**
     * Get the count of the currently stored pieces
     */
    fun getFruitCount(fruit: Fruit): Int

    /**
     * Applies changes to the store
     */
    fun storeFruits(fruit: Fruit, count: Int)
}


/**
 * Dagger module to provide the standard [FruitStore].
 */
@Module
object FruitStoreModule {

    /**
     * Provides the singleton [FruitStore]
     */
    @Provides
    @Singleton
    fun provideFruitStore(): FruitStore = InMemoryFruitStore()
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

@Module
object HttpVerticleModule {
    @Provides    
    @Singleton
    @IntoMap
    @StringKey("com.randolphledesma.gad.HttpVerticle")
    fun provideHttpVerticle(): Verticle = HttpVerticle()
}

class FruitVerticle(private val store: FruitStore, private val fruit: Fruit) : CoroutineVerticle() {

    private val LOG by logger()

    companion object {
        fun buildAddress(fruit: Fruit): String = "add-${fruit.name}.cmd"
    }

    override suspend fun start() {
        vertx.eventBus().consumer<Int>(buildAddress(fruit), this::onAddFruitEvent)
        LOG.info("${fruit.name} verticle started")
    }

    private fun onAddFruitEvent(msg: Message<Int>) {
        launch {
            val count = msg.body()
            for (i in 1..count) {
                store.storeFruits(fruit, count)
            }
            LOG.info("$count ${fruit.name} added to store")
        }
    }
}

@Module
object FruitVerticleModule {

    private val fruitIter = Fruit.values().iterator()

    @Provides
    @IntoMap
    @StringKey("com.randolphledesma.gad.FruitVerticle")
    fun provideFruitVerticle(store: FruitStore): Verticle = FruitVerticle(store, fruitIter.next())
}

open class InMemoryFruitStore : FruitStore {
    private val LOG by logger()

    private val fruits: MutableMap<Fruit, Int> = ConcurrentHashMap()

    init {
        LOG.info("In-memory fruit store instantiated")
    }

    override fun storeFruits(fruit: Fruit, count: Int) {
        fruits[fruit] = fruits.getOrDefault(fruit, 0).plus(count)
    }

    override fun getFruitCount(fruit: Fruit): Int = fruits.getOrDefault(fruit, 0)
}

class StorePrintVerticle(private val store: FruitStore) : CoroutineVerticle() {

    companion object {
        const val ADDRESS = "print-store.cmd"
    }

    private val LOG by logger()

    override suspend fun start() {
        vertx.eventBus().consumer<Void>(ADDRESS, this::onPrintEvent)
        LOG.info("Store print verticle created")
    }

    private fun onPrintEvent(msg: Message<Void>) {
        launch {
            LOG.info("There are ${store.getFruitCount(Fruit.APPLE)} ${Fruit.APPLE.name}'s in the store")
            LOG.info("There are ${store.getFruitCount(Fruit.RASPBERRY)} ${Fruit.RASPBERRY.name}'s in the store")
            LOG.info("There are ${store.getFruitCount(Fruit.BANANA)} ${Fruit.BANANA.name}'s in the store")
        }
    }
}

@Module
object StorePrintVerticleModule {

    @Provides
    @IntoMap
    @StringKey("com.randolphledesma.gad.StorePrintVerticle")
    fun provideStorePrintVerticle(store: FruitStore): Verticle = StorePrintVerticle(store)
}

@Module
object VertxModule {

    @Provides    
    @Singleton
    fun provideVertx(verticleFactory: VerticleFactory): Vertx {        
        val vertx = Vertx.vertx()
        println("provideVertx: ${vertx}")
        vertx.registerVerticleFactory(verticleFactory)
        return vertx
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
                    throw RuntimeException(ar.cause().message)
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