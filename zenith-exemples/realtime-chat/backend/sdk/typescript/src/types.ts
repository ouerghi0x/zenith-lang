/**
 * ZenithChat TypeScript SDK - Types
 * Generated automatically by Zenith DSL v1.0.0
 */

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface QueryParams {
  page?: number;
  size?: number;
  sort?: string;
  direction?: 'asc' | 'desc';
  search?: string;
  [key: string]: any;
}

// ================= AUTH =================
export interface LoginRequest {
  username: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  type: string;
  username: string;
  roles: string[];
}

export type MessageType = 'CHAT' | 'JOIN' | 'LEAVE';

export type UserRole = 'ADMIN' | 'USER';

export interface ChatUser {
  id?: string;
  username: string;
  email: string;
  role: UserRole;
  avatarUrl?: string;
  createdAt?: string;
}

export interface ChatRoom {
  id?: string;
  name: string;
  description?: string;
  createdAt?: string;
}

export interface CreateChatRoomRequest {
  name: string;
  description?: string;
}

export interface UpdateChatRoomRequest {
  name?: string;
  description?: string;
}

export interface ChatMessage {
  id?: string;
  content: string;
  type: MessageType;
  recipientId?: string;
  createdAt?: string;
}

export interface CreateChatMessageRequest {
  content: string;
  type?: MessageType;
  recipientId?: string;
}

export interface UpdateChatMessageRequest {
  content?: string;
  type?: MessageType;
  recipientId?: string;
}

