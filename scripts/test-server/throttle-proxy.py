#!/usr/bin/env python3
"""A pass-through proxy to the local test server (localhost:8196) that sends the bodies of /Download answers at about RATE bytes a second,
so that a download lasts long enough to turn the screen off, drop the network or kill the app in the middle of it.

    scripts/test-server/throttle-proxy.py <listen port> <bytes per second>      e.g. 8197 600000 (the big-download fixture takes ~8 min)

Add http://10.0.2.2:<port> as the server's address on the emulator (the app tries the best address first). Stop it with Ctrl-C."""
import http.client, http.server, socketserver, sys, time

UP_HOST, UP_PORT, LISTEN, RATE = "localhost", 8196, int(sys.argv[1]), int(sys.argv[2])
CHUNK = 64 * 1024
HOP = {"transfer-encoding", "connection", "keep-alive", "proxy-connection", "te", "trailers", "upgrade"}


class Handler(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *args):
        pass

    def do_GET(self):
        self.forward()

    do_POST = do_DELETE = do_PUT = do_HEAD = do_GET

    def forward(self):
        body = self.rfile.read(int(self.headers.get("Content-Length") or 0)) or None
        headers = {k: v for k, v in self.headers.items() if k.lower() not in HOP | {"host"}}
        conn = http.client.HTTPConnection(UP_HOST, UP_PORT, timeout=60)
        conn.request(self.command, self.path, body, headers)
        resp = conn.getresponse()
        self.send_response(resp.status)
        for k, v in resp.getheaders():
            if k.lower() not in HOP:
                self.send_header(k, v)
        self.send_header("Connection", "close")
        self.end_headers()
        self.close_connection = True
        slow = "/Download" in self.path
        try:
            while True:
                chunk = resp.read(CHUNK)
                if not chunk:
                    break
                self.wfile.write(chunk)
                if slow:
                    time.sleep(len(chunk) / RATE)
        except (BrokenPipeError, ConnectionResetError):
            pass
        finally:
            conn.close()


class Server(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True


Server(("0.0.0.0", LISTEN), Handler).serve_forever()
