import { ApiResponse, ApiError } from '../types';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api/v1';

export class ApiException extends Error {
  code: string;
  details?: string[];
  status: number;

  constructor(code: string, message: string, status: number, details?: string[]) {
    super(message);
    this.name = 'ApiException';
    this.code = code;
    this.status = status;
    this.details = details;
  }
}

async function request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const url = `${API_BASE}${endpoint.startsWith('/') ? endpoint : `/${endpoint}`}`;

  const token = localStorage.getItem('helixflow_token');
  const headers = new Headers(options.headers || {});

  if (!headers.has('Content-Type') && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }

  if (token && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const config: RequestInit = {
    ...options,
    headers,
  };

  try {
    const response = await fetch(url, config);

    if (response.status === 401) {
      localStorage.removeItem('helixflow_token');
      localStorage.removeItem('helixflow_user');
      window.dispatchEvent(new CustomEvent('helixflow:auth_expired'));
    }

    const json = await response.json();

    if (!response.ok) {
      const err = json as ApiError;
      throw new ApiException(
        err.error?.code || 'HTTP_ERROR',
        err.error?.message || response.statusText,
        response.status,
        err.error?.details
      );
    }

    const apiResponse = json as ApiResponse<T>;
    return apiResponse.data !== undefined ? apiResponse.data : (json as T);
  } catch (error) {
    if (error instanceof ApiException) {
      throw error;
    }
    throw new ApiException('NETWORK_ERROR', (error as Error).message || 'Network request failed', 0);
  }
}

export const apiClient = {
  get: <T>(endpoint: string, options?: RequestInit) => request<T>(endpoint, { ...options, method: 'GET' }),
  post: <T>(endpoint: string, body?: any, options?: RequestInit) =>
    request<T>(endpoint, {
      ...options,
      method: 'POST',
      body: body !== undefined ? JSON.stringify(body) : undefined,
    }),
  put: <T>(endpoint: string, body?: any, options?: RequestInit) =>
    request<T>(endpoint, {
      ...options,
      method: 'PUT',
      body: body !== undefined ? JSON.stringify(body) : undefined,
    }),
  delete: <T>(endpoint: string, options?: RequestInit) => request<T>(endpoint, { ...options, method: 'DELETE' }),
};
