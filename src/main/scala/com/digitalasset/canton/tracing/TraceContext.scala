package com.digitalasset.canton.tracing

// Stub for Canton TraceContext
case class TraceContext(traceId: String = "")

object TraceContext {
  implicit val empty: TraceContext = TraceContext()
}
