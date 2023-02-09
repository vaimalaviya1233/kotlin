/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("unused")  // Used by compiler

package kotlin.wasm.internal

internal const val TYPE_INFO_ELEMENT_SIZE = 4

internal const val TYPE_INFO_TYPE_PACKAGE_NAME_LENGTH_OFFSET = 0
internal const val TYPE_INFO_TYPE_PACKAGE_NAME_ID_OFFSET = TYPE_INFO_TYPE_PACKAGE_NAME_LENGTH_OFFSET + TYPE_INFO_ELEMENT_SIZE
internal const val TYPE_INFO_TYPE_PACKAGE_NAME_PRT_OFFSET = TYPE_INFO_TYPE_PACKAGE_NAME_ID_OFFSET + TYPE_INFO_ELEMENT_SIZE
internal const val TYPE_INFO_TYPE_SIMPLE_NAME_LENGTH_OFFSET = TYPE_INFO_TYPE_PACKAGE_NAME_PRT_OFFSET + TYPE_INFO_ELEMENT_SIZE
internal const val TYPE_INFO_TYPE_SIMPLE_NAME_ID_OFFSET = TYPE_INFO_TYPE_SIMPLE_NAME_LENGTH_OFFSET + TYPE_INFO_ELEMENT_SIZE
internal const val TYPE_INFO_TYPE_SIMPLE_NAME_PRT_OFFSET = TYPE_INFO_TYPE_SIMPLE_NAME_ID_OFFSET + TYPE_INFO_ELEMENT_SIZE
internal const val TYPE_INFO_SUPER_TYPE_LIST_SIZE_OFFSET = TYPE_INFO_TYPE_SIMPLE_NAME_PRT_OFFSET + TYPE_INFO_ELEMENT_SIZE
internal const val TYPE_INFO_INTERFACE_LIST_SIZE_OFFSET = TYPE_INFO_SUPER_TYPE_LIST_SIZE_OFFSET + TYPE_INFO_ELEMENT_SIZE
internal const val TYPE_INFO_SUPER_TYPE_LIST_OFFSET = TYPE_INFO_INTERFACE_LIST_SIZE_OFFSET + TYPE_INFO_ELEMENT_SIZE

internal class TypeInfoData(val typeId: Int, val isInterface: Boolean, val packageName: String, val typeName: String)

internal fun getTypeInfoTypeDataByPtr(typeInfoPtr: Int): TypeInfoData {
    val fqNameLength = wasm_i32_load(typeInfoPtr + TYPE_INFO_TYPE_PACKAGE_NAME_LENGTH_OFFSET)
    val fqNameId = wasm_i32_load(typeInfoPtr + TYPE_INFO_TYPE_PACKAGE_NAME_ID_OFFSET)
    val fqNamePtr = wasm_i32_load(typeInfoPtr + TYPE_INFO_TYPE_PACKAGE_NAME_PRT_OFFSET)
    val simpleNameLength = wasm_i32_load(typeInfoPtr + TYPE_INFO_TYPE_SIMPLE_NAME_LENGTH_OFFSET)
    val simpleNameId = wasm_i32_load(typeInfoPtr + TYPE_INFO_TYPE_SIMPLE_NAME_ID_OFFSET)
    val simpleNamePtr = wasm_i32_load(typeInfoPtr + TYPE_INFO_TYPE_SIMPLE_NAME_PRT_OFFSET)
    val packageName = stringLiteral(fqNameId, fqNamePtr, fqNameLength)
    val simpleName = stringLiteral(simpleNameId, simpleNamePtr, simpleNameLength)
    return TypeInfoData(typeInfoPtr, isInterface = false, packageName, simpleName)
}

internal fun isInterfaceById(obj: Any, interfaceId: Int): Boolean {
    val superTypeListSize = wasm_i32_load(obj.typeInfo + TYPE_INFO_SUPER_TYPE_LIST_SIZE_OFFSET)
    val interfacesListSize = wasm_i32_load(obj.typeInfo + TYPE_INFO_INTERFACE_LIST_SIZE_OFFSET)

    val interfaceListPtr = obj.typeInfo + TYPE_INFO_SUPER_TYPE_LIST_OFFSET + superTypeListSize * TYPE_INFO_ELEMENT_SIZE
    val interfaceListEndPtr = interfaceListPtr + interfacesListSize * TYPE_INFO_ELEMENT_SIZE

    var currentPtr = interfaceListPtr
    while (currentPtr < interfaceListEndPtr) {
        if (interfaceId == wasm_i32_load(currentPtr)) {
            return true
        }
        currentPtr += TYPE_INFO_ELEMENT_SIZE
    }
    return false
}

internal fun isSupertypeByTypeInfo(obj: Any, typeData: TypeInfoData): Boolean {
    val objPtr = obj.typeInfo
    val typeDataPtr = typeData.typeId
    val objSuperTypesListSize = wasm_i32_load(objPtr + TYPE_INFO_SUPER_TYPE_LIST_SIZE_OFFSET)
    val typeDataTypesListSize = wasm_i32_load(typeDataPtr + TYPE_INFO_SUPER_TYPE_LIST_SIZE_OFFSET)
    if (objSuperTypesListSize < typeDataTypesListSize) return false
    val superTypeOnPositionPtr = objPtr + TYPE_INFO_SUPER_TYPE_LIST_OFFSET + (typeDataTypesListSize - 1) * TYPE_INFO_ELEMENT_SIZE
    return wasm_i32_load(superTypeOnPositionPtr) == typeDataPtr
}

@Suppress("UNUSED_PARAMETER")
@ExcludedFromCodegen
internal fun <T> wasmIsInterface(obj: Any): Boolean =
    implementedAsIntrinsic

@ExcludedFromCodegen
internal fun <T> wasmClassId(): Int =
    implementedAsIntrinsic

@ExcludedFromCodegen
internal fun <T> wasmInterfaceId(): Int =
    implementedAsIntrinsic

@ExcludedFromCodegen
internal fun <T> wasmGetTypeInfoData(): TypeInfoData =
    implementedAsIntrinsic
