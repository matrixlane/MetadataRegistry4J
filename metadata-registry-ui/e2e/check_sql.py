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

def test_size_with_long_timeout(size):
    start = time.time()
    req = urllib.request.Request(f"{base_url}/data-elements?page=0&size={size}", headers=headers)
    try:
        print(f"Requesting /data-elements?page=0&size={size} (timeout=60s)...")
        with urllib.request.urlopen(req, timeout=60) as response:
            res = json.loads(response.read().decode())
            print(f"Size {size} completed in {time.time() - start:.2f} seconds. Items returned: {len(res['data']['content'])}")
            return True
    except Exception as e:
        print(f"Size {size} failed after {time.time() - start:.2f} seconds: {e}")
        return False

# Run test
test_size_with_long_timeout(2)
test_size_with_long_timeout(8)
test_size_with_long_timeout(20)
