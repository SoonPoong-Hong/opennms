/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2011-2017 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2017 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.jicmp.jna;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.LastErrorException;
import com.sun.jna.Platform;

/**
 * NativeDatagramSocket
 *
 * @author brozow
 */
public abstract class NativeDatagramSocket {
    private static final Logger LOG = LoggerFactory.getLogger(NativeDatagramSocket.class);

    public static final int AF_INET = 2;
    public static final int PF_INET = AF_INET;
    public static final int AF_INET6 = Platform.isMac() ? 30 
        : Platform.isLinux() ? 10 
            : Platform.isWindows() ? 23 
                : Platform.isFreeBSD() ? 28 
                    : Platform.isSolaris() ? 26 
                        : -1;
    public static final int PF_INET6 = AF_INET6;

    public static final int SOCK_DGRAM = Platform.isSolaris() ? 1 
        : 2;
    public static final int SOCK_RAW = Platform.isSolaris() ? 4 
        : 3;

    public static final int IPPROTO_ICMP = 1;
    public static final int IPPROTO_UDP = 17;
    public static final int IPPROTO_ICMPV6 = 58;

    private static ThreadLocal<String> m_classPackage = new ThreadLocal<>();
    private static ThreadLocal<String> m_classPrefix = new ThreadLocal<>();

    public NativeDatagramSocket() {
        if (AF_INET6 == -1) {
            throw new UnsupportedPlatformException(System.getProperty("os.name"));
        }
    }

    public static NativeDatagramSocket create(final int family, final int type, final int protocol) throws Exception {
        final String implClassName = NativeDatagramSocket.getImplementationClassName(family);
        LOG.debug("{}({}, {}, {})", implClassName, family, type, protocol);
        final Class<? extends NativeDatagramSocket> implementationClass = Class.forName(implClassName).asSubclass(NativeDatagramSocket.class);
        return implementationClass.getDeclaredConstructor(Integer.TYPE, Integer.TYPE, Integer.TYPE).newInstance(family, type, protocol);
    }

    private static String getClassPackage() {
        if (m_classPackage.get() == null) {
            m_classPackage.set(NativeDatagramSocket.class.getPackage().getName());
        }
        return m_classPackage.get();
    }

    private static String getClassPrefix() {
        if (m_classPrefix.get() == null) {
            m_classPrefix.set(Platform.isWindows() ? "Win32" 
                : Platform.isSolaris() ? "Sun" 
                    : (Platform.isMac() || Platform.isFreeBSD() || Platform.isOpenBSD()) ? "BSD" 
                        : "Unix");
        }
        return m_classPrefix.get();
    }

    private static String getFamilyPrefix(final int family) {
        if (AF_INET == family) {
            return "V4";
        } else if (AF_INET6 == family) {
            return "V6";
        } else {
            throw new IllegalArgumentException("Unsupported Protocol Family: "+ family);
        }
    }

    private static String getImplementationClassName(final int family) {
        return new StringBuffer(NativeDatagramSocket.getClassPackage())
                .append(".")
                .append(NativeDatagramSocket.getClassPrefix())
                .append(NativeDatagramSocket.getFamilyPrefix(family))
                .append("NativeSocket").toString();
    }

    public abstract int receive(NativeDatagramPacket p) throws IOException;
    public abstract int send(NativeDatagramPacket p) throws IOException;
    public abstract int close() throws IOException;

    protected abstract String strerror(final int error) throws LastErrorException;
    protected abstract IOException translateException(final LastErrorException e);

    public String getError(final LastErrorException e) {
        return this.strerror(e.getErrorCode());
    }

}
