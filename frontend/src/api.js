const API_URL = import.meta.env.VITE_API_URL || "/api";

export async function api(path, { token, ...options } = {}) {
  const headers = new Headers(options.headers || {});
  if (options.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (token) headers.set("Authorization", `Bearer ${token}`);

  let response;
  try {
    response = await fetch(`${API_URL}${path}`, { ...options, headers });
  } catch (requestError) {
    if (requestError instanceof TypeError) {
      throw new Error("Cannot connect to the LedgerLine backend. Start the backend and PostgreSQL, then try again.");
    }
    throw requestError;
  }
  const payload = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) {
    if (response.status === 401 && token) {
      window.dispatchEvent(new CustomEvent("ledgerline:unauthorized"));
    }
    if (payload?.database === "DOWN") {
      throw new Error("The backend is running, but PostgreSQL is unavailable. Check the database connection.");
    }
    throw new Error(payload?.error || `Request failed (${response.status})`);
  }
  return payload;
}

export function idempotencyKey() {
  return crypto.randomUUID();
}
