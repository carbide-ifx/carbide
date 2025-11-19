@file:Suppress("unused") // ØS oct 2024: util methods not taken into use yet

package ifx.stdlib

import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream


fun String.resourceAsString(): String = loadResource().readText(Charsets.UTF_8)
fun String.resourceAsFile(): File = File(loadResource().toURI())
fun String.resourceAsStream(): InputStream = loadResource().openStream()
fun String.resourceAsBytes(): ByteArray = loadResource().openStream().readAllBytes()
fun String.loadResource() = object {}.javaClass.classLoader.getResource(this)
    ?: throw FileNotFoundException("Failed to load resource: $this")
