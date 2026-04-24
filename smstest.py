from http.server import BaseHTTPRequestHandler, HTTPServer
import json
from urllib.parse import urlparse, parse_qs, unquote

pending_replies = []

class SmsTestHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)
        query = parse_qs(parsed.query)
        request_type = query.get("type", [""])[0]

        if request_type == "send":
            phone = query.get("phone", [""])[0]
            message = unquote(query.get("message", [""])[0])
            print("\n==============================")
            print("SMS RECEIVED FROM PHONE")
            print("Phone:", phone)
            print("Message:", message)
            print("==============================\n")

            pending_replies.append({
                "id": "reply_1",
                "phone": phone,
                "message": "پیام دریافت شد"
            })
            response = {"status": "stored"}

        elif request_type == "receive":
            if pending_replies:
                sms = pending_replies.pop(0)
                print("SENDING REPLY:", sms)
                response = {
                    "status": "ok",
                    "id": sms["id"],
                    "phone": sms["phone"],
                    "message": sms["message"]
                }
            else:
                print("Phone checked server. No SMS to send.")
                response = {"status": "empty"}

        elif "ack" in parsed.path:
            print("ACK:", query)
            response = {"status": "ack_received"}

        else:
            response = {"status": "ok", "message": "RastiSMS test server is running"}

        body = json.dumps(response, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

server = HTTPServer(("0.0.0.0", 8000), SmsTestHandler)
print("RastiSMS test server running on http://0.0.0.0:8000")
server.serve_forever()
