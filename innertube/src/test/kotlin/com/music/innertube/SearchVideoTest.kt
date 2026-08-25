package com.music.innertube

import com.music.innertube.YouTube.SearchFilter
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Test

class SearchVideoTest {
    // The explicit `: Unit` is load-bearing. The body's last expression is
    // `items?.forEach { … }`, which is `Unit?` — not `Unit` — so an expression body made the compiled
    // method return Object, and JUnit rejects the whole CLASS with "Method testVideoSearch() should be
    // void". That took every other test in this module down with it: `:innertube:test` has been failing
    // to initialise since ec290c85, so the module has effectively had no test coverage at all.
    //
    // FASE 17 (2026-08-24, súper auditoría): neutralizado con @Ignore. Este test pega contra la red
    // real de YouTube y no tiene assertions (solo println), así que es no determinista y tumbaría
    // cualquier gate de CI que incluya el módulo sin dar señal útil. Se conserva para correrlo a mano
    // (quitando el @Ignore localmente); la suite mínima de seguridad del CI es solo :app.
    @Ignore("Hits the live YouTube network with no assertions; non-deterministic. Run manually.")
    @Test
    fun testVideoSearch(): Unit = runBlocking {
        val result = YouTube.search("fakira", SearchFilter("EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D"))
        println("Result: $result")
        if (result.isSuccess) {
            println("Items size: ${result.getOrNull()?.items?.size}")
            result.getOrNull()?.items?.forEach {
                println("Item: $it")
            }
        } else {
            println("Error: ${result.exceptionOrNull()}")
        }
    }
}
