#!/usr/bin/env python3
"""
Cliente de prueba para el servidor DNS del Lab03.
Uso:  python3 test_dns.py <nombre> <TIPO> [puerto]
Ej:   python3 test_dns.py example.local A 5353
      python3 test_dns.py 10.1.168.192.in-addr.arpa PTR 5353
      python3 test_dns.py google.com A 5353
"""
import socket, struct, sys, random

TYPES = {"A": 1, "NS": 2, "CNAME": 5, "SOA": 6, "PTR": 12, "AAAA": 28}
RTYPES = {v: k for k, v in TYPES.items()}


def encode_name(name):
    out = b""
    for label in name.split("."):
        if label:
            out += bytes([len(label)]) + label.encode()
    return out + b"\x00"


def build_query(name, qtype, rd=True):
    tid = random.randint(0, 65535)
    flags = 0x0100 if rd else 0x0000          # RD = recursion deseada
    header = struct.pack(">HHHHHH", tid, flags, 1, 0, 0, 0)
    q = encode_name(name) + struct.pack(">HH", TYPES[qtype], 1)
    return tid, header + q


def read_name(data, off):
    labels, jumped, nxt = [], False, off
    while True:
        l = data[off]
        if (l & 0xC0) == 0xC0:                  # puntero de compresion
            ptr = ((l & 0x3F) << 8) | data[off + 1]
            if not jumped:
                nxt = off + 2
            off, jumped = ptr, True
            continue
        if l == 0:
            if not jumped:
                nxt = off + 1
            break
        off += 1
        labels.append(data[off:off + l].decode(errors="replace"))
        off += l
    return ".".join(labels), nxt


def parse(data):
    tid, flags, qd, an, ns, ar = struct.unpack(">HHHHHH", data[:12])
    rcode = flags & 0x0F
    aa = (flags >> 10) & 1
    off = 12
    for _ in range(qd):
        _, off = read_name(data, off)
        off += 4
    answers = []
    for _ in range(an):
        name, off = read_name(data, off)
        rtype, rclass, ttl, rdlen = struct.unpack(">HHIH", data[off:off + 10])
        off += 10
        rdata = data[off:off + rdlen]
        off += rdlen
        if rtype == 1:
            val = socket.inet_ntop(socket.AF_INET, rdata)
        elif rtype == 28:
            val = socket.inet_ntop(socket.AF_INET6, rdata)
        elif rtype == 12:
            val, _ = read_name(data, off - rdlen)
        else:
            val = rdata.hex()
        answers.append((RTYPES.get(rtype, rtype), ttl, val))
    return rcode, aa, an, answers


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(1)
    name, qtype = sys.argv[1], sys.argv[2].upper()
    port = int(sys.argv[3]) if len(sys.argv) > 3 else 53
    if qtype not in TYPES:
        print("Tipo no soportado. Usa: " + ", ".join(TYPES))
        sys.exit(1)

    tid, pkt = build_query(name, qtype)
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.settimeout(5)
    s.sendto(pkt, ("127.0.0.1", port))
    resp, _ = s.recvfrom(65535)

    rcode, aa, an, answers = parse(resp)
    RC = {0: "NOERROR", 1: "FORMERR", 2: "SERVFAIL", 3: "NXDOMAIN"}
    print(f"QUERY: {qtype} {name}  ->  status={RC.get(rcode, rcode)} "
          f"AA={aa} ANSWERS={an}")
    for t, ttl, v in answers:
        print(f"  {name}. {ttl} IN {t} {v}")


if __name__ == "__main__":
    main()
