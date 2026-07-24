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

def test_size(size):
    start = time.time()
    req = urllib.request.Request(f"{base_url}/data-elements?page=0&size={size}", headers=headers)
    try:
        print(f"Requesting /data-elements?page=0&size={size}...")
        with urllib.request.urlopen(req, timeout=5) as response:
            res = json.loads(response.read().decode())
            print(f"Size {size}: Succeeded in {time.time() - start:.2f} seconds. Items returned: {len(res['data']['content'])}")
    except Exception as e:
        print(f"Size {size}: Failed in {time.time() - start:.2f} seconds: {e}")

for s in [1, 2, 3, 4, 5, 8, 10, 20]:
    test_size(s)
