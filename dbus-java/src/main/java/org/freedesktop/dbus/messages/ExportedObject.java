package org.freedesktop.dbus.messages;

import java.lang.annotation.Annotation;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.freedesktop.dbus.Marshalling;
import org.freedesktop.dbus.MethodTuple;
import org.freedesktop.dbus.StrongReference;
import org.freedesktop.dbus.Tuple;
import org.freedesktop.dbus.TypeRef;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.DBusMemberName;
import org.freedesktop.dbus.annotations.DBusProperties;
import org.freedesktop.dbus.annotations.DBusProperty;
import org.freedesktop.dbus.connections.AbstractConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.DBusInterface;

public class ExportedObject {
    private Map<MethodTuple, Method> methods;
    private Reference<DBusInterface> object;
    private String                   introspectiondata;

    public ExportedObject(DBusInterface _object, boolean _weakreferences) throws DBusException {
        if (_weakreferences) {
            this.object = new WeakReference<>(_object);
        } else {
            this.object = new StrongReference<>(_object);
        }
        introspectiondata = "";
        methods = getExportedMethods(_object.getClass());
        introspectiondata += " <interface name=\"org.freedesktop.DBus.Introspectable\">\n" +
                "  <method name=\"Introspect\">\n" +
                "   <arg type=\"s\" direction=\"out\"/>\n" +
                "  </method>\n" +
                " </interface>\n";
        introspectiondata += " <interface name=\"org.freedesktop.DBus.Peer\">\n" +
                "  <method name=\"Ping\">\n" +
                "  </method>\n" +
                "  <method name=\"GetMachineId\">\n" +
                "   <arg type=\"s\" name=\"machine_uuid\" direction=\"out\"/>\n" +
                "  </method>\n" +
                " </interface>\n";
    }

    private String getAnnotations(AnnotatedElement c) {
        String ans = "";
        for (Annotation a : c.getDeclaredAnnotations()) {

            if (!a.annotationType().isAssignableFrom(DBusInterface.class)) { // skip all interfaces not compatible with
                                                                             // DBusInterface (mother of all DBus
                                                                             // related interfaces)
                continue;
            }
            Class<?> t = a.annotationType();
            String value = "";
            try {
                Method m = t.getMethod("value");
                if (m != null) {
                    value = m.invoke(a).toString();
                }
            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException _ex) {
                // ignore
            }

            ans += "  <annotation name=\"" + AbstractConnection.DOLLAR_PATTERN.matcher(t.getName()).replaceAll(".")
                    + "\" value=\"" + value + "\" />\n";
        }
        return ans;
    }

    protected String getProperty(DBusProperty property) throws DBusException {
        Class<?> propertyTypeClass = property.type();
        String propertyTypeString;
        if (TypeRef.class.isAssignableFrom(propertyTypeClass)) {
            Type actualType = Arrays.stream(propertyTypeClass.getGenericInterfaces())
                    .filter(t -> t instanceof ParameterizedType)
                    .map(t -> (ParameterizedType) t)
                    .filter(t -> TypeRef.class.equals(t.getRawType()))
                    .map(t -> t.getActualTypeArguments()[0]) // TypeRef has one generic argument
                    .findFirst()
                    .orElseThrow(() ->
                            new DBusException("Could not read TypeRef type for property '" + property.name() + "'")
                    );
            propertyTypeString = Marshalling.getDBusType(new Type[]{actualType});
        } else if (List.class.equals(propertyTypeClass)) {
            // default non generic list types
            propertyTypeString = "av";
        } else if (Map.class.equals(propertyTypeClass)) {
            // default non generic map type
            propertyTypeString = "a{vv}";
        } else {
            propertyTypeString = Marshalling.getDBusType(new Type[]{propertyTypeClass});
        }

        String access = property.access().getAccessName();
        return "<property name=\"" + property.name() + "\" type=\"" + propertyTypeString + "\" access=\"" + access + "\" />";
    }

    protected String getProperties(Class<?> c) throws DBusException {
        StringBuilder xml = new StringBuilder();
        DBusProperties properties = c.getAnnotation(DBusProperties.class);
        if (properties != null) {
            for (DBusProperty property : properties.value()) {
                xml.append("  ").append(getProperty(property)).append("\n");
            }
        }
        DBusProperty property = c.getAnnotation(DBusProperty.class);
        if (property != null) {
            xml.append("  ").append(getProperty(property)).append("\n");
        }
        return xml.toString();
    }

    private Map<MethodTuple, Method> getExportedMethods(Class<?> c) throws DBusException {
        if (DBusInterface.class.equals(c)) {
            return new HashMap<>();
        }
        Map<MethodTuple, Method> m = new HashMap<>();
        for (Class<?> i : c.getInterfaces()) {
            if (DBusInterface.class.equals(i)) {
                // add this class's public methods
                if (null != c.getAnnotation(DBusInterfaceName.class)) {
                    String name = c.getAnnotation(DBusInterfaceName.class).value();
                    introspectiondata += " <interface name=\"" + name + "\">\n";
                    DBusSignal.addInterfaceMap(c.getName(), name);
                } else {
                    // don't let people export things which don't have a
                    // valid D-Bus interface name
                    if (c.getName().equals(c.getSimpleName())) {
                        throw new DBusException("DBusInterfaces cannot be declared outside a package");
                    }
                    if (c.getName().length() > AbstractConnection.MAX_NAME_LENGTH) {
                        throw new DBusException(
                                "Introspected interface name exceeds 255 characters. Cannot export objects of type "
                                        + c.getName());
                    } else {
                        introspectiondata += " <interface name=\""
                                + AbstractConnection.DOLLAR_PATTERN.matcher(c.getName()).replaceAll(".") + "\">\n";
                    }
                }
                introspectiondata += getAnnotations(c);
                for (Method meth : c.getDeclaredMethods()) {
                    if (Modifier.isPublic(meth.getModifiers())) {
                        String ms = "";
                        String name;
                        if (meth.isAnnotationPresent(DBusMemberName.class)) {
                            name = meth.getAnnotation(DBusMemberName.class).value();
                        } else {
                            name = meth.getName();
                        }
                        if (name.length() > AbstractConnection.MAX_NAME_LENGTH) {
                            throw new DBusException(
                                    "Introspected method name exceeds 255 characters. Cannot export objects with method "
                                            + name);
                        }
                        introspectiondata += "  <method name=\"" + name + "\" >\n";
                        introspectiondata += getAnnotations(meth);
                        for (Class<?> ex : meth.getExceptionTypes()) {
                            if (DBusExecutionException.class.isAssignableFrom(ex)) {
                                introspectiondata +=
                                        "   <annotation name=\"org.freedesktop.DBus.Method.Error\" value=\""
                                                + AbstractConnection.DOLLAR_PATTERN.matcher(ex.getName())
                                                        .replaceAll(".")
                                                + "\" />\n";
                            }
                        }
                        for (Type pt : meth.getGenericParameterTypes()) {
                            for (String s : Marshalling.getDBusType(pt)) {
                                introspectiondata += "   <arg type=\"" + s + "\" direction=\"in\"/>\n";
                                ms += s;
                            }
                        }
                        if (!Void.TYPE.equals(meth.getGenericReturnType())) {
                            if (Tuple.class.isAssignableFrom(meth.getReturnType())) {
                                ParameterizedType tc = (ParameterizedType) meth.getGenericReturnType();
                                Type[] ts = tc.getActualTypeArguments();

                                for (Type t : ts) {
                                    if (t != null) {
                                        for (String s : Marshalling.getDBusType(t)) {
                                            introspectiondata += "   <arg type=\"" + s + "\" direction=\"out\"/>\n";
                                        }
                                    }
                                }
                            } else if (Object[].class.equals(meth.getGenericReturnType())) {
                                throw new DBusException("Return type of Object[] cannot be introspected properly");
                            } else {
                                for (String s : Marshalling.getDBusType(meth.getGenericReturnType())) {
                                    introspectiondata += "   <arg type=\"" + s + "\" direction=\"out\"/>\n";
                                }
                            }
                        }
                        introspectiondata += "  </method>\n";
                        m.put(new MethodTuple(name, ms), meth);
                    }
                }
                introspectiondata += getProperties(c);
                for (Class<?> sig : c.getDeclaredClasses()) {
                    if (DBusSignal.class.isAssignableFrom(sig)) {
                        String name;
                        if (sig.isAnnotationPresent(DBusMemberName.class)) {
                            name = sig.getAnnotation(DBusMemberName.class).value();
                            DBusSignal.addSignalMap(sig.getSimpleName(), name);
                        } else {
                            name = sig.getSimpleName();
                        }
                        if (name.length() > AbstractConnection.MAX_NAME_LENGTH) {
                            throw new DBusException(
                                    "Introspected signal name exceeds 255 characters. Cannot export objects with signals of type "
                                            + name);
                        }
                        introspectiondata += "  <signal name=\"" + name + "\">\n";
                        Constructor<?> con = sig.getConstructors()[0];
                        Type[] ts = con.getGenericParameterTypes();
                        for (int j = 1; j < ts.length; j++) {
                            for (String s : Marshalling.getDBusType(ts[j])) {
                                introspectiondata += "   <arg type=\"" + s + "\" direction=\"out\" />\n";
                            }
                        }
                        introspectiondata += getAnnotations(sig);
                        introspectiondata += "  </signal>\n";

                    }
                }
                introspectiondata += " </interface>\n";
            } else {
                // recurse
                m.putAll(getExportedMethods(i));
            }
        }
        return m;
    }

    public Map<MethodTuple, Method> getMethods() {
        return methods;
    }

    public Reference<DBusInterface> getObject() {
        return object;
    }

    public String getIntrospectiondata() {
        return introspectiondata;
    }

}
