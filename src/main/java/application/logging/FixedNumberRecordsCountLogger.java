package application.logging;

import application.root.Restartable;
import application.utils.RepeatableTimer;
import javafx.application.Platform;
import javafx.scene.control.TextArea;

import java.util.concurrent.ConcurrentLinkedQueue;

public class FixedNumberRecordsCountLogger implements Restartable {

    public static final int REPEAT_RATE_MS = 250;
    private final ConcurrentLinkedQueue<String> stringBufferQueue = new ConcurrentLinkedQueue<>();
    private final RepeatableTimer appendTextTimer = new RepeatableTimer();
    private TextArea logTextArea;
    private String localBuffer = "";
    private CyclicStringBuffer cyclicBuffer;

    public FixedNumberRecordsCountLogger(CyclicStringBuffer buffer) {
        this(null, buffer);
    }

    public FixedNumberRecordsCountLogger(TextArea logTextArea,
                                         CyclicStringBuffer buffer) {
        this.logTextArea = logTextArea;
        this.cyclicBuffer = buffer;
    }

    public void setLogTextArea(TextArea logTextArea) {
        this.logTextArea = logTextArea;
    }

    public void appendText(String text) {
        //text = normalizeNewlines(text);
        stringBufferQueue.add(text);
    }

    public void start() {
        appendTextTimer.cancel();
        appendTextTimer.startExecutingRepeatedly(this::periodicallyAppendTextToTextEdit, REPEAT_RATE_MS);
    }

    public void stop() {
        appendTextTimer.cancel();
    }

    public void clear() {
        Platform.runLater(logTextArea::clear);
        localBuffer = "";
        cyclicBuffer.clear();
    }

    /*
    For unknown reason, when text with sequence '\r\n' is appended to  TextArea it will be replaced with '\n'
    so when we call TextArea.getText() it return different! text that was put in !
    In order to compare text between cyclicBuffer text and TextArea text, we make sure that
    all strings in TextArea are also without '\r'
     */
//    //private String normalizeNewlines(String text) {
//        return text.replace("\r\n", "\n");
//    }

    private void periodicallyAppendTextToTextEdit() {
        final int size = stringBufferQueue.size();
        for (int i = 0; i < size; i++) {
            cyclicBuffer.appendText(stringBufferQueue.remove());
        }

        final String currentBufferContent = cyclicBuffer.getContent();
        //final String textAreaText = logTextArea.getText();
        if (!currentBufferContent.equals(localBuffer)) {
            Platform.runLater(() -> {
                logTextArea.clear();
                localBuffer = currentBufferContent;
                logTextArea.appendText(localBuffer);
            });
        }
    }
}
