package ifx.logging

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.Configuration
import org.apache.logging.log4j.core.config.ConfigurationFactory
import org.apache.logging.log4j.core.config.ConfigurationSource
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration
import org.slf4j.bridge.SLF4JBridgeHandler
import java.net.URI

class CustomConfigurationFactory : ConfigurationFactory() {
    override fun getConfiguration(loggerContext: LoggerContext, source: ConfigurationSource) =
        getConfiguration(loggerContext, source.toString(), null)

    override fun getConfiguration(loggerContext: LoggerContext, name: String, configLocation: URI?) =
        createConfiguration(name, newConfigurationBuilder())

    override fun getSupportedTypes(): Array<String> = arrayOf("*")

    companion object {
        fun createConfiguration(name: String, builder: ConfigurationBuilder<BuiltConfiguration>): Configuration {
            /* log level for messages about log4j itself. Uncomment line below to troubleshoot Log4j */
            // builder.setStatusLevel(Level.DEBUG)

            installJuLToSlf4jBridge()
            val layout = if (System.getProperty("logformat") == "json") {
                builder.newLayout("JsonTemplateLayout")
            } else {
                builder.newLayout("PatternLayout").addAttribute("pattern", PATTERN)
            }
            val appenderBuilder: AppenderComponentBuilder = builder.newAppender("Stdout", "CONSOLE")
            appenderBuilder.add(layout)
            val rootLogger = builder.newRootLogger(Level.INFO).add(builder.newAppenderRef("Stdout"))

            builder.add(appenderBuilder)
            builder.add(rootLogger)
            return builder.build()
        }

        const val PATTERN =
            "%d{yyyy-MM-dd HH:mm:ss} %highlight{%-5level} %cyan{%logger{36}:} %msg %style{[%thread] [%X{traceId}]}{bright black}%n"


        /**
         * Logging is configured with logback, using the logback.xml file.
         * However, this is required to route java.util.logging logs to SLF4J.
         * See https://www.slf4j.org/api/org/slf4j/bridge/SLF4JBridgeHandler.html
         * and https://medium.com/@a.petrivskyy/using-java-util-logging-and-slf4j-together-e7f2ee1d712b
         * and https://stackoverflow.com/questions/41591459/how-to-make-java-util-logging-send-logs-to-logback
         */
        fun installJuLToSlf4jBridge() {
            check(!julBridgeInitialized) { "SLF4JBridgeHandler already installed" }
            SLF4JBridgeHandler.removeHandlersForRootLogger()
            // add SLF4JBridgeHandler to j.u.l's root logger, should be done once during
            // the initialization phase of your application
            SLF4JBridgeHandler.install()
            julBridgeInitialized = true
        }

        // TODO: Remove this after testing. This is just to make sure we don't accidentally do something expensive multiple times
        var julBridgeInitialized = false
    }
}
