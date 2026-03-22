export async function apiRequest(path, { baseUrl, token, subjectOverride, method = "GET", body } = {}) {
  const headers = {
    "Content-Type": "application/json"
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  if (subjectOverride) {
    headers["X-Subject-Override"] = subjectOverride;
  }

  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined
  });

  if (!response.ok) {
    const raw = await response.text();
    let detail = raw;
    if (raw) {
      try {
        const payload = JSON.parse(raw);
        detail = payload.message || JSON.stringify(payload);
      } catch {
        // keep raw text
      }
    }
    throw new Error(`${response.status} ${response.statusText}${detail ? `: ${detail}` : ""}`);
  }

  if (response.status === 204) {
    return null;
  }
  return response.json();
}
