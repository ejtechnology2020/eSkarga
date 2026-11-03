"""Minimalny serwer RFC 3161 (application/timestamp-query → application/timestamp-reply) na OpenSSL ts. Tylko dev."""
import subprocess, tempfile, os
from http.server import BaseHTTPRequestHandler, HTTPServer

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/tsa.crt':
            data = open('/tsa/data/tsa.crt', 'rb').read()
            self.send_response(200); self.send_header('Content-Type', 'application/x-pem-file'); self.send_header('Content-Length', str(len(data))); self.end_headers(); self.wfile.write(data)
        else:
            self.send_response(200); self.send_header('Content-Type', 'text/plain'); self.end_headers(); self.wfile.write(b'eSkarga dev TSA: POST application/timestamp-query\n')

    def do_POST(self):
        length = int(self.headers.get('Content-Length', '0'))
        query = self.rfile.read(length)
        with tempfile.NamedTemporaryFile(delete=False, suffix='.tsq') as f:
            f.write(query); qpath = f.name
        try:
            reply = subprocess.run(['openssl', 'ts', '-reply', '-queryfile', qpath, '-config', '/tsa/openssl-tsa.cnf'], capture_output=True, timeout=30)
            if reply.returncode != 0:
                self.send_response(500); self.end_headers(); self.wfile.write(reply.stderr); return
            self.send_response(200); self.send_header('Content-Type', 'application/timestamp-reply'); self.send_header('Content-Length', str(len(reply.stdout))); self.end_headers(); self.wfile.write(reply.stdout)
        finally:
            os.unlink(qpath)

    def log_message(self, fmt, *args):
        pass

HTTPServer(('0.0.0.0', 3180), Handler).serve_forever()
