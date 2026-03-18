import subprocess
import http.server
import socketserver
import time
from urllib.parse import urlparse, parse_qs


# 自定义请求处理程序以支持 CORS 和流式响应
class CORSRequestHandler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header('Access-Control-Allow-Origin', '*')
        http.server.SimpleHTTPRequestHandler.end_headers(self)

    def do_GET(self):
        parsed_url = urlparse(self.path)
        if parsed_url.path == '/stream':
            query_params = parse_qs(parsed_url.query)
            rtsp_url = query_params.get('rtsp_url')
            if not rtsp_url:
                self.send_error(400, "Missing 'rtsp_url' parameter")
                return

            rtsp_url = rtsp_url[0]
            self.send_response(200)
            self.send_header('Content-type', 'video/x-flv')
            self.end_headers()

            try:
                # 调用 FFmpeg 进行流转换
                ffmpeg_command = [
                    r'ffmpeg.exe',
                    '-rtsp_transport', 'tcp',
                    "-i", rtsp_url,
                    "-c:v", "libx264",
                    "-preset", "ultrafast",
                    "-tune", "zerolatency",
                    "-c:a", "aac",
                    "-f", "flv",
                    "-"
                ]
                ffmpeg_process = subprocess.Popen(ffmpeg_command, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)

                while True:
                    try:
                        data = ffmpeg_process.stdout.read(4096)
                        if not data:
                            break
                        self.wfile.write(data)
                    except (BrokenPipeError, ConnectionResetError):
                        # 前端中断连接，退出循环
                        print("Client disconnected, stopping FFmpeg process...")
                        break
                    except Exception as e:
                        print(f"Error while reading data: {e}")
                        break

            except Exception as e:
                print(f"Error starting FFmpeg process: {e}")
            finally:
                if 'ffmpeg_process' in locals() and ffmpeg_process:
                    ffmpeg_process.terminate()
                    ffmpeg_process.wait()  # 等待 FFmpeg 进程结束
        else:
            self.send_error(404, "File not found")


if __name__ == '__main__':
    # 启动简单的 Web 服务器
    PORT = 9000
    while True:
        try:
            with socketserver.ThreadingTCPServer(("", PORT), CORSRequestHandler) as httpd:
                print(f"Serving at port {PORT}")
                httpd.serve_forever()
        except Exception as e:
            print(f"Error in web server: {e}")
            time.sleep(5)  # 等待 5 秒后重试
