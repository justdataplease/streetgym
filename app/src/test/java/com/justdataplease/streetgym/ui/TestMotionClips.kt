package com.justdataplease.streetgym.ui

import org.json.JSONObject
import java.io.File

/**
 * The authored clips from the app's asset JSON, for JVM tests. The library parser is private to
 * ExerciseAnimationView.kt, so this reaches it by reflection instead of widening production code.
 */
internal object TestMotionClips {
    private val library: Any by lazy {
        val owner = Class.forName("com.justdataplease.streetgym.ui.AnimationLibrary")
        val companion = owner.getDeclaredField("Companion").apply { isAccessible = true }.get(null)
        val parse = companion.javaClass.getDeclaredMethod("parse", JSONObject::class.java).apply { isAccessible = true }
        parse.invoke(companion, JSONObject(File("src/main/assets/exercise_animations.json").readText()))!!
    }

    val rig: Rig by lazy { library.javaClass.getDeclaredMethod("getRig").apply { isAccessible = true }.invoke(library) as Rig }

    fun named(name: String): MotionClip? {
        val template = library.javaClass.getDeclaredMethod("template", String::class.java)
            .apply { isAccessible = true }
            .invoke(library, name) ?: return null
        val curve = template.javaClass.getDeclaredMethod("getCurve").apply { isAccessible = true }.invoke(template)!!
        return MotionClip::class.java.getDeclaredConstructor(curve.javaClass)
            .apply { isAccessible = true }
            .newInstance(curve)
    }
}
