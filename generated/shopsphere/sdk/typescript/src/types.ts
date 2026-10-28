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

export type UserRole = 'ADMIN' | 'CUSTOMER';

export type OrderStatus = 'PENDING' | 'PAID' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';

export type ProductStatus = 'DRAFT' | 'ACTIVE' | 'OUT_OF_STOCK' | 'ARCHIVED';

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
  name: string;
  description?: string;
  price: number;
  stock: number;
  status: ProductStatus;
  createdAt?: string;
  updatedAt: string;
}

export interface CreateProductRequest {
  sku: string;
  name: string;
  description?: string;
  price: number;
  stock?: number;
  status?: ProductStatus;
}

export interface UpdateProductRequest {
  sku?: string;
  name?: string;
  description?: string;
  price?: number;
  stock?: number;
  status?: ProductStatus;
}

export interface Customer {
  id?: number;
  firstName: string;
  lastName: string;
  email: string;
  role: UserRole;
  phone?: string;
  createdAt?: string;
}

export interface CreateCustomerRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  role?: UserRole;
  phone?: string;
}

export interface UpdateCustomerRequest {
  firstName?: string;
  lastName?: string;
  email?: string;
  password?: string;
  role?: UserRole;
  phone?: string;
}

export interface Order {
  id?: number;
  orderNumber: string;
  totalAmount: number;
  status: OrderStatus;
  notes?: string;
  createdAt?: string;
  updatedAt: string;
}

export interface CreateOrderRequest {
  orderNumber: string;
  totalAmount: number;
  status?: OrderStatus;
  notes?: string;
}

export interface UpdateOrderRequest {
  orderNumber?: string;
  totalAmount?: number;
  status?: OrderStatus;
  notes?: string;
}

