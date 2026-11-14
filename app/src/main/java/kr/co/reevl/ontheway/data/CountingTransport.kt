package kr.co.reevl.ontheway.data

/**
 * Counts every server call (driving-context-spec §2.2 request budget — the evidence that nothing goes out while restricted).
 * [onCall] gets the method and the path **without its query** (the query of nearby/area/search carries coordinates; they are
 * never logged).
 */
class CountingTransport(private val inner: HttpTransport, private val onCall: (method: String, path: String) -> Unit) : HttpTransport {
    override fun execute(call: HttpCall): HttpAnswer {
        onCall(call.method, call.path.substringBefore('?'))
        return inner.execute(call)
    }
}
