package com.hoprxi.application;


/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK21
 * @version 0.0.1 builder 2026/2/9
 */

public class AreaSearchException extends RuntimeException {
    public AreaSearchException(String message, Throwable cause) {
        super(message, cause, false, false);
    }

    public AreaSearchException(String message) {
        super(message, null, false, false);
    }
}
