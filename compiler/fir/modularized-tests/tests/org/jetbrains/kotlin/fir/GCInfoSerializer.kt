/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.encoding.CompositeDecoder

object GCInfoSerializer : KSerializer<GCInfo> {
    override val descriptor = buildClassSerialDescriptor("GCInfo") {
        element("name", PrimitiveSerialDescriptor("name", PrimitiveKind.STRING))
        element("gcTime", PrimitiveSerialDescriptor("gcTime", PrimitiveKind.LONG))
        element("collections", PrimitiveSerialDescriptor("collections", PrimitiveKind.LONG))
    }

    override fun serialize(encoder: Encoder, value: GCInfo) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.name)
            encodeLongElement(descriptor, 1, value.gcTime)
            encodeLongElement(descriptor, 2, value.collections)
        }
    }

    override fun deserialize(decoder: Decoder): GCInfo {
        return decoder.decodeStructure(descriptor) {
            var name = ""
            var gcTime = -1L
            var collections = -1L
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> name = decodeStringElement(descriptor, 0)
                    1 -> gcTime = decodeLongElement(descriptor, 1)
                    2 -> collections = decodeLongElement(descriptor, 2)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            GCInfo(name, gcTime, collections)
        }
    }
}