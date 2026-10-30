package app.vibeyfin.data.api

import java.security.cert.CertificateException
import javax.net.ssl.SSLException

actual fun Throwable.isTlsFailure(): Boolean = generateSequence(this) { it.cause }.any { it is SSLException || it is CertificateException }
