// TARGET_BACKEND: WASM

// WASM_DCE_EXPECTED_OUTPUT_SIZE: wasm 35_819
// WASM_DCE_EXPECTED_OUTPUT_SIZE:  mjs  5_605

fun box(): String {
    println("Hello, World!")
    return "OK"
}
