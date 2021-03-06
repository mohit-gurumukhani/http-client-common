package io.ktor.common.client

import platform.Foundation.*
import kotlin.coroutines.experimental.*

actual class HttpClient actual constructor() : Closeable {

    actual suspend fun request(request: HttpRequest): HttpResponse = suspendCoroutine { continuation ->
        val delegate = object : NSObject(), NSURLSessionDataDelegateProtocol {
            val receivedData = NSMutableData()

            override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
                receivedData.appendData(didReceiveData)
            }

            override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
                val rawResponse = task.response ?: return
                if (didCompleteWithError != null) return

                val responseData = rawResponse as NSHTTPURLResponse

                @Suppress("UNCHECKED_CAST")
                val headersDict = responseData.allHeaderFields as Map<String, String>

                val response = HttpResponseBuilder(request).apply {
                    statusCode = responseData.statusCode.toInt()
                    headersDict.mapKeys { it ->
                        headers[it.key] = listOf(it.value)
                    }

                    body = receivedData.decode(NSWindowsCP1251StringEncoding)
                }.build()

                continuation.resume(response)
            }
        }

        val session = NSURLSession.sessionWithConfiguration(
            NSURLSessionConfiguration.defaultSessionConfiguration(),
            delegate, delegateQueue = NSOperationQueue.mainQueue()
        )

        val URLString = request.url.build()
        val url = NSURL(string = URLString)
        val nativeRequest = NSMutableURLRequest.requestWithURL(url)

        request.headers.forEach { (key, values) ->
            values.forEach {
                nativeRequest.setValue(it, key)
            }
        }

        nativeRequest.setHTTPMethod(request.method.value)
        request.body?.let { nativeRequest.setHTTPBody(it.encode()) }
        session.dataTaskWithRequest(nativeRequest).resume()
    }

    override fun close() {
    }
}

private fun String.encode(): NSData = (this as NSString).dataUsingEncoding(NSWindowsCP1251StringEncoding)!!

private fun NSData.decode(encoding: NSStringEncoding) = NSString.create(this, encoding)!! as String
