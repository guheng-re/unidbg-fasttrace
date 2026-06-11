package com.github.unidbg.linux.file;

import com.github.unidbg.Emulator;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.file.linux.BaseAndroidFileIO;
import com.github.unidbg.file.linux.IOConstants;
import com.github.unidbg.linux.struct.IFConf;
import com.github.unidbg.linux.struct.IFReq;
import com.github.unidbg.pointer.UnidbgPointer;
import com.github.unidbg.unix.IO;
import com.github.unidbg.unix.struct.SockAddr;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;

public abstract class SocketIO extends BaseAndroidFileIO implements AndroidFileIO {

    private static final Logger log = LoggerFactory.getLogger(SocketIO.class);

    public static final short AF_UNSPEC = 0;
    public static final short AF_LOCAL = 1; // AF_UNIX
    public static final short AF_INET = 2;
    public static final short AF_INET6 = 10;
    public static final short AF_NETLINK = 16;
    public static final short AF_ROUTE = 17;		/* Internal Routing Protocol */
    public static final short AF_LINK =		18;		/* Link layer interface */

    protected static final int IPV4_ADDR_LEN = 16;
    protected static final int IPV6_ADDR_LEN = 28;

    public static final int SOCK_STREAM = 1;
    public static final int SOCK_DGRAM = 2;
    public static final int SOCK_RAW = 3;
    public static final int SOCK_SEQPACKET = 5;

    private static final int IPPROTO_IP = 0;
    public static final int IPPROTO_ICMP = 1;
    public static final int IPPROTO_TCP = 6;

    protected static final int SOL_SOCKET = 1;

    private static final int SO_REUSEADDR = 2;
    private static final int SO_ERROR = 4;
    private static final int SO_BROADCAST = 6;
    private static final int SO_SNDBUF = 7;
    private static final int SO_RCVBUF = 8;
    private static final int SO_KEEPALIVE = 9;
    private static final int SO_RCVTIMEO = 20;
    private static final int SO_SNDTIMEO = 21;
    protected static final int SO_PEERSEC = 31;

    static final int SHUT_RD = 0;
    static final int SHUT_WR = 1;
    static final int SHUT_RDWR = 2;

    private static final int TCP_NODELAY = 1;
    private static final int TCP_MAXSEG = 2;

    static final int MSG_PEEK = 0x02; /* Peek at incoming messages. */
    protected static final int MSG_NOSIGNAL = 0x4000; /* Do not generate SIGPIPE. */

    public static short IFF_UP = 0x1; /* interface is up		*/
    public static short IFF_BROADCAST = 0x2;		/* broadcast address valid	*/
    public static short IFF_LOOPBACK = 0x8;		/* is a loopback net		*/
    public static short IFF_RUNNING = 0x40; /* interface RFC2863 OPER_UP	*/
    public static short IFF_NOARP = 0x80; /* no ARP protocol		*/
    public static short IFF_MULTICAST = 0x1000;		/* Supports multicast		*/

    protected SocketIO() {
        super(IOConstants.O_RDWR);
    }

    @Override
    public int ioctl(Emulator<?> emulator, long request, long argp) {
        if (request == SIOCGIFCONF) {
            return getIFaceList(emulator, argp);
        }
        if (request == SIOCGIFFLAGS) {
            return getIFaceFlags(emulator, argp);
        }
        if (request == SIOCGIFNAME) {
            return getIFaceName(emulator, argp);
        }
        if (request == SIOCGIFADDR) {
            return getIFaceAddr(emulator, argp);
        }
        return super.ioctl(emulator, request, argp);
    }

    protected List<NetworkIF> getNetworkIFs(Emulator<?> emulator) throws SocketException {
        Enumeration<NetworkInterface> enumeration = NetworkInterface.getNetworkInterfaces();
        List<NetworkIF> list = new ArrayList<>();
        while (enumeration.hasMoreElements()) {
            NetworkInterface networkInterface = enumeration.nextElement();
            Enumeration<InetAddress> addressEnumeration = networkInterface.getInetAddresses();
            while (addressEnumeration.hasMoreElements()) {
                InetAddress address = addressEnumeration.nextElement();
                if (address instanceof Inet4Address) {
                    Inet4Address broadcast = null;
                    for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                        if (interfaceAddress.getBroadcast() != null) {
                            broadcast = (Inet4Address) interfaceAddress.getBroadcast();
                            break;
                        }
                    }
                    list.add(new NetworkIF(networkInterface.getIndex(), networkInterface.getName(), (Inet4Address) address, broadcast));
                    break;
                }
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("Return host network ifs: {}", list);
        }
        if (emulator.getSyscallHandler().isVerbose()) {
            System.out.println(getClass().getSimpleName() + " return host network ifs: " + list + " from " + emulator.getContext().getLRPointer());
        }
        return list;
    }

    private int getIFaceAddr(Emulator<?> emulator, long argp) {
        IFReq req = IFReq.createIFReq(emulator, UnidbgPointer.pointer(emulator, argp));
        req.unpack();
        String ifName = new String(req.ifrn_name).trim();
        if (log.isDebugEnabled()) {
            log.debug("get iface addr: {}", ifName);
        }
        try {
            for (NetworkIF networkIF : getNetworkIFs(emulator)) {
                if (ifName.equals(networkIF.ifName)) {
                    SockAddr sockAddr = new SockAddr(req.getAddrPointer());
                    sockAddr.sin_family = AF_INET;
                    sockAddr.sin_port = 0;
                    sockAddr.sin_addr = Arrays.copyOf(networkIF.ipv4.getAddress(), IPV4_ADDR_LEN - 4);
                    sockAddr.pack();
                    return 0;
                }
            }
        } catch (SocketException e) {
            throw new IllegalStateException(e);
        }
        if (log.isDebugEnabled()) {
            log.debug("getIFaceAddr not found: {}", ifName);
        }
        emulator.getMemory().setErrno(19); // ENODEV
        return -1;
    }

    private int getIFaceList(Emulator<?> emulator, long argp) {
        try {
            List<NetworkIF> list = getNetworkIFs(emulator);
            IFConf conf = IFConf.create(emulator, UnidbgPointer.pointer(emulator, argp));
            Pointer ifcu_req = UnidbgPointer.pointer(emulator, conf.getIfcuReq());
            IFReq ifReq = IFReq.createIFReq(emulator, ifcu_req);
            if (list.size() * ifReq.size() > conf.ifc_len) {
                throw new BufferOverflowException();
            }

            conf.ifc_len = list.size() * ifReq.size();
            conf.pack();

            Pointer pointer = Objects.requireNonNull(ifcu_req);
            for (NetworkIF networkIF : list) {
                ifReq = IFReq.createIFReq(emulator, pointer);
                ifReq.setName(networkIF.ifName);
                ifReq.pack();

                SockAddr sockAddr = new SockAddr(ifReq.getAddrPointer());
                sockAddr.sin_family = AF_INET;
                sockAddr.sin_port = 0;
                sockAddr.sin_addr = Arrays.copyOf(networkIF.ipv4.getAddress(), IPV4_ADDR_LEN - 4);
                sockAddr.pack();

                pointer = pointer.share(ifReq.size());
            }

            return 0;
        } catch (SocketException e) {
            throw new IllegalStateException(e);
        }
    }

    protected int getIFaceFlags(Emulator<?> emulator, long argp) {
        IFReq req = IFReq.createIFReq(emulator, UnidbgPointer.pointer(emulator, argp));
        req.unpack();
        String ifName = new String(req.ifrn_name).trim();
        if (log.isDebugEnabled()) {
            log.debug("get iface flags: {}", ifName);
        }
        NetworkIF selected = null;
        try {
            for (NetworkIF networkIF : getNetworkIFs(emulator)) {
                if (ifName.equals(networkIF.ifName)) {
                    selected = networkIF;
                    break;
                }
            }
        } catch (SocketException e) {
            throw new IllegalStateException(e);
        }
        int flags;
        if (selected == null) {
            flags = getFallbackInterfaceFlags(ifName);
        } else {
            flags = IFF_UP | IFF_RUNNING;
            if (selected.isLoopback()) {
                flags |= IFF_LOOPBACK;
            } else if (selected.broadcast != null) {
                flags |= IFF_BROADCAST;
                flags |= IFF_MULTICAST;
            }
        }
        Pointer ptr = req.getAddrPointer();
        ptr.setShort(0, (short) flags);
        return 0;
    }

    protected int getFallbackInterfaceFlags(String ifName) {
        int flags = IFF_UP | IFF_RUNNING;
        if (ifName.startsWith("lo")) {
            flags |= IFF_LOOPBACK;
        } else {
            flags |= IFF_BROADCAST;
            flags |= IFF_MULTICAST;
        }
        return flags;
    }

    protected int getIFaceName(Emulator<?> emulator, long argp) {
        IFReq req = IFReq.createIFReq(emulator, UnidbgPointer.pointer(emulator, argp));
        Pointer ptr = req.getAddrPointer();
        int ifindex = ptr.getInt(0);
        if (log.isDebugEnabled()) {
            log.debug("get iface name: {}", ifindex);
        }
        try {
            List<NetworkIF> list = getNetworkIFs(emulator);
            for (NetworkIF networkIF : list) {
                if (ifindex == networkIF.index) {
                    req.setName(networkIF.ifName);
                    req.pack();
                    return 0;
                }
            }
            throw new IllegalStateException("ifindex=" + ifindex);
        } catch (SocketException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int getsockopt(int level, int optname, Pointer optval, Pointer optlen) {
        try {
            switch (level) {
                case SOL_SOCKET:
                    if (optname == SO_ERROR) {
                        optlen.setInt(0, 4);
                        optval.setInt(0, 0);
                        return 0;
                    }
                    break;
                case IPPROTO_TCP:
                    if (optname == TCP_NODELAY) {
                        optlen.setInt(0, 4);
                        optval.setInt(0, getTcpNoDelay());
                        return 0;
                    }
                    break;
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return super.getsockopt(level, optname, optval, optlen);
    }

    protected abstract int getTcpNoDelay() throws SocketException;

    @Override
    public int setsockopt(int level, int optname, Pointer optval, int optlen) {
        try {
            switch (level) {
                case SOL_SOCKET:
                    switch (optname) {
                        case SO_REUSEADDR:
                            if (optlen != 4) {
                                throw new IllegalStateException("optlen=" + optlen);
                            }
                            setReuseAddress(optval.getInt(0));
                            return 0;
                        case SO_BROADCAST:
                            if (optlen != 4) {
                                throw new IllegalStateException("optlen=" + optlen);
                            }
                            optval.getInt(0); // broadcast_pings
                            return 0;
                        case SO_SNDBUF:
                            if (optlen != 4) {
                                throw new IllegalStateException("optlen=" + optlen);
                            }
                            setSendBufferSize(optval.getInt(0));
                            return 0;
                        case SO_RCVBUF:
                            if (optlen != 4) {
                                throw new IllegalStateException("optlen=" + optlen);
                            }
                            setReceiveBufferSize(optval.getInt(0));
                            return 0;
                        case SO_KEEPALIVE:
                            if (optlen != 4) {
                                throw new IllegalStateException("optlen=" + optlen);
                            }
                            setKeepAlive(optval.getInt(0));
                            return 0;
                        case SO_RCVTIMEO:
                        case SO_SNDTIMEO: {
                            return 0;
                        }
                    }
                    break;
                case IPPROTO_TCP:
                    switch (optname) {
                        case TCP_NODELAY:
                            if (optlen != 4) {
                                throw new IllegalStateException("optlen=" + optlen);
                            }
                            setTcpNoDelay(optval.getInt(0));
                            return 0;
                        case TCP_MAXSEG:
                            if (optlen != 4) {
                                throw new IllegalStateException("optlen=" + optlen);
                            }
                            log.debug("setsockopt TCP_MAXSEG={}", optval.getInt(0));
                            return 0;
                    }
                    break;
                case IPPROTO_IP:
                    return 0;
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        log.warn("setsockopt level={}, optname={}, optval={}, optlen={}", level, optname, optval, optlen);
        return 0;
    }

    protected abstract void setTcpNoDelay(int tcpNoDelay) throws SocketException;

    protected abstract void setReuseAddress(int reuseAddress) throws SocketException;

    protected abstract void setKeepAlive(int keepAlive) throws SocketException;

    protected abstract void setSendBufferSize(int size) throws SocketException;

    protected abstract void setReceiveBufferSize(int size) throws SocketException;

    @Override
    public int getsockname(Pointer addr, Pointer addrlen) {
        InetSocketAddress local = getLocalSocketAddress();
        fillAddress(local, addr, addrlen);
        return 0;
    }

    protected final void fillAddress(InetSocketAddress socketAddress, Pointer addr, Pointer addrlen) {
        InetAddress address = socketAddress.getAddress();
        SockAddr sockAddr = new SockAddr(addr);
        sockAddr.sin_port = (short) socketAddress.getPort();
        if (address instanceof Inet4Address) {
            sockAddr.sin_family = AF_INET;
            sockAddr.sin_addr = Arrays.copyOf(address.getAddress(), IPV4_ADDR_LEN - 4);
            addrlen.setInt(0, IPV4_ADDR_LEN);
        } else if (address instanceof Inet6Address) {
            sockAddr.sin_family = AF_INET6;
            sockAddr.sin_addr = Arrays.copyOf(address.getAddress(), IPV6_ADDR_LEN - 4);
            addrlen.setInt(0, IPV6_ADDR_LEN);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    protected abstract InetSocketAddress getLocalSocketAddress();

    @Override
    public int connect(Pointer addr, int addrlen) {
        if (addrlen == IPV4_ADDR_LEN) {
            return connect_ipv4(addr, addrlen);
        } else if(addrlen == IPV6_ADDR_LEN) {
            return connect_ipv6(addr, addrlen);
        } else {
            throw new IllegalStateException("addrlen=" + addrlen);
        }
    }

    @Override
    public int bind(Pointer addr, int addrlen) {
        if (addrlen == IPV4_ADDR_LEN) {
            return bind_ipv4(addr, addrlen);
        } else if(addrlen == IPV6_ADDR_LEN) {
            return bind_ipv6(addr, addrlen);
        } else {
            throw new IllegalStateException("addrlen=" + addrlen);
        }
    }

    protected abstract int connect_ipv6(Pointer addr, int addrlen);

    protected abstract int connect_ipv4(Pointer addr, int addrlen);

    protected int bind_ipv6(Pointer addr, int addrlen) {
        throw new AbstractMethodError(getClass().getName());
    }

    protected int bind_ipv4(Pointer addr, int addrlen) {
        throw new AbstractMethodError(getClass().getName());
    }

    @Override
    public int recvfrom(Backend backend, Pointer buf, int len, int flags, Pointer src_addr, Pointer addrlen) {
        if (flags == 0x0 && src_addr == null && addrlen == null) {
            return read(backend, buf, len);
        }

        return super.recvfrom(backend, buf, len, flags, src_addr, addrlen);
    }

    @Override
    public int sendto(byte[] data, int flags, Pointer dest_addr, int addrlen) {
        flags &= ~MSG_NOSIGNAL;

        if (flags == 0x0 && dest_addr == null && addrlen == 0) {
            return write(data);
        }

        return super.sendto(data, flags, dest_addr, addrlen);
    }

    @Override
    public int fstat(Emulator<?> emulator, com.github.unidbg.file.linux.StatStructure stat) {
        stat.st_dev = 0;
        stat.st_mode = IO.S_IFSOCK;
        stat.st_uid = 0;
        stat.st_gid = 0;
        stat.st_size = 0;
        stat.st_blksize = 0;
        stat.st_ino = 0;
        stat.pack();
        return 0;
    }

    @Override
    public int getdents64(Pointer dirp, int size) {
        throw new UnsupportedOperationException();
    }
}
