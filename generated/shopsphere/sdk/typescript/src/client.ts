/**
 * ShopSphere HTTP API Client
 * Generated automatically by Zenith DSL v1.0.0
 */

import {
  PageResponse,
  QueryParams,
  LoginRequest,
  AuthResponse,
  Category,
  CreateCategoryRequest,
  UpdateCategoryRequest,
  Product,
  CreateProductRequest,
  UpdateProductRequest,
  Customer,
  CreateCustomerRequest,
  UpdateCustomerRequest,
  Order,
  CreateOrderRequest,
  UpdateOrderRequest,
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
    this.baseUrl = (options.baseUrl || 'http://localhost:8080').replace(/\/+$/, '');
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

  public readonly categorys = {
    list: (params?: QueryParams): Promise<PageResponse<Category>> => {
      const query = params ? '?' + new URLSearchParams(Object.entries(params).filter(([_, v]) => v != null).map(([k, v]) => [k, String(v)])).toString() : '';
      return this.request<PageResponse<Category>>(`/api/v1/categories${query}`);
    },

    getById: (id: string | number): Promise<Category> => {
      return this.request<Category>(`/api/v1/categories/${id}`);
    },

    create: (data: CreateCategoryRequest): Promise<Category> => {
      return this.request<Category>('/api/v1/categories', {
        method: 'POST',
        body: JSON.stringify(data),
      });
    },

    update: (id: string | number, data: UpdateCategoryRequest): Promise<Category> => {
      return this.request<Category>(`/api/v1/categories/${id}`, {
        method: 'PUT',
        body: JSON.stringify(data),
      });
    },

    delete: (id: string | number): Promise<void> => {
      return this.request<void>(`/api/v1/categories/${id}`, {
        method: 'DELETE',
      });
    },
  };

  public readonly products = {
    list: (params?: QueryParams): Promise<PageResponse<Product>> => {
      const query = params ? '?' + new URLSearchParams(Object.entries(params).filter(([_, v]) => v != null).map(([k, v]) => [k, String(v)])).toString() : '';
      return this.request<PageResponse<Product>>(`/api/v1/products${query}`);
    },

    getById: (id: string | number): Promise<Product> => {
      return this.request<Product>(`/api/v1/products/${id}`);
    },

    create: (data: CreateProductRequest): Promise<Product> => {
      return this.request<Product>('/api/v1/products', {
        method: 'POST',
        body: JSON.stringify(data),
      });
    },

    update: (id: string | number, data: UpdateProductRequest): Promise<Product> => {
      return this.request<Product>(`/api/v1/products/${id}`, {
        method: 'PUT',
        body: JSON.stringify(data),
      });
    },

    delete: (id: string | number): Promise<void> => {
      return this.request<void>(`/api/v1/products/${id}`, {
        method: 'DELETE',
      });
    },

    postIdArchive: (id: string | number, payload?: any): Promise<any> => {
      return this.request<any>(`/api/v1/products/${id}/archive`, {
        method: 'POST',
        body: payload ? JSON.stringify(payload) : undefined,
      });
    },
  };

  public readonly customers = {
    list: (params?: QueryParams): Promise<PageResponse<Customer>> => {
      const query = params ? '?' + new URLSearchParams(Object.entries(params).filter(([_, v]) => v != null).map(([k, v]) => [k, String(v)])).toString() : '';
      return this.request<PageResponse<Customer>>(`/api/v1/customers${query}`);
    },

    getById: (id: string | number): Promise<Customer> => {
      return this.request<Customer>(`/api/v1/customers/${id}`);
    },

    create: (data: CreateCustomerRequest): Promise<Customer> => {
      return this.request<Customer>('/api/v1/customers', {
        method: 'POST',
        body: JSON.stringify(data),
      });
    },

    update: (id: string | number, data: UpdateCustomerRequest): Promise<Customer> => {
      return this.request<Customer>(`/api/v1/customers/${id}`, {
        method: 'PUT',
        body: JSON.stringify(data),
      });
    },

    delete: (id: string | number): Promise<void> => {
      return this.request<void>(`/api/v1/customers/${id}`, {
        method: 'DELETE',
      });
    },
  };

  public readonly orders = {
    list: (params?: QueryParams): Promise<PageResponse<Order>> => {
      const query = params ? '?' + new URLSearchParams(Object.entries(params).filter(([_, v]) => v != null).map(([k, v]) => [k, String(v)])).toString() : '';
      return this.request<PageResponse<Order>>(`/api/v1/orders${query}`);
    },

    getById: (id: string | number): Promise<Order> => {
      return this.request<Order>(`/api/v1/orders/${id}`);
    },

    create: (data: CreateOrderRequest): Promise<Order> => {
      return this.request<Order>('/api/v1/orders', {
        method: 'POST',
        body: JSON.stringify(data),
      });
    },

    update: (id: string | number, data: UpdateOrderRequest): Promise<Order> => {
      return this.request<Order>(`/api/v1/orders/${id}`, {
        method: 'PUT',
        body: JSON.stringify(data),
      });
    },

    delete: (id: string | number): Promise<void> => {
      return this.request<void>(`/api/v1/orders/${id}`, {
        method: 'DELETE',
      });
    },

    postIdCancel: (id: string | number, payload?: any): Promise<any> => {
      return this.request<any>(`/api/v1/orders/${id}/cancel`, {
        method: 'POST',
        body: payload ? JSON.stringify(payload) : undefined,
      });
    },

    postIdShip: (id: string | number, payload?: any): Promise<any> => {
      return this.request<any>(`/api/v1/orders/${id}/ship`, {
        method: 'POST',
        body: payload ? JSON.stringify(payload) : undefined,
      });
    },
  };

}

export const createClient = (options?: ZenithClientOptions) => new ZenithClient(options);
