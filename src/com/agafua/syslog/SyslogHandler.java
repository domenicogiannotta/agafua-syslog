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

import java.net.UnknownHostException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.*;

/**
 * Implementation of java.util.logging.Handler for syslog protocol RFC 3164.
 */
public class SyslogHandler extends Handler {

    private static final int LOG_QUEUE_SIZE = 1024;

    private static final String LOCALHOST = "localhost";
    private static final String MY_HOST_NAME = "0.0.0.0";
    private static final int DEFAULT_PORT = 514;
    private static final int MIN_PORT = 0;
    private static final int MAX_PORT = 65535;
    private static final String DEF_APPNAME = "-";

    private static final String TRANSPORT_PROPERTY = "transport";
    private static final String HOSTNAME_PROPERTY = "hostname";
    private static final String PORT_PROPERTY = "port";
    private static final String FACILITY_PROPERTY = "facility";
    private static final String DAEMON_MODE_PROPERTY = "daemon";
    private static final String FORMATTER_PROPERTY = "formatter";
    private static final String ESCAPE_NEWLINES_PROPERTY = "escapeNewlines";
    private static final String LEVEL_PROPERTY = "level";
    private static final String FILTER_PROPERTY = "filter";
    private static final String APPNAME_PROPERTY = "appname";
    private static final String STRING_SEPARATOR = " ";
    private static final String SYSLOG_VERSION = "1";

    private final String hostName, appName;
    private final int port;
    private final Facility facility;
    private final Transport transport;
    
    private BlockingQueue<Message> blockingQueue = new ArrayBlockingQueue<Message>(LOG_QUEUE_SIZE);
    private boolean closed = false;
    private Thread worker;
    private volatile String myHostName;

    private Adaptor adaptor = new Adaptor();

    /**
     * Option to disable replacing new lines, on by default.
     */
    final static boolean escapeNewLines;
    static {
        escapeNewLines = parseEscapeNewlines();
    }

    public SyslogHandler() {
        super();
        transport = parseTransport();
        hostName = parseHostName();
        appName = parseAppName();
        port = parsePort();
        facility = parseFacility();
        setFormatter(parseFormatter());
        setFilter(parseFilter());
        setLevel(parseLevel());
        if (Transport.TCP.equals(transport)) {
            worker = new Thread(new TcpSender(hostName, port, blockingQueue));
        } else {
            worker = new Thread(new UdpSender(hostName, port, blockingQueue));
        }
        worker.start();
    }

    @Override
    public void publish(LogRecord record) {
        if (closed) {
            return;
        }
        //<15>1 2024-01-16T18:14:15.003Z cbdlapxas01.ux.cbdom.it APP_NAME MESSAGGIO"
        try {
            Message message = new Message();
            String pri = adaptor.adaptPriority(record, facility);
            message.print(pri);
            message.print(SYSLOG_VERSION);
            message.print(STRING_SEPARATOR);
            String timestamp = adaptor.adaptTimeStamp(record);
            message.print(timestamp);
            message.print(STRING_SEPARATOR);
            String host = getLocalHostname();
            message.print(host);
            message.print(STRING_SEPARATOR);
            message.print(appName);
            message.print(STRING_SEPARATOR);
            String msg = getFormatter().format(record);
            message.print(msg);
            blockingQueue.offer(message);
        } catch (Throwable t) {

        }
    }

    @Override
    public void flush() {
        // Does nothing because sending is asynchronous
    }

    @Override
    public void close() throws SecurityException {
        blockingQueue.clear();
        if (worker != null) {
            worker.interrupt();
        }
        worker = null;
        closed = true;
    }

    public String getTransport() {
        return transport.name();
    }

    public String getHostName() {
        return hostName;
    }

    public int getPort() {
        return port;
    }

    public String getFacility() {
        return facility.name();
    }

    private Transport parseTransport() {
        String transportValue = ConfigurationUtil.getStringPropertyOfLogHandlerClass(SyslogHandler.class, TRANSPORT_PROPERTY);
        for (Transport t : Transport.values()) {
            if (t.name().equalsIgnoreCase(transportValue)) {
                return t;
            }
        }
        return Transport.UDP;
    }

    private String parseHostName() {
    	String val =  ConfigurationUtil.getStringPropertyOfLogHandlerClass(SyslogHandler.class, HOSTNAME_PROPERTY);
    	return val != null ? val : LOCALHOST;
    }
    
    private String parseAppName() {
    	String val =  ConfigurationUtil.getStringPropertyOfLogHandlerClass(SyslogHandler.class, APPNAME_PROPERTY);
    	return val != null ? val : DEF_APPNAME;
    }

    private int parsePort() {
        String portValue = ConfigurationUtil.getStringPropertyOfLogHandlerClass(SyslogHandler.class, PORT_PROPERTY);
        if (portValue != null) {
            Integer p = null;
            try {
                p = Integer.parseInt(portValue);
            } catch (NumberFormatException e) { }
            if (p != null && p >= MIN_PORT && p < MAX_PORT) {
                return p;
            }
        }
        return DEFAULT_PORT;
    }

    private Facility parseFacility() {
        String facilityValue = ConfigurationUtil.getStringPropertyOfLogHandlerClass(SyslogHandler.class, FACILITY_PROPERTY);
        for (Facility f : Facility.values()) {
            if (f.name().equalsIgnoreCase(facilityValue)) {
                return f;
            }
        }
        return Facility.USER;
    }

    private String getLocalHostname() {
        if (myHostName != null) {
            return myHostName;
        }
        try {
            String localHostName = java.net.InetAddress.getLocalHost().getHostName();
            if (localHostName != null) {
                myHostName = localHostName;
                return localHostName;
            }
        } catch (UnknownHostException e) {
            // Nothing
        }
        try {
            String localHostAddress = java.net.Inet4Address.getLocalHost().getHostAddress();
            if (localHostAddress != null) {
                myHostName = localHostAddress;
                return localHostAddress;
            }
        } catch (UnknownHostException e) {
            // Nothing
        }
        myHostName = MY_HOST_NAME;
        return myHostName;
    }
    
    /**
     * Parse formatter class from the property, instantiate it and use 
     * in the class.
     * If there any problems within parsing or instantiating of <tt>formatter</tt>
     * property, the <tt>net.java.util.logging.SimpleFormatter</tt> will be used
     * 
     * @return The instance of formatter to be used to format actual log 
     *         output
     */
    private Formatter parseFormatter() {
        String formatterClassProperty = ConfigurationUtil.getStringPropertyOfLogHandlerClass(SyslogHandler.class, FORMATTER_PROPERTY);
        try {
        	if (formatterClassProperty != null) {
        		Class<?> formatterClass = ClassLoader.getSystemClassLoader().loadClass(formatterClassProperty);
            	return (Formatter)formatterClass.newInstance();
            } 
        } catch (Exception e) { }
        return new SimpleFormatter();    
    }
    
   
    private Level parseLevel() {
        String val = ConfigurationUtil.getStringPropertyOfLogHandlerClass(SyslogHandler.class, LEVEL_PROPERTY);
        try {
        	if (val != null) {
        		return Level.parse(val.trim());
        	}
        } catch (Exception e) { }
        return Level.ALL;
    }
    
    private Filter parseFilter() {
        String val = ConfigurationUtil.getStringPropertyOfLogHandlerClass(SyslogHandler.class, FILTER_PROPERTY);
        try {
            if (val != null) {
                Class<?> clz = ClassLoader.getSystemClassLoader().loadClass(val);
                return (Filter) clz.newInstance();
            }
        } catch (Exception ex) { }
        return null;
    }
    
    /**
     * Checks for ESCAPE_NEWLINES_PROPERTY and return its boolean value if any.
     * By default new lines are escaped.
     * @return whether new lines need to be escaped.
     */
    private static boolean parseEscapeNewlines() {
        String escapeProperty = ConfigurationUtil.getStringPropertyOfLogHandlerClass(SyslogHandler.class, ESCAPE_NEWLINES_PROPERTY);
        // by default new lines are replaced
        if(escapeProperty != null)
            return Boolean.parseBoolean(escapeProperty);
        else
            return true;
    }
    
}
