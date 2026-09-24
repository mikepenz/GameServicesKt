package com.mikepenz.gameservices.gamecenter

import kotlin.test.*

class NativeBindingsTest {
    @Test fun packagedLibraryLoadsAndSessionCanClose() {
        if (System.getProperty("os.name") != "Mac OS X") return
        NativeBindings.load(null)
        val handle = NativeBindings.create()
        assertNotEquals(0L, handle)
        NativeBindings.close(handle)
    }

    @Test fun completionIsDeliveredOnceAndCancelledRequestsIgnoreLateCallbacks() {
        val id = NativeBindings.ids.incrementAndGet()
        var result: String? = null
        NativeBindings.pending[id] = { ok, data -> assertTrue(ok); result = data }
        NativeBindings.complete(id, true, "玩家 🎮".encodeToByteArray())
        NativeBindings.complete(id, true, "duplicate".encodeToByteArray())
        assertEquals("玩家 🎮", result)
        NativeBindings.pending[id] = { _, _ -> fail("Cancelled callback delivered") }
        NativeBindings.pending.remove(id)
        NativeBindings.complete(id, true, byteArrayOf())
    }
}
