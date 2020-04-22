package com.randolphledesma.gad

import javax.inject.Inject

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