/**
 * ZenithChat HTTP API Client
 * Generated automatically by Zenith DSL v1.0.0
 */

import {
  PageResponse,
  QueryParams,
  LoginRequest,
  AuthResponse,
  ChatUser,
  ChatRoom,
  CreateChatRoomRequest,
  UpdateChatRoomRequest,
  ChatMessage,
  CreateChatMessageRequest,
  UpdateChatMessageRequest,
} from './types';

export interface ZenithClientOptions {
  baseUrl?: string;
  getToken?: () => string | null | Promise<string | null>;
  headers?: Record<string, string>;
}

export class ZenithClient {
  private baseUrl: string;
  private getToken?: () => string | null | Promise<string | null>;
  private customHeaders: Record<string, string>;

  constructor(options: ZenithClientOptions = {}) {
    this.baseUrl = (options.baseUrl || 'http://localhost:8081').replace(/\/+$/, '');
    this.getToken = options.getToken;
    this.customHeaders = options.headers || {};
  }

  private async request<T>(path: string, options: RequestInit = {}): Promise<T> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      ...this.customHeaders,
    };

    if (this.getToken) {
      const token = await this.getToken();
      if (token) {
        headers['Authorization'] = `Bearer ${token}`;
      }
    }

    const response = await fetch(`${this.baseUrl}${path}`, {
      ...options,
      headers: { ...headers, ...(options.headers as any) },
    });

    if (!response.ok) {
      let errorBody = await response.text();
      try { errorBody = JSON.parse(errorBody); } catch (_) {}
      throw new Error((errorBody as any)?.message || `HTTP ${response.status}: ${response.statusText}`);
    }

    if (response.status === 204) {
      return undefined as any;
    }
    return response.json();
  }

  public readonly auth = {
    login: (credentials: LoginRequest): Promise<AuthResponse> => {
      return this.request<AuthResponse>('/api/v1/auth/login', {
        method: 'POST',
        body: JSON.stringify(credentials),
      });
    },
    register: (data: any): Promise<AuthResponse> => {
      return this.request<AuthResponse>('/api/v1/auth/register', {
        method: 'POST',
        body: JSON.stringify(data),
      });
    },
  };

  public readonly chatRooms = {
    list: (params?: QueryParams): Promise<PageResponse<ChatRoom>> => {
      const query = params ? '?' + new URLSearchParams(Object.entries(params).filter(([_, v]) => v != null).map(([k, v]) => [k, String(v)])).toString() : '';
      return this.request<PageResponse<ChatRoom>>(`/api/v1/chat_rooms${query}`);
    },

    getById: (id: string | number): Promise<ChatRoom> => {
      return this.request<ChatRoom>(`/api/v1/chat_rooms/${id}`);
    },

    create: (data: CreateChatRoomRequest): Promise<ChatRoom> => {
      return this.request<ChatRoom>('/api/v1/chat_rooms', {
        method: 'POST',
        body: JSON.stringify(data),
      });
    },

    update: (id: string | number, data: UpdateChatRoomRequest): Promise<ChatRoom> => {
      return this.request<ChatRoom>(`/api/v1/chat_rooms/${id}`, {
        method: 'PUT',
        body: JSON.stringify(data),
      });
    },

    delete: (id: string | number): Promise<void> => {
      return this.request<void>(`/api/v1/chat_rooms/${id}`, {
        method: 'DELETE',
      });
    },
  };

  public readonly chatMessages = {
    list: (params?: QueryParams): Promise<PageResponse<ChatMessage>> => {
      const query = params ? '?' + new URLSearchParams(Object.entries(params).filter(([_, v]) => v != null).map(([k, v]) => [k, String(v)])).toString() : '';
      return this.request<PageResponse<ChatMessage>>(`/api/v1/chat_messages${query}`);
    },

    getById: (id: string | number): Promise<ChatMessage> => {
      return this.request<ChatMessage>(`/api/v1/chat_messages/${id}`);
    },

    create: (data: CreateChatMessageRequest): Promise<ChatMessage> => {
      return this.request<ChatMessage>('/api/v1/chat_messages', {
        method: 'POST',
        body: JSON.stringify(data),
      });
    },

    update: (id: string | number, data: UpdateChatMessageRequest): Promise<ChatMessage> => {
      return this.request<ChatMessage>(`/api/v1/chat_messages/${id}`, {
        method: 'PUT',
        body: JSON.stringify(data),
      });
    },

    delete: (id: string | number): Promise<void> => {
      return this.request<void>(`/api/v1/chat_messages/${id}`, {
        method: 'DELETE',
      });
    },

    postIdSend: (id: string | number, payload?: any): Promise<any> => {
      return this.request<any>(`/api/v1/chat_messages/${id}/send`, {
        method: 'POST',
        body: payload ? JSON.stringify(payload) : undefined,
      });
    },

    postIdDirect: (id: string | number, payload?: any): Promise<any> => {
      return this.request<any>(`/api/v1/chat_messages/${id}/direct`, {
        method: 'POST',
        body: payload ? JSON.stringify(payload) : undefined,
      });
    },
  };

}

export const createClient = (options?: ZenithClientOptions) => new ZenithClient(options);
