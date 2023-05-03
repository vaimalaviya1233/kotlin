/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

import objclib.*

import platform.darwin.NSObject
import platform.Foundation.NSSelectorFromString
import kotlin.test.*
import kotlinx.cinterop.*

class Incrementor : NSObject() {
    private var _counter = 0

    val counter by this::_counter

    @ObjCAction
    fun increment() {
        println("I'm here to make sure this function generates a frame. Here's an object: ${Any()}")
        _counter++
    }
}

@Test
fun testObjCAction() {
    val incrementor = Incrementor()
    assertEquals(0, incrementor.counter)
    execute(incrementor, NSSelectorFromString(Incrementor::increment.name))
    assertEquals(1, incrementor.counter)
}

class Proxy(val id: Int) : NSObject()

class ProxyHolder : NSObject() {
    @ObjCOutlet
    var proxy: Proxy? = null
}

@Test
fun testObjCOutlet() {
    val proxyHolder = ProxyHolder()
    assertNull(proxyHolder.proxy)
    setProxy(proxyHolder, Proxy(42))
    assertEquals(42, proxyHolder.proxy?.id)
}
