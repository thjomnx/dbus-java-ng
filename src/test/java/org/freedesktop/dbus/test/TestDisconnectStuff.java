package org.freedesktop.dbus.test;

import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnection.DBusBusType;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.NotConnected;
import org.freedesktop.dbus.test.helper.interfaces.SampleRemoteInterface;
import org.junit.Assert;
import org.junit.Test;

public class TestDisconnectStuff extends Assert {

    @Test
    public void testStuffAfterDisconnect() throws DBusException {

        DBusConnection serverConnection = DBusConnection.getConnection(DBusBusType.SESSION);
        DBusConnection clientConnection = DBusConnection.getConnection(DBusBusType.SESSION);
        serverConnection.setWeakReferences(true);
        clientConnection.setWeakReferences(true);

        serverConnection.requestBusName("foo.bar.why.again.Test");

        SampleRemoteInterface tri =
                clientConnection.getRemoteObject("foo.bar.why.again.Test", "/Test", SampleRemoteInterface.class);
        /** Call a method when disconnected */
        try {
            clientConnection.disconnect();
            serverConnection.disconnect();
            System.out.println("getName() suceeded and returned: " + tri.getName());
            fail("Should not succeed when disconnected");
        } catch (NotConnected exnc) {
        }
    }
}
