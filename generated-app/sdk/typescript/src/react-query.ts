/**
 * ShopSphere React Query Hooks
 * Generated automatically by Zenith DSL v1.0.0
 * Supports @tanstack/react-query v4 / v5
 */

import { useQuery, useMutation, useQueryClient, UseQueryOptions, UseMutationOptions } from '@tanstack/react-query';
import { ZenithClient } from './client';
import {
  PageResponse,
  QueryParams,
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

export function useCategoryList(
  client: ZenithClient,
  params?: QueryParams,
  options?: Omit<UseQueryOptions<PageResponse<Category>>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: ['categorys', 'list', params],
    queryFn: () => client.categorys.list(params),
    ...options,
  });
}

export function useCategory(
  client: ZenithClient,
  id: string | number,
  options?: Omit<UseQueryOptions<Category>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: ['categorys', 'detail', id],
    queryFn: () => client.categorys.getById(id),
    enabled: !!id,
    ...options,
  });
}

export function useCreateCategory(
  client: ZenithClient,
  options?: UseMutationOptions<Category, Error, CreateCategoryRequest>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateCategoryRequest) => client.categorys.create(data),
    onSuccess: (data, variables, context) => {
      queryClient.invalidateQueries({ queryKey: ['categorys'] });
      options?.onSuccess?.(data, variables, context);
    },
    ...options,
  });
}

export function useUpdateCategory(
  client: ZenithClient,
  options?: UseMutationOptions<Category, Error, { id: string | number; data: UpdateCategoryRequest }>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }) => client.categorys.update(id, data),
    onSuccess: (data, variables, context) => {
      queryClient.invalidateQueries({ queryKey: ['categorys'] });
      options?.onSuccess?.(data, variables, context);
    },
    ...options,
  });
}

export function useDeleteCategory(
  client: ZenithClient,
  options?: UseMutationOptions<void, Error, string | number>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string | number) => client.categorys.delete(id),
    onSuccess: (data, variables, context) => {
      queryClient.invalidateQueries({ queryKey: ['categorys'] });
      options?.onSuccess?.(data, variables, context);
    },
    ...options,
  });
}

export function useProductList(
  client: ZenithClient,
  params?: QueryParams,
  options?: Omit<UseQueryOptions<PageResponse<Product>>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: ['products', 'list', params],
    queryFn: () => client.products.list(params),
    ...options,
  });
}

export function useProduct(
  client: ZenithClient,
  id: string | number,
  options?: Omit<UseQueryOptions<Product>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: ['products', 'detail', id],
    queryFn: () => client.products.getById(id),
    enabled: !!id,
    ...options,
  });
}

export function useCreateProduct(
  client: ZenithClient,
  options?: UseMutationOptions<Product, Error, CreateProductRequest>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateProductRequest) => client.products.create(data),
    onSuccess: (data, variables, context) => {
      queryClient.invalidateQueries({ queryKey: ['products'] });
      options?.onSuccess?.(data, variables, context);
    },
    ...options,
  });
}

export function useUpdateProduct(
  client: ZenithClient,
  options?: UseMutationOptions<Product, Error, { id: string | number; data: UpdateProductRequest }>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }) => client.products.update(id, data),
    onSuccess: (data, variables, context) => {
      queryClient.invalidateQueries({ queryKey: ['products'] });
      options?.onSuccess?.(data, variables, context);
    },
    ...options,
  });
}

export function useDeleteProduct(
  client: ZenithClient,
  options?: UseMutationOptions<void, Error, string | number>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string | number) => client.products.delete(id),
    onSuccess: (data, variables, context) => {
      queryClient.invalidateQueries({ queryKey: ['products'] });
      options?.onSuccess?.(data, variables, context);
    },
    ...options,
  });
}

export function useCustomerList(
  client: ZenithClient,
  params?: QueryParams,
  options?: Omit<UseQueryOptions<PageResponse<Customer>>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: ['customers', 'list', params],
    queryFn: () => client.customers.list(params),
    ...options,
  });
}

export function useCustomer(
  client: ZenithClient,
  id: string | number,
  options?: Omit<UseQueryOptions<Customer>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: ['customers', 'detail', id],
    queryFn: () => client.customers.getById(id),
    enabled: !!id,
    ...options,
  });
}

export function useCreateCustomer(
  client: ZenithClient,
  options?: UseMutationOptions<Customer, Error, CreateCustomerRequest>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateCustomerRequest) => client.customers.create(data),
    onSuccess: (data, variables, context) => {
      queryClient.invalidateQueries({ queryKey: ['customers'] });
      options?.onSuccess?.(data, variables, context);
    },
    ...options,
  });
}

export function useUpdateCustomer(
  client: ZenithClient,
  options?: UseMutationOptions<Customer, Error, { id: string | number; data: UpdateCustomerRequest }>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }) => client.customers.update(id, data),
    onSuccess: (data, variables, context) => {
      queryClient.invalidateQueries({ queryKey: ['customers'] });
      options?.onSuccess?.(data, variables, context);
    },
    ...options,
  });
}

export function useDeleteCustomer(
  client: ZenithClient,
  options?: UseMutationOptions<void, Error, string | number>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string | number) => client.customers.delete(id),
    onSuccess: (data, variables, context) => {
      queryClient.invalidateQueries({ queryKey: ['customers'] });
      options?.onSuccess?.(data, variables, context);
    },
    ...options,
  });
}

export function useOrderList(
  client: ZenithClient,
  params?: QueryParams,
  options?: Omit<UseQueryOptions<PageResponse<Order>>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: ['orders', 'list', params],
    queryFn: () => client.orders.list(params),
    ...options,
  });
}

export function useOrder(
  client: ZenithClient,
  id: string | number,
  options?: Omit<UseQueryOptions<Order>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: ['orders', 'detail', id],
    queryFn: () => client.orders.getById(id),
    enabled: !!id,
    ...options,
  });
}

export function useCreateOrder(
  client: ZenithClient,
  options?: UseMutationOptions<Order, Error, CreateOrderRequest>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateOrderRequest) => client.orders.create(data),
    onSuccess: (data, variables, context) => {
      queryClient.invalidateQueries({ queryKey: ['orders'] });
      options?.onSuccess?.(data, variables, context);
    },
    ...options,
  });
}

export function useUpdateOrder(
  client: ZenithClient,
  options?: UseMutationOptions<Order, Error, { id: string | number; data: UpdateOrderRequest }>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }) => client.orders.update(id, data),
    onSuccess: (data, variables, context) => {
      queryClient.invalidateQueries({ queryKey: ['orders'] });
      options?.onSuccess?.(data, variables, context);
    },
    ...options,
  });
}

export function useDeleteOrder(
  client: ZenithClient,
  options?: UseMutationOptions<void, Error, string | number>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string | number) => client.orders.delete(id),
    onSuccess: (data, variables, context) => {
      queryClient.invalidateQueries({ queryKey: ['orders'] });
      options?.onSuccess?.(data, variables, context);
    },
    ...options,
  });
}

