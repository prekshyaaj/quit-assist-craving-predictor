import json
import time
import urllib.request

AZURE_CHATBOT_URL = "https://chatbot-server.proudcliff-0eaff5e4.centralindia.azurecontainerapps.io/health"


def test_azure_deployment():
    print("==================================================")
    print(" Testing Azure Chatbot Deployment Status ")
    print(f" Target: {AZURE_CHATBOT_URL}")
    print("==================================================")

    start_time = time.time()
    try:
        req = urllib.request.Request(
            AZURE_CHATBOT_URL,
            headers={"User-Agent": "Deployment-Checker/1.0"}
        )
        with urllib.request.urlopen(req, timeout=10) as response:
            latency = (time.time() - start_time) * 1000
            status_code = response.getcode()
            body_text = response.read().decode("utf-8")

            print(f"HTTP Status  : {status_code}")
            print(f"Latency      : {latency:.2f} ms")
            print(f"Response Body: {body_text}")

            try:
                data = json.loads(body_text)
                if data.get("status") == "ok":
                    print("\n✅ Azure Chatbot Server is LIVE & Healthy!")
                else:
                    print(f"\n⚠️ Server online but returned unexpected JSON: {data}")
            except Exception:
                print(f"\n⚠️ Server online but body is not JSON: {body_text}")

    except Exception as e:
        print(f"\n❌ Failed to reach Azure Chatbot Server: {e}")


if __name__ == "__main__":
    test_azure_deployment()
