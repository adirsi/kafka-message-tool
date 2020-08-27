package application.constants

object ApplicationConstants {
    const val APPLICATION_NAME = "Kafka Message Tool"
    const val CONFIG_FILE_NAME = "KafkaMessageToolConfig.xml"
    const val AUTHOR = "Grzegorz Wolszczak"
    const val GITHUB_WEBSITE = "https://github.com/grzegorz-wolszczak/kafka-message-tool"
    const val INVALID_TEXT_FIELD_INPUT_PSEUDO_CLASS_NAME = "error"
    const val GLOBAL_CSS_FILE_NAME = "/fx_global.css"
    const val VERSION_PROPERTIES_FILE_NAME = "/version.properties"
    const val DEFAULT_FETCH_TIMEOUT = "5000"
    const val DEFAULT_CONSUMER_GROUP_ID = "kmt-cg"
    const val DEFAULT_MESSAGE_KEY = "kmt-msg-key"
    const val HOSTNAME_REACHABLE_TIMEOUT_MS = 2000 // warning, less than 2000 seconds causes timeouts
    const val FUTURE_GET_TIMEOUT_MS = 5000L
    const val DESCRIBE_CONSUMER_METEADATA_TIMEOUT_MS = 2000L
    const val CLOSE_CONNECTION_TIMEOUT_MS = 2000L
    const val DELETE_TOPIC_FUTURE_GET_TIMEOUT_MS = 2000L
    const val DEFAULT_NEW_TOPIC_NAME = "test"
    const val DEFAULT_NEW_TOPIC_CONFIG_NAME = "<empty name>"
    const val GROOVY_KEYWORDS_STYLES_CSS = "/groovy_keywords_styles.css"
    const val JSON_STYLES_CSS = "/json_styles.css"
    const val DEFAULT_BROKER_CONFIG_NAME = "<new broker config>"
    const val DEFAULT_PORT_AS_STRING = "9092"
    const val DEFAULT_HOSTNAME = "localhost"
    const val DEFAULT_SENDER_CONFIG_NAME = "<new message sender config>"
    const val DEFAULT_LISTENER_CONFIG_NAME = "<new message listener config>"
    const val DEFAULT_TOPIC_CONFIG_NAME = "<new topic config>"
}