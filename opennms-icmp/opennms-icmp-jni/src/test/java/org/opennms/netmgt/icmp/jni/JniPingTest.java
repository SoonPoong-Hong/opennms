/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2007-2017 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.icmp.jni;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;
import java.net.NoRouteToHostException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.junit.Before;
import org.junit.Test;
import org.opennms.core.test.MockLogAppender;
import org.opennms.core.utils.CollectionMath;
import org.opennms.core.utils.InetAddressUtils;
import org.opennms.netmgt.icmp.EchoPacket;
import org.opennms.netmgt.icmp.PingConstants;
import org.opennms.netmgt.icmp.PingResponseCallback;
import org.opennms.netmgt.icmp.Pinger;
import org.springframework.test.annotation.IfProfileValue;

/**
 * @author <a href="mailto:ranger@opennms.org">Ben Reed</a>
 */
@IfProfileValue(name="runPingTests", value="true")
public class JniPingTest {
    private static final JniPinger s_jniPinger = new JniPinger();

    private InetAddress m_goodHost = InetAddressUtils.getLocalHostAddress();

    // 192.0.2.0/24 is reserved for documentation purposes
    private InetAddress m_badHost = InetAddressUtils.addr("192.0.2.123");

    @Before
    public void setUp() throws Exception {
        MockLogAppender.setupLogging();
    }

    @Test
    public void testSinglePingJni() throws Exception {
        doSinglePingSuccess(s_jniPinger);
    }

    @Test
    public void testPingCallbackTimeoutJni() throws Exception {
        pingCallbackTimeout(s_jniPinger);
    }

    @Test
    public void testSinglePingFailureJni() throws Exception {
        try {
            doSinglePingFailure(s_jniPinger);
        } catch (NoRouteToHostException ex) {
            // this is a possible option if the OS knows that this is impossible
        }
    }

    @Test
    public void testParallelPingJni() throws Exception {
        doParallelPingSuccess(s_jniPinger);
    }

    @Test
    public void testParallelPingFailureJni() throws Exception {
        doParallelPingFailure(s_jniPinger);
    }

    protected void doSinglePingSuccess(final Pinger pinger) throws Exception {
        final Number rtt = pinger.ping(m_goodHost);
        assertNotNull("No RTT value returned from ping, looks like the ping failed", rtt);
        assertTrue("Negative RTT value returned from ping", rtt.doubleValue() > 0);
    }

    protected void doSinglePingFailure(final Pinger pinger) throws Exception {
        assertNull(pinger.ping(m_badHost));
    }

    protected void doParallelPingSuccess(final Pinger pinger) throws Exception {
        final List<Number> items = pinger.parallelPing(m_goodHost, 20, PingConstants.DEFAULT_TIMEOUT, 50);
        printResponse(items);
        assertTrue("Collection contained all null values, all parallel pings failed", CollectionMath.countNotNull(items) > 0);
        for (final Number item : items) {
            assertNotNull("Found a null reponse time in the response", item);
            assertTrue("Negative RTT value returned from ping", item.floatValue() > 0);
        }
    }

    protected void doParallelPingFailure(final Pinger pinger) throws Exception {
        final List<Number> items = pinger.parallelPing(m_badHost, 20, PingConstants.DEFAULT_TIMEOUT, 50);
        printResponse(items);
        assertTrue("Collection contained some numeric values when all parallel pings should have failed", CollectionMath.countNotNull(items) == 0);
    }

    protected void pingCallbackTimeout(final Pinger pinger) throws Exception {
        final TestPingResponseCallback cb = new TestPingResponseCallback();

        pinger.ping(m_badHost, PingConstants.DEFAULT_TIMEOUT, PingConstants.DEFAULT_RETRIES, PingConstants.DEFAULT_PACKET_SIZE, 1, cb);

        cb.await();

        assertTrue("Unexpected Error sending ping to " + m_badHost + ": " + cb.getThrowable(), 
                   cb.getThrowable() == null || cb.getThrowable() instanceof NoRouteToHostException);
        assertTrue(cb.isTimeout());
        assertNotNull(cb.getPacket());
        assertNotNull(cb.getAddress());

    }

    private void printResponse(final List<Number> items) {
        final Long passed = CollectionMath.countNotNull(items);
        final Long failed = CollectionMath.countNull(items);
        Number passedPercent = CollectionMath.percentNotNull(items);
        Number failedPercent = CollectionMath.percentNull(items);
        Number average = CollectionMath.average(items);
        Number median = CollectionMath.median(items);

        if (passedPercent == null) passedPercent = Long.valueOf(0);
        if (failedPercent == null) failedPercent = Long.valueOf(100);
        if (median        == null) median        = Double.valueOf(0);

        if (average == null) {
            average = new Double(0);
        } else {
            average = new Double(average.doubleValue() / 1000.0);
        }

        final StringBuffer sb = new StringBuffer();
        sb.append("response times = ").append(items);
        sb.append("\n");

        sb.append("pings = ").append(items.size());
        sb.append(", passed = ").append(passed).append(" (").append(passedPercent).append("%)");
        sb.append(", failed = ").append(failed).append(" (").append(failedPercent).append("%)");
        sb.append(", median = ").append(median);
        sb.append(", average = ").append(average).append("ms");
        sb.append("\n");
        System.out.print(sb);
    }

    private static class TestPingResponseCallback implements PingResponseCallback {
        private final CountDownLatch m_latch = new CountDownLatch(1);
        private InetAddress m_address;
        private EchoPacket m_packet;
        private Throwable m_throwable;
        private boolean m_timeout = false;

        @Override
        public void handleResponse(final InetAddress address, final EchoPacket response) {
            m_address = address;
            m_packet = response;
            m_latch.countDown();
            System.err.println("RESPONSE COUNTED DOWN");
        }

        @Override
        public void handleTimeout(final InetAddress address, final EchoPacket request) {
            m_timeout = true;
            m_address = address;
            m_packet = request;
            m_latch.countDown();
            System.err.println("TIMEOUT COUNTED DOWN");
        }

        @Override
        public void handleError(final InetAddress address, final EchoPacket request, final Throwable t) {
            m_address = address;
            m_packet = request;
            m_throwable = t;
            m_latch.countDown();
            System.err.println("ERROR COUNTED DOWN");
            t.printStackTrace();
        }

        public void await() throws InterruptedException {
            m_latch.await();
        }

        public InetAddress getAddress() {
            return m_address;
        }

        public EchoPacket getPacket() {
            return m_packet;
        }

        public Throwable getThrowable() {
            return m_throwable;
        }

        public boolean isTimeout() {
            return m_timeout;
        }
    };
}

