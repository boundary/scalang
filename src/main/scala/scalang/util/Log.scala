package scalang.util

import org.slf4j.{Logger, LoggerFactory}

trait Log {
  private val log = LoggerFactory.getLogger(getClass)

  def trace(message : => String) =
    if (log.isTraceEnabled) log.trace(message)
  def trace(message : => String, error:Throwable) =
    if (log.isTraceEnabled) log.trace(message, error)
    
  def debug(message : => String) =
    if (log.isDebugEnabled) log.debug(message)
  def debug(message : => String, error:Throwable) =
    if (log.isDebugEnabled) log.debug(message, error)
    
  def info(message : => String) =
    if (log.isInfoEnabled) log.info(message)
  def info(message : => String, error:Throwable) =
    if (log.isInfoEnabled) log.info(message, error)
    
  def warn(message : => String) =
    if (log.isWarnEnabled) log.warn(message)
  def warn(message : => String, error:Throwable) =
    if (log.isWarnEnabled) log.warn(message, error)
    
  def error(message : => String) =
    if (log.isErrorEnabled) log.error(message)
  def error(message : => String, error:Throwable) =
    if (log.isErrorEnabled) log.error(message, error)
}
