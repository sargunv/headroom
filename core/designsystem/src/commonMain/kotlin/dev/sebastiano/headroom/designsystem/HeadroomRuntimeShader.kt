package dev.sebastiano.headroom.designsystem

import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush

/** Executes the same AGSL/SkSL source with Android RuntimeShader or desktop Skia. */
class HeadroomRuntimeShader internal constructor(private val backend: RuntimeShaderBackend) {
    constructor(source: String) : this(createRuntimeShaderBackend(source))

    fun setFloatUniform(name: String, vararg values: Float) {
        backend.setFloatUniform(name, values)
    }

    fun setColorUniform(name: String, argb: Int) {
        backend.setColorUniform(name, argb)
    }

    /** A retainable brush that observes subsequent uniform updates on both platforms. */
    fun asBrush(): ShaderBrush = backend.asBrush()

    /** Snapshot an effect whose child [contentName] is the layer being rendered. */
    fun asRenderEffect(contentName: String): RenderEffect = backend.asRenderEffect(contentName)
}

internal interface RuntimeShaderBackend {
    fun setFloatUniform(name: String, values: FloatArray)

    fun setColorUniform(name: String, argb: Int)

    fun asBrush(): ShaderBrush

    fun asRenderEffect(contentName: String): RenderEffect
}

internal expect fun createRuntimeShaderBackend(source: String): RuntimeShaderBackend
