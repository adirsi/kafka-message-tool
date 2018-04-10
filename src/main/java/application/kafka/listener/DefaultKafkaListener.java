package application.kafka.listener;

import application.logging.Logger;
import application.model.modelobjects.KafkaBrokerConfig;
import application.model.modelobjects.KafkaListenerConfig;
import application.model.modelobjects.KafkaTopicConfig;
import application.utils.AppUtils;
import application.utils.HostInfo;
import application.utils.RepeatableTimer;
import application.utils.TimestampUtils;
import application.utils.kafka.KafkaBrokerHostInfo;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

import static application.utils.PropertiesUtils.prettyProperties;
import static application.utils.TimestampUtils.timestampFromEpochMili;
import static java.lang.Thread.sleep;

public class DefaultKafkaListener implements Listener {

    private static final int ADDITIONAL_WAIT_DURATION_BEFORE_WAKEUP_MS = 2000;
    public static final int REPEAT_RATE_MS = 500;
    private final StringProperty loggedText = new SimpleStringProperty();
    private final KafkaListenerConfig listenerConfig;
    private final BooleanProperty isRunning = new SimpleBooleanProperty(false);
    private final AtomicBoolean shouldBeRunning = new AtomicBoolean(false);
    private final StringBuilder collector = new StringBuilder();
    private KafkaBrokerHostInfo brokerHost;

    private Consumer<String, String> consumer;
    private FutureTask<Void> wakeUpTask;
    private Thread fetchThread;
    private final SynchronizedStringBuffer buffer = new SynchronizedStringBuffer();
    private final RepeatableTimer logCollectorTimer = new RepeatableTimer();

    public DefaultKafkaListener(KafkaListenerConfig listenerConfig) {
        this.listenerConfig = listenerConfig;
    }

    @Override
    public KafkaListenerConfig getListenerConfig() {
        return listenerConfig;
    }

    @Override
    public StringProperty loggedTextProperty() {
        return loggedText;
    }

    @Override
    public BooleanProperty isRunningProperty() {
        return isRunning;
    }

    @Override
    public void start() {
        try {
            tryStart();
        } catch (Throwable t) {
            Logger.error("Starting consumer thread failed", t);
        }
    }

    private synchronized void appendLog(String text) {
        loggedText.set(text);
    }

    @Override
    public void stop() {
        try {
            tryStop();
        } catch (Throwable e) {
            Logger.warn("Problems with stopping consumer thread: ", e);
        }
    }

    private Consumer<String, String> setUpConsumer() {
        Logger.trace("Preparing consumer");
        final String topicName = listenerConfig.getRelatedConfig().getTopicName();
        final Consumer<String, String> consumer = createConsumer(brokerHost);
        final List<String> topics = Collections.singletonList(topicName);
        Logger.trace(String.format("Subscribing for topics '%s'", topics));
        consumer.subscribe(topics);
        return consumer;
    }

    private void fetch() {
        if (!canUseTopicConfigForListener()) {
            Logger.error("Could not start consumer. Topic config is invalid.");
            return;
        }

        final KafkaTopicConfig topicConfig = listenerConfig.getRelatedConfig();
        try {

            tryFetch(topicConfig);

        } catch (WakeupException ignored) {
            Logger.trace("Closing consumer due to wakeup()");
            closeConsumer();

        } catch (Throwable t) {
            Logger.error("Exception for fetch()", t);
        } finally {
            if (isRunning.get()) {
                Logger.info(String.format("Consumer stopped (topic:%s, consumer group:%s)", topicConfig.getTopicName(),
                        listenerConfig.getConsumerGroup()));
            }
            shouldBeRunning.set(false);
            isRunning.set(false);
        }
    }

    private void tryFetch(KafkaTopicConfig topicConfig) {
        final KafkaBrokerConfig brokerConfig = topicConfig.getRelatedConfig();

        Logger.info(String.format("Starting consumer '%s',  consumer group '%s'",
                listenerConfig.getName(),
                listenerConfig.getConsumerGroup()));

        this.brokerHost = brokerConfig.getHostInfo();
        consumer = setUpConsumer();

        shouldBeRunning.set(true);
        isRunning.set(true);

        Logger.info(String.format("Consumer started (topic:%s, consumer group:%s)", topicConfig.getTopicName(),
                listenerConfig.getConsumerGroup()));
        final long pollTimeout = Long.parseLong(listenerConfig.getPollTimeout());

        while (shouldBeRunning.get()) {
            consume(consumer, pollTimeout);
        }

        closeConsumer();
    }

    private void closeConsumer() {
        AppUtils.runAndSwallowExceptions(() -> {
            if (consumer != null) {
                consumer.unsubscribe();
                consumer.close();
            }
        });
    }

    private boolean canUseTopicConfigForListener() {
        final KafkaTopicConfig topicConfig = listenerConfig.getRelatedConfig();
        return topicConfig != null && topicConfig.getRelatedConfig() != null;
    }

    private Consumer<String, String> createConsumer(HostInfo hostname) {
        final Properties config = createConsumerProperties(hostname);
        return new KafkaConsumer<>(config);
    }

    private void consume(Consumer<String, String> consumer, long pollTimeout) {
        startWakeUpTask(pollTimeout);
        final ConsumerRecords<String, String> records = consumer.poll(pollTimeout);
        cancelWakeupTask();
        logReceivedRecords(records);
        consumer.commitSync();

    }

    private void logReceivedRecords(ConsumerRecords<String, String> records) {
        records.forEach(record -> {
            buffer.appendContent(prepareConsumerRecordToBeLogged(record));
        });
    }

    private void startWakeUpTask(long pollTimeout) {
        wakeUpTask = scheduleWakeupTask(pollTimeout);
    }

    private void cancelWakeupTask() {
        if (wakeUpTask == null) {
            return;
        }
        wakeUpTask.cancel(true);
        wakeUpTask = null;
    }

    private FutureTask<Void> scheduleWakeupTask(long pollTimeoutMs) {
        FutureTask<Void> task = new FutureTask<>(() -> {
            final long wakeUpDurationMs = pollTimeoutMs + ADDITIONAL_WAIT_DURATION_BEFORE_WAKEUP_MS;
            sleep(wakeUpDurationMs);
            Logger.warn(String.format("Waking up consumer (after %d ms), because consumer::poll() did not respond within its %d ms timeout."
                    , wakeUpDurationMs, pollTimeoutMs));
            wakeUpConsumer();
            return null;
        });
        // todo: consider thread pool
        new Thread(task, "Consumer-Poll-WakeUpThread").start();
        return task;
    }

    private void wakeUpConsumer() {
        AppUtils.runAndSwallowExceptions(() -> {
            if (consumer != null) {
                consumer.wakeup();
            }
        });
    }

    private String prepareConsumerRecordToBeLogged(ConsumerRecord<String, String> record) {
        return String.format("[%s] ConsumerRecord: (key:%s,  partition:%d, offset:%d, timestamp:%s)%nvalue '%s'%n",
                TimestampUtils.nowTimestamp(),
                record.key(),
                record.partition(),
                record.offset(),
                timestampFromEpochMili(record.timestamp()),
                record.value());
    }

    private void tryStart() {
        stop();
        logCollectorTimer.startExecutingRepeatedly(()->{
            appendLog(buffer.getContent());
        }, REPEAT_RATE_MS);
        fetchThread = new Thread(this::fetch, buildThreadNameForDebugging());
        fetchThread.start();
    }

    private String buildThreadNameForDebugging() {
        return "Thread-kafka-message-tool-listener:" + listenerConfig.getName();
    }

    private void tryStop() {
        logCollectorTimer.cancel();
        shouldBeRunning.set(false);
        cancelWakeupTask();
        wakeUpConsumer();
    }

    private Properties createConsumerProperties(HostInfo hostInfo) {
        final Properties config = new Properties();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, hostInfo.toHostPortString());
        config.put(ConsumerConfig.GROUP_ID_CONFIG, listenerConfig.getConsumerGroup());
        config.put(ConsumerConfig.CLIENT_ID_CONFIG, listenerConfig.getName());
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, listenerConfig.getOffsetResetConfig().value());
        Logger.trace("Consumer properties:\n" + prettyProperties(config));

        return config;
    }

}
