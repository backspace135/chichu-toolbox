package rikka.shizuku.server;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class PendingPermissions {
    private static final long LIFETIME_MS = 300_000;
    private static final int MAX_PENDING = 128;
    private final Map<String, Request> requests = new LinkedHashMap<>();

    public static final class Request {
        public final int uid;
        public final int pid;
        public final int requestCode;
        public final String packageName;
        private final long issuedAt;

        Request(int uid, int pid, int requestCode, String packageName, long issuedAt) {
            this.uid = uid;
            this.pid = pid;
            this.requestCode = requestCode;
            this.packageName = packageName;
            this.issuedAt = issuedAt;
        }
    }

    public synchronized String issue(int uid, int pid, int requestCode, String packageName, long now) {
        requests.values().removeIf(request -> expired(request, now)
                || (request.uid == uid && request.pid == pid && request.requestCode == requestCode));
        if (requests.size() >= MAX_PENDING) {
            requests.remove(requests.keySet().iterator().next());
        }
        String token = UUID.randomUUID().toString();
        requests.put(token, new Request(uid, pid, requestCode, packageName, now));
        return token;
    }

    public synchronized Request consume(String token, int uid, int pid, int requestCode, long now) {
        Request request = requests.get(token);
        if (request == null) return null;
        if (expired(request, now)) {
            requests.remove(token);
            return null;
        }
        if (request.uid != uid || request.pid != pid || request.requestCode != requestCode) return null;
        requests.remove(token);
        return request;
    }

    public synchronized Request peek(String token, long now) {
        Request request = requests.get(token);
        if (request != null && expired(request, now)) {
            requests.remove(token);
            return null;
        }
        return request;
    }

    private boolean expired(Request request, long now) {
        return now < request.issuedAt || now - request.issuedAt >= LIFETIME_MS;
    }
}
