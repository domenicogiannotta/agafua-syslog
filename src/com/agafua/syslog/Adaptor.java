/*
Copyright (c) 2012 Vitaly Russinkovsky

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
 */

package com.agafua.syslog;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Auxiliary class that adapts fields of java.util.logging.LogRecord for syslog format RFC 3164.
 */
class Adaptor {

   public String adaptPriority(LogRecord logRecord, Facility facility) {
        int code = (facility.getId() << 3) + adaptSeverity(logRecord);
        return String.format("<%d>", code);
    }

    public int adaptSeverity(LogRecord logRecord) {
        Level level = logRecord.getLevel();
        if (level.intValue() >= Level.SEVERE.intValue()) {
            return Severity.ERROR.getLevel();
        } else if (level.intValue() >= Level.WARNING.intValue()) {
            return Severity.WARNING.getLevel();
        } else if (level.intValue() >= Level.INFO.intValue()) {
            return Severity.INFO.getLevel();
        } else {
            return Severity.DEBUG.getLevel();
        }
    }

    public String adaptTimeStamp(LogRecord logRecord) {
    	DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        return df.format(new Date(logRecord.getMillis()));
    }


}
