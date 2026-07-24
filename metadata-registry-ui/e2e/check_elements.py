import urllib.request
import urllib.parse
import json
import time

base_url = "http://localhost:8081/api/v1"

# Login
login_data = json.dumps({"username": "admin", "password": "Admin@123"}).encode('utf-8')
req = urllib.request.Request(f"{base_url}/auth/login", data=login_data, headers={'Content-Type': 'application/json'})
try:
    with urllib.request.urlopen(req) as response:
        login_res = json.loads(response.read().decode())
        token = login_res['data']['token']
except Exception as e:
    print(f"Login failed: {e}")
    exit(1)

headers = {
    'Authorization': f'Bearer {token}',
    'Content-Type': 'application/json'
}

start = time.time()
req = urllib.request.Request(f"{base_url}/data-elements?page=0&size=20", headers=headers)
try:
    print("Requesting /data-elements?page=0&size=20...")
    with urllib.request.urlopen(req, timeout=10) as response:
        res = json.loads(response.read().decode())
        print(f"Succeeded in {time.time() - start:.2f} seconds.")
        print(f"Total elements: {res['data']['totalElements']}")
except Exception as e:
    print(f"Failed in {time.time() - start:.2f} seconds: {e}")
