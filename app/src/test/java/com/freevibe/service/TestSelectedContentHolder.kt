package com.freevibe.service

import android.content.Context
import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs

fun testSelectedContentHolder(): SelectedContentHolder {
    val context = mockk<Context>()
    val prefs = mockk<SharedPreferences>()
    val editor = mockk<SharedPreferences.Editor>(relaxed = true)

    every { context.getSharedPreferences(any(), any()) } returns prefs
    every { prefs.getString(any(), any()) } returns null
    every { prefs.edit() } returns editor
    every { editor.putString(any(), any()) } returns editor
    every { editor.apply() } just runs

    return SelectedContentHolder(
        context = context,
        moshi = Moshi.Builder().build(),
    )
}
