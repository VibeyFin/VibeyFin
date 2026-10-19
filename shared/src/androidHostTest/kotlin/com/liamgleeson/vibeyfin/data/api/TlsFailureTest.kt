package com.liamgleeson.vibeyfin.data.api

import java.io.IOException
import java.net.ConnectException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TlsFailureTest {
    @Test
    fun sslErrorsAreTlsFailures() {
        assertTrue(SSLHandshakeException("handshake failed").isTlsFailure())
        assertTrue(SSLPeerUnverifiedException("hostname mismatch").isTlsFailure())
    }

    @Test
    fun certificateErrorsAreTlsFailures() {
        assertTrue(CertificateException("not trusted").isTlsFailure())
    }

    @Test
    fun aTlsErrorInsideAWrapperIsStillFound() {
        val wrapped = IOException("request failed", RuntimeException("engine", SSLHandshakeException("bad cert")))

        assertTrue(wrapped.isTlsFailure())
    }

    @Test
    fun otherNetworkErrorsAreNot() {
        assertFalse(ConnectException("Connection refused").isTlsFailure())
        assertFalse(IOException("timeout").isTlsFailure())
        assertFalse(IllegalStateException("boom").isTlsFailure())
    }
}
