package pers.yufiria.landguard.database.exception;

public class DatabaseLoadException extends RuntimeException {

    public DatabaseLoadException(String message) {
        super(message);
    }

    public DatabaseLoadException(Throwable cause) {
        super(cause);
    }

}
