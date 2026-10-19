package com.liamgleeson.vibeyfin.data.api

import io.ktor.client.engine.darwin.DarwinHttpRequestException
import platform.Foundation.NSURLErrorDomain

/** NSURLErrorSecureConnectionFailed (-1200) through NSURLErrorClientCertificateRequired (-1206). */
private val tlsErrorCodes = -1206L..-1200L

actual fun Throwable.isTlsFailure(): Boolean =
    generateSequence(this) { it.cause }.any {
        it is DarwinHttpRequestException &&
            it.origin.domain == NSURLErrorDomain &&
            it.origin.code in tlsErrorCodes
    }
