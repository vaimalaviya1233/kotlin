/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.wasm.internal

import kotlin.wasm.internal.reftypes.anyref
import kotlin.wasm.unsafe.withScopedMemoryAllocator
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.internal.reftypes.stringref

internal external interface ExternalInterfaceType

internal class JsExternalBox @WasmPrimitiveConstructor constructor(val ref: ExternalInterfaceType) {
    override fun toString(): String =
        externrefToString(ref)

    override fun equals(other: Any?): Boolean =
        if (other is JsExternalBox) {
            externrefEquals(ref, other.ref)
        } else {
            false
        }

    override fun hashCode(): Int {
        var hashCode = _hashCode
        if (hashCode != 0) return hashCode
        hashCode = externrefHashCode(ref)
        _hashCode = hashCode
        return hashCode
    }
}

//language=js
@JsFun("""
(() => {
const dataView = new DataView(new ArrayBuffer(8));
function numberHashCode(obj) {
    if ((obj | 0) === obj) {
        return obj | 0;
    } else {
        dataView.setFloat64(0, obj, true);
        return (dataView.getInt32(0, true) * 31 | 0) + dataView.getInt32(4, true) | 0;
    }
}

const hashCodes = new WeakMap();
function getObjectHashCode(obj) {
    const res = hashCodes.get(obj);
    if (res === undefined) {
        const POW_2_32 = 4294967296;
        const hash = (Math.random() * POW_2_32) | 0;
        hashCodes.set(obj, hash);
        return hash;
    }
    return res;
}

function getStringHashCode(str) {
    var hash = 0;
    for (var i = 0; i < str.length; i++) {
        var code  = str.charCodeAt(i);
        hash  = (hash * 31 + code) | 0;
    }
    return hash;
}

return (obj) => {
    if (obj == null) {
        return 0;
    }
    switch (typeof obj) {
        case "object":
        case "function":
            return getObjectHashCode(obj);
        case "number":
            return numberHashCode(obj);
        case "boolean":
            return obj;
        default:
            return getStringHashCode(String(obj)); 
    }
}
})()"""
)
private external fun externrefHashCode(ref: ExternalInterfaceType): Int

@JsFun("ref => String(ref)")
private external fun externrefToString(ref: ExternalInterfaceType): String

@JsFun("ref => Number(ref)")
private external fun externrefToInt(ref: ExternalInterfaceType): Int

@JsFun("ref => Number(ref)")
private external fun externrefToLong(ref: ExternalInterfaceType): Long

@JsFun("ref => Boolean(ref)")
private external fun externrefToBoolean(ref: ExternalInterfaceType): Boolean

@JsFun("ref => Number(ref)")
private external fun externrefToFloat(ref: ExternalInterfaceType): Float

@JsFun("ref => Number(ref)")
private external fun externrefToDouble(ref: ExternalInterfaceType): Double

@JsFun("x => x")
private external fun intToExternref(x: Int): ExternalInterfaceType

@JsFun("x => x")
private external fun longToExternref(x: Long): ExternalInterfaceType

@JsFun("x => x")
private external fun booleanToExternref(x: Boolean): ExternalInterfaceType

@JsFun("x => x")
private external fun floatToExternref(x: Float): ExternalInterfaceType

@JsFun("x => x")
private external fun doubleToExternref(x: Double): ExternalInterfaceType

@JsFun("(lhs, rhs) => lhs === rhs")
private external fun externrefEquals(lhs: ExternalInterfaceType, rhs: ExternalInterfaceType): Boolean

private external fun tryGetOrSetExternrefBox(ref: ExternalInterfaceType, ifNotCached: JsExternalBox): JsExternalBox?

@WasmNoOpCast
@Suppress("unused")
private fun Any?.asWasmAnyref(): anyref =
    implementedAsIntrinsic

@WasmOp(WasmOp.EXTERN_INTERNALIZE)
private fun ExternalInterfaceType.externAsWasmAnyref(): anyref =
    implementedAsIntrinsic

@WasmOp(WasmOp.EXTERN_EXTERNALIZE)
private fun Any.asWasmExternRef(): ExternalInterfaceType =
    implementedAsIntrinsic

@JsFun("(ref) => ref == null")
internal external fun isNullish(ref: ExternalInterfaceType?): Boolean

internal fun externRefToAny(ref: ExternalInterfaceType): Any? {
    // TODO rewrite it so to get something like:
    // block {
    //     refAsAnyref
    //     br_on_cast_fail null 0 $kotlin.Any
    //     return
    // }
    // If ref is an instance of kotlin class -- return it casted to Any
    val refAsAnyref = ref.externAsWasmAnyref()
    if (wasm_ref_is_data_deprecated(refAsAnyref)) {
        val refAsDataRef = wasm_ref_as_data_deprecated(refAsAnyref)
        if (wasm_ref_test_deprecated<Any>(refAsDataRef)) {
            return wasm_ref_cast_deprecated<Any>(refAsDataRef)
        }
    }

    // If we have Null in notNullRef -- return null
    // If we already have a box -- return it,
    // otherwise -- remember new box and return it.
    return tryGetOrSetExternrefBox(ref, JsExternalBox(ref))
}


internal fun anyToExternRef(x: Any): ExternalInterfaceType {
    return if (x is JsExternalBox)
        x.ref
    else
        x.asWasmExternRef()
}

internal fun jsCheckIsNullOrUndefinedAdapter(x: ExternalInterfaceType?): ExternalInterfaceType? =
    x.takeIf { !isNullish(it) }

private const val STRING_INTEROP_MEM_BUFFER_SIZE = 65_536 // 1 page 4KiB

@JsFun("() => true")
private external fun getJsTrue(): ExternalInterfaceType

@JsFun("() => false")
private external fun getJsFalse(): ExternalInterfaceType

private val jsTrue by lazy(::getJsTrue)
private val jsFalse by lazy(::getJsFalse)

internal fun numberToDoubleAdapter(x: Number): Double =
    x.toDouble()

internal fun kotlinToJsAnyAdapter(x: Any?): ExternalInterfaceType? =
    if (x == null) null else anyToExternRef(x)

internal fun jsToKotlinAnyAdapter(x: ExternalInterfaceType?): Any? =
    if (x == null) null else externRefToAny(x)

internal fun jsToKotlinByteAdapter(x: Int): Byte = x.toByte()
internal fun jsToKotlinShortAdapter(x: Int): Short = x.toShort()
internal fun jsToKotlinCharAdapter(x: Int): Char = x.toChar()

internal fun externRefToKotlinIntAdapter(x: ExternalInterfaceType): Int =
    externrefToInt(x)

internal fun externRefToKotlinBooleanAdapter(x: ExternalInterfaceType): Boolean =
    externrefToBoolean(x)

internal fun externRefToKotlinLongAdapter(x: ExternalInterfaceType): Long =
    externrefToLong(x)

internal fun externRefToKotlinFloatAdapter(x: ExternalInterfaceType): Float =
    externrefToFloat(x)

internal fun externRefToKotlinDoubleAdapter(x: ExternalInterfaceType): Double =
    externrefToDouble(x)


internal fun kotlinIntToExternRefAdapter(x: Int): ExternalInterfaceType =
    intToExternref(x)

internal fun kotlinBooleanToExternRefAdapter(x: Boolean): ExternalInterfaceType =
    if (x) jsTrue else jsFalse

internal fun kotlinLongToExternRefAdapter(x: Long): ExternalInterfaceType =
    longToExternref(x)

internal fun kotlinFloatToExternRefAdapter(x: Float): ExternalInterfaceType =
    floatToExternref(x)

internal fun kotlinDoubleToExternRefAdapter(x: Double): ExternalInterfaceType =
    doubleToExternref(x)

internal fun kotlinByteToExternRefAdapter(x: Byte): ExternalInterfaceType =
    intToExternref(x.toInt())

internal fun kotlinShortToExternRefAdapter(x: Short): ExternalInterfaceType =
    intToExternref(x.toInt())

internal fun kotlinCharToExternRefAdapter(x: Char): ExternalInterfaceType =
    intToExternref(x.toInt())

internal fun kotlinToJsStringAdapter(x: String?): stringref? =
    x?.reference

internal fun jsToKotlinStringAdapter(x: stringref): String =
    String(x)

@JsFun("() => []")
internal external fun newJsArray(): ExternalInterfaceType

@JsFun("(array, element) => { array.push(element); }")
internal external fun jsArrayPush(array: ExternalInterfaceType, element: ExternalInterfaceType)

@JsFun("(array, element) => { array.push(element); }")
internal external fun jsArrayPushString(array: ExternalInterfaceType, element: stringref?)