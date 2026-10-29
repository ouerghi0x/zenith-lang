/**
 * ShopSphere TypeScript SDK - Types
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

export type OrderStatus = 'PENDING' | 'PAID' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';

export type UserRole = 'ADMIN' | 'CUSTOMER';

export interface Category {
  id?: number;
  name: string;
  description?: string;
  createdAt?: string;
}

export interface CreateCategoryRequest {
  name: string;
  description?: string;
}

export interface UpdateCategoryRequest {
  name?: string;
  description?: string;
}

export interface Product {
  id?: number;
  sku: string;
  title: string;
  description?: string;
  price: number;
  stock: number;
  createdAt?: string;
  updatedAt: string;
}

export interface CreateProductRequest {
  sku: string;
  title: string;
  description?: string;
  price: number;
  stock?: number;
}

export interface UpdateProductRequest {
  sku?: string;
  title?: string;
  description?: string;
  price?: number;
  stock?: number;
}

export interface Customer {
  id?: string;
  email: string;
  role: UserRole;
  fullName: string;
  createdAt?: string;
}

export interface CreateCustomerRequest {
  email: string;
  password: string;
  role?: UserRole;
  fullName: string;
}

export interface UpdateCustomerRequest {
  email?: string;
  password?: string;
  role?: UserRole;
  fullName?: string;
}

export interface Order {
  id?: string;
  orderNumber: string;
  totalAmount: number;
  status: OrderStatus;
  createdAt?: string;
}

export interface CreateOrderRequest {
  orderNumber: string;
  totalAmount: number;
  status?: OrderStatus;
}

export interface UpdateOrderRequest {
  orderNumber?: string;
  totalAmount?: number;
  status?: OrderStatus;
}

