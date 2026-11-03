package app.vibeyfin.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

class AvatarTintTest {
    @Test
    fun `a name gets the tint Android always gave it, whatever its case`() {
        // The JVM's own String.hashCode decided these before the tint was shared; a long name overflows the hash on purpose
        assertEquals(0, AvatarTint.of("Alex"))
        assertEquals(5, AvatarTint.of("Liam"))
        assertEquals(5, AvatarTint.of("LIAM"))
        assertEquals(1, AvatarTint.of("Lorenzo"))
        assertEquals(3, AvatarTint.of("Maximilian Fitzgerald"))
    }
}
