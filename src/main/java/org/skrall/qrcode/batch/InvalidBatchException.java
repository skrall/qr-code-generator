package org.skrall.qrcode.batch;

/**
 * A batch that cannot start: the CSV is unreadable, too large, has no {@code data} column or too
 * many rows, or the options contradict each other. The message is phrased for the person who
 * uploaded the file. A single row that cannot be rendered is not this: it is skipped and reported.
 */
public class InvalidBatchException extends RuntimeException {

    public InvalidBatchException(String message) {
        super(message);
    }
}
