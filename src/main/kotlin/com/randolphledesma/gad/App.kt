package com.randolphledesma.gad

fun main(args: Array<String>) {
    DaggerApplicationComponent.builder()
            .build()
            .application()
            .start()
}