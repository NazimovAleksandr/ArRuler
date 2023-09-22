package com.ar_ruler.halpers

infix fun Any?.ifNull(block: () -> Unit) {
    if (this == null) block.invoke()
}