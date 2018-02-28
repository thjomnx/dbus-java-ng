package org.freedesktop.dbus.test;

import java.io.IOException;
import java.text.ParseException;

import org.freedesktop.dbus.connections.BusAddress;
import org.freedesktop.dbus.connections.Transport;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.messages.Message;
import org.freedesktop.dbus.messages.MethodCall;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LowLevelTest extends Assert {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Test
    @Ignore
    public void testLowLevel() throws ParseException, IOException, DBusException {
        String addr = System.getenv("DBUS_SESSION_BUS_ADDRESS");
        addr = "unix:/tmp/test.bus:1";
        logger.debug(addr);
        BusAddress address = new BusAddress(addr);
        logger.debug(address + "");

        try (Transport conn = new Transport(address)) {
            Message m = new MethodCall("org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus", "Hello", (byte) 0, null);
            conn.writeMessage(m);
            m = conn.readMessage();
            logger.debug(m.getClass() + "");
            logger.debug(m + "");
            m = conn.readMessage();
            logger.debug(m.getClass() + "");
            logger.debug(m + "");
            m = conn.readMessage();
            logger.debug("" + m);
            m = new MethodCall("org.freedesktop.DBus", "/", null, "Hello", (byte) 0, null);
            conn.writeMessage(m);
            m = conn.readMessage();
            logger.debug(m + "");
    
            m = new MethodCall("org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus", "RequestName", (byte) 0, "su", "org.testname", 0);
            conn.writeMessage(m);
            m = conn.readMessage();
            logger.debug(m + "");
            m = new DBusSignal(null, "/foo", "org.foo", "Foo", null);
            conn.writeMessage(m);
            m = conn.readMessage();
            logger.debug(m + "");
            conn.disconnect();
        }
    }
}
