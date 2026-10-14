package com.github.unidbg.linux.file;

import com.github.unidbg.Emulator;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.env.TraceEnvironmentConfig;
import com.github.unidbg.file.FileIO;
import com.sun.jna.Pointer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * AF_NETLINK SOCK_DGRAM/SOCK_RAW. Turing {@code libturingmfa_cc.so} field 129 enumerates
 * interfaces via {@code RTM_GETLINK} + {@code RTM_GETADDR} on {@code NETLINK_ROUTE}.
 * Payload comes from {@code network.interfaces} / {@code network.ipv6Addresses} when
 * the environment profile is loaded; otherwise the historical host IPv4 dump.
 */
public class NetLinkSocket extends SocketIO implements FileIO {

    private static final short NLMSG_DONE = 0x3;
    private static final short RTM_NEWLINK = 0x10;
    private static final short RTM_GETLINK = 0x12;
    private static final short RTM_NEWADDR = 0x14;
    private static final short RTM_GETADDR = 0x16;

    private static final short NLM_F_REQUEST = 0x1;
    private static final short NLM_F_MULTI = 0x2;

    private static final short IFLA_IFNAME = 3;
    private static final short IFA_ADDRESS = 1;
    private static final short IFA_LOCAL = 2;
    private static final short IFA_LABEL = 3;

    /** Profile uses this IPv4 as "no address" for virtual / down interfaces. */
    private static final String SENTINEL_IPV4 = "169.254.1.1";
    /** Linux {@code IFF_LOWER_UP}; JSON flags are 16-bit, netlink ifi_flags is 32-bit. */
    private static final int IFF_LOWER_UP = 0x10000;

    private static final int SOCKADDR_NL_LEN = 12;

    private final Emulator<?> emulator;

    private short netlinkType;
    private short netlinkFlags;
    private int netlinkSeq;
    private byte[] pending;
    private int pendingOff;

    public NetLinkSocket(Emulator<?> emulator) {
        this.emulator = emulator;
    }

    @Override
    public int write(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        int size = buffer.getInt();
        if (size < 16 || size - 4 > buffer.remaining()) {
            throw new IllegalStateException("remaining=" + buffer.remaining() + ", size=" + size);
        }
        this.netlinkType = buffer.getShort();
        this.netlinkFlags = buffer.getShort();
        this.netlinkSeq = buffer.getInt();
        pending = buildResponse();
        pendingOff = 0;
        return size;
    }

    @Override
    public int sendto(byte[] data, int flags, Pointer dest_addr, int addrlen) {
        return write(data);
    }

    @Override
    public int read(Backend backend, Pointer buffer, int count) {
        if (pending == null || pendingOff >= pending.length) {
            return 0;
        }
        int n = Math.min(count, pending.length - pendingOff);
        buffer.write(0, pending, pendingOff, n);
        pendingOff += n;
        return n;
    }

    @Override
    public int recvfrom(Backend backend, Pointer buf, int len, int flags, Pointer src_addr, Pointer addrlen) {
        int n = read(backend, buf, len);
        if (src_addr != null) {
            writeSockaddrNl(src_addr);
        }
        if (addrlen != null) {
            addrlen.setInt(0, SOCKADDR_NL_LEN);
        }
        return n;
    }

    @Override
    public int bind(Pointer addr, int addrlen) {
        return 0;
    }

    @Override
    public int getsockname(Pointer addr, Pointer addrlen) {
        if (addr != null) {
            writeSockaddrNl(addr);
        }
        if (addrlen != null) {
            addrlen.setInt(0, SOCKADDR_NL_LEN);
        }
        return 0;
    }

    private void writeSockaddrNl(Pointer addr) {
        addr.setShort(0, AF_NETLINK);
        addr.setShort(2, (short) 0);
        addr.setInt(4, emulator.getPid());
        addr.setInt(8, 0);
    }

    private byte[] buildResponse() {
        if ((netlinkFlags & NLM_F_REQUEST) == 0) {
            throw new UnsupportedOperationException("netlink flags=0x" + Integer.toHexString(netlinkFlags));
        }
        try {
            if (netlinkType == RTM_GETLINK) {
                return dumpLinks();
            }
            if (netlinkType == RTM_GETADDR) {
                return dumpAddrs();
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        throw new UnsupportedOperationException("netlinkType=0x" + Integer.toHexString(netlinkType));
    }

    private byte[] dumpLinks() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config != null && config.isNetworkInterfacesConfigured()) {
            for (TraceEnvironmentConfig.NetworkInterfaceConfig iface : config.getNetworkInterfaces()) {
                int flags = netlinkIfiFlags(iface.getFlags() == null ? 0 : iface.getFlags().intValue());
                int hw = iface.isHardwareTypeConfigured() && iface.getHardwareType() != null
                        ? iface.getHardwareType().intValue() : 1;
                baos.write(newLink(iface.getName(), iface.getIndex(), flags, hw));
            }
        } else {
            for (NetworkIF networkIF : getNetworkIFs(emulator)) {
                int flags = networkIF.configuredFlags >= 0
                        ? networkIF.configuredFlags
                        : (IFF_UP | IFF_RUNNING | IFF_BROADCAST | IFF_MULTICAST);
                baos.write(newLink(networkIF.ifName, networkIF.index, netlinkIfiFlags(flags), 1));
            }
        }
        baos.write(doneMessage());
        return baos.toByteArray();
    }

    private byte[] dumpAddrs() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        TraceEnvironmentConfig config = TraceEnvironmentConfig.get(emulator);
        if (config != null && config.isNetworkInterfacesConfigured()) {
            for (TraceEnvironmentConfig.NetworkInterfaceConfig iface : config.getNetworkInterfaces()) {
                if (iface.getIpv4() != null && !SENTINEL_IPV4.equals(iface.getIpv4())) {
                    byte[] ipv4 = parseIpv4(iface.getIpv4());
                    baos.write(newAddr(iface.getName(), iface.getIndex(), AF_INET, (byte) 24,
                            (byte) 0, ipv4));
                }
                if (config.isNetworkIpv6AddressesConfigured()) {
                    for (TraceEnvironmentConfig.NetworkIpv6AddressConfig v6 : config.getNetworkIpv6Addresses()) {
                        if (!iface.getName().equals(v6.getInterfaceName())) {
                            continue;
                        }
                        baos.write(newAddr(iface.getName(), iface.getIndex(), AF_INET6,
                                (byte) v6.getPrefixLength(), (byte) v6.getScope(),
                                parseIpv6Hex(v6.getAddressHex())));
                    }
                }
            }
        } else {
            List<NetworkIF> list = getNetworkIFs(emulator);
            for (NetworkIF networkIF : list) {
                baos.write(newAddr(networkIF.ifName, networkIF.index, AF_INET, (byte) 24,
                        (byte) 0, networkIF.ipv4.getAddress()));
            }
        }
        baos.write(doneMessage());
        return baos.toByteArray();
    }

    /** Kernel netlink reports IFF_LOWER_UP on up+running interfaces. */
    private static int netlinkIfiFlags(int flags) {
        if ((flags & (IFF_UP | IFF_RUNNING)) == (IFF_UP | IFF_RUNNING)) {
            flags |= IFF_LOWER_UP;
        }
        return flags;
    }

    private byte[] newLink(String name, int index, int flags, int hardwareType) {
        byte[] ifname = (name + '\0').getBytes(StandardCharsets.UTF_8);
        ByteBuffer bb = ByteBuffer.allocate(256);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        int start = bb.position();
        bb.putInt(0);
        bb.putShort(RTM_NEWLINK);
        bb.putShort(NLM_F_MULTI);
        bb.putInt(netlinkSeq);
        bb.putInt(emulator.getPid());
        bb.put((byte) AF_UNSPEC); // ifi_family
        bb.put((byte) 0);
        bb.putShort((short) hardwareType);
        bb.putInt(index);
        bb.putInt(flags);
        bb.putInt(0xffffffff);
        putRta(bb, IFLA_IFNAME, ifname);
        return finishNlMsg(bb, start);
    }

    private byte[] newAddr(String name, int index, short family, byte prefixLen, byte scope, byte[] address) {
        byte[] label = (name + '\0').getBytes(StandardCharsets.UTF_8);
        ByteBuffer bb = ByteBuffer.allocate(256);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        int start = bb.position();
        bb.putInt(0);
        bb.putShort(RTM_NEWADDR);
        bb.putShort(NLM_F_MULTI);
        bb.putInt(netlinkSeq);
        bb.putInt(emulator.getPid());
        bb.put((byte) family);
        bb.put(prefixLen);
        bb.put((byte) 0); // ifa_flags
        bb.put(scope);
        bb.putInt(index);
        putRta(bb, IFA_ADDRESS, address);
        if (family == AF_INET) {
            putRta(bb, IFA_LOCAL, address);
        }
        putRta(bb, IFA_LABEL, label);
        return finishNlMsg(bb, start);
    }

    private byte[] doneMessage() {
        ByteBuffer bb = ByteBuffer.allocate(20);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(20);
        bb.putShort(NLMSG_DONE);
        bb.putShort((short) 0);
        bb.putInt(netlinkSeq);
        bb.putInt(emulator.getPid());
        bb.putInt(0);
        return bb.array();
    }

    private static void putRta(ByteBuffer bb, short type, byte[] data) {
        int unaligned = 4 + data.length;
        bb.putShort((short) unaligned);
        bb.putShort(type);
        bb.put(data);
        int aligned = (unaligned + 3) & ~3;
        for (int i = unaligned; i < aligned; i++) {
            bb.put((byte) 0);
        }
    }

    private static byte[] finishNlMsg(ByteBuffer bb, int start) {
        int end = bb.position();
        int aligned = (end + 3) & ~3;
        while (bb.position() < aligned) {
            bb.put((byte) 0);
        }
        int len = bb.position() - start;
        bb.putInt(start, len);
        byte[] out = new byte[len];
        System.arraycopy(bb.array(), start, out, 0, len);
        return out;
    }

    private static byte[] parseIpv4(String text) {
        try {
            String[] parts = text.split("\\.", -1);
            byte[] bytes = new byte[4];
            for (int i = 0; i < 4; i++) {
                bytes[i] = (byte) Integer.parseInt(parts[i]);
            }
            InetAddress addr = InetAddress.getByAddress(bytes);
            if (!(addr instanceof Inet4Address)) {
                throw new IllegalStateException(text);
            }
            return bytes;
        } catch (Exception e) {
            throw new IllegalStateException("ipv4 " + text, e);
        }
    }

    private static byte[] parseIpv6Hex(String hex) {
        if (hex == null || hex.length() != 32) {
            throw new IllegalStateException("ipv6 hex " + hex);
        }
        byte[] out = new byte[16];
        for (int i = 0; i < 16; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    @Override
    protected int getTcpNoDelay() {
        return 0;
    }

    @Override
    protected void setTcpNoDelay(int tcpNoDelay) {
    }

    @Override
    protected void setReuseAddress(int reuseAddress) {
    }

    @Override
    protected void setKeepAlive(int keepAlive) {
    }

    @Override
    protected void setSendBufferSize(int size) {
    }

    @Override
    protected void setReceiveBufferSize(int size) {
    }

    @Override
    protected InetSocketAddress getLocalSocketAddress() {
        throw new UnsupportedOperationException(getClass().getName());
    }

    @Override
    protected int connect_ipv6(Pointer addr, int addrlen) {
        throw new UnsupportedOperationException(getClass().getName());
    }

    @Override
    protected int connect_ipv4(Pointer addr, int addrlen) {
        throw new UnsupportedOperationException(getClass().getName());
    }

    @Override
    public void close() {
        netlinkType = 0;
        netlinkFlags = 0;
        pending = null;
        pendingOff = 0;
    }
}
