"""验证两端真实 Nginx 代理保留浏览器 Host、Origin 和认证头。

运行：python3 -m unittest discover -s tools -p 'test_nginx_proxy.py'
需要 nginx 可执行文件，可用 NGINX_BIN 指定；所有服务与文件仅用于本次测试。
"""

import http.client
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import threading
import time
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


ROOT = Path(__file__).resolve().parents[1]
NGINX = os.environ.get("NGINX_BIN") or shutil.which("nginx")


class EchoHeaders(BaseHTTPRequestHandler):
    def do_GET(self):
        body = json.dumps(dict(self.headers)).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *_):
        pass


@unittest.skipUnless(NGINX, "需要 nginx 或 NGINX_BIN，未验证真实代理")
class NginxProxyTest(unittest.TestCase):
    def test_front_preserves_browser_headers(self):
        self.check_proxy("homestay-front", 8088)

    def test_admin_preserves_browser_headers(self):
        self.check_proxy("homestay-admin", 8089)

    def check_proxy(self, app, external_port):
        upstream = ThreadingHTTPServer(("127.0.0.1", 0), EchoHeaders)
        thread = threading.Thread(target=upstream.serve_forever, daemon=True)
        thread.start()
        try:
            with tempfile.TemporaryDirectory(prefix="homestay-nginx-test-") as directory:
                with socket.socket() as reservation:
                    reservation.bind(("127.0.0.1", 0))
                    port = reservation.getsockname()[1]
                # 仅替换监听地址和独立测试上游，其余均使用仓库的实际配置。
                config = (ROOT / app / "nginx.conf").read_text()
                config = config.replace("listen 80;", f"listen 127.0.0.1:{port};")
                config = config.replace("http://backend:8081", f"http://127.0.0.1:{upstream.server_port}")
                path = Path(directory) / "nginx.conf"
                path.write_text(
                    f"daemon off;\nmaster_process off;\npid {directory}/nginx.pid;\n"
                    f"error_log {directory}/error.log;\nevents {{}}\n"
                    f"http {{ access_log off; client_body_temp_path {directory}/body; "
                    f"proxy_temp_path {directory}/proxy; fastcgi_temp_path {directory}/fastcgi; "
                    f"uwsgi_temp_path {directory}/uwsgi; scgi_temp_path {directory}/scgi; {config} }}\n"
                )
                process = subprocess.Popen(
                    [NGINX, "-p", directory + "/", "-c", str(path)],
                    stdout=subprocess.DEVNULL, stderr=subprocess.PIPE,
                )
                try:
                    deadline = time.monotonic() + 5
                    while True:
                        if process.poll() is not None:
                            self.fail(process.stderr.read().decode())
                        try:
                            with socket.create_connection(("127.0.0.1", port), timeout=0.1):
                                break
                        except OSError:
                            if time.monotonic() >= deadline:
                                self.fail("测试 Nginx 启动超时")
                            time.sleep(0.01)
                    for host in (f"localhost:{external_port}", f"homestay.test:{external_port + 10000}"):
                        for endpoint in ("/api/auth/current", "/ws/info", "/ws/000/session/websocket"):
                            with self.subTest(app=app, host=host, endpoint=endpoint):
                                headers = {"Host": host, "Origin": "http://" + host,
                                           "Cookie": "homestay_token=test-only",
                                           "Authorization": "Bearer test-only"}
                                if endpoint.endswith("websocket"):
                                    headers.update({"Upgrade": "websocket", "Connection": "upgrade"})
                                connection = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
                                try:
                                    connection.request("GET", endpoint, headers=headers)
                                    response = connection.getresponse()
                                    self.assertEqual(response.status, 200)
                                    forwarded = json.loads(response.read())
                                    for name, value in headers.items():
                                        self.assertEqual(forwarded.get(name), value)
                                finally:
                                    connection.close()
                finally:
                    process.terminate()
                    process.communicate(timeout=5)
        finally:
            upstream.shutdown()
            upstream.server_close()
            thread.join(timeout=5)


if __name__ == "__main__":
    unittest.main()
