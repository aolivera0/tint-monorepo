const API_URL = import.meta.env.VITE_IDENTITY_API_URL || 'http://localhost:8081/api/v1';

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

export async function apiFetch(path: string, options: RequestInit = {}) {
  const headers = new Headers(options.headers);
  if (!headers.has('Content-Type') && options.body) {
    headers.set('Content-Type', 'application/json');
  }
  const token = localStorage.getItem('tint_token');
  const guestToken = localStorage.getItem('tint_guest_token');
  if (guestToken && !token) {
    headers.set('Authorization', `Bearer ${guestToken}`);
  }
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }
  const res = await fetch(`${API_URL}${path}`, {
    ...options,
    headers,
  });
  if (!res.ok) {
    const data = (await res.json().catch(() => null)) as { error?: string } | null;
    throw new ApiError(res.status, data?.error || `API error ${res.status}`);
  }
  return res.json();
}
