package com.digitalasset.canton.logging

import org.slf4j.LoggerFactory

// Stub for Canton NamedLoggerFactory
class NamedLoggerFactory(name: String) {
  def getLogger(clazz: Class[_]) = LoggerFactory.getLogger(clazz)
}

object NamedLoggerFactory {
  def apply(name: String): NamedLoggerFactory = new NamedLoggerFactory(name)
}
