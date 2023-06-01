/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
// TODO:
//   replace usages of these functions with copyToArrayImpl in compiler
//   advancing bootstrap version
//   delete this file

@file:JvmName("CollectionToArray")

package kotlin.jvm.internal

@JvmName("toArray")
fun collectionToArray(collection: Collection<*>): Array<Any?> = copyToArrayImpl(collection)

// Note: Array<Any?> here can have any reference array JVM type at run time
@JvmName("toArray")
fun collectionToArray(collection: Collection<*>, a: Array<Any?>?): Array<Any?> = copyToArrayImpl(collection, a!!)