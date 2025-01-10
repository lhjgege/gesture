/*
 * Decompiled with CFR 0.152.
 */
package com.lhj.read.script

import java.io.Reader

interface Compilable {

    @Throws(ScriptException::class)
    fun compile(script: Reader): CompiledScript

    @Throws(ScriptException::class)
    fun compile(script: String): CompiledScript
}