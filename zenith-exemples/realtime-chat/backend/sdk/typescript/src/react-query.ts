/**
 * ZenithChat React Query Hooks
 * Generated automatically by Zenith DSL v1.0.0
 * Supports @tanstack/react-query v4 / v5
 */

import { useQuery, useMutation, useQueryClient, UseQueryOptions, UseMutationOptions } from '@tanstack/react-query';
import { ZenithClient } from './client';
import {
  PageResponse,
  QueryParams,
  ChatUser,
  ChatRoom,
  CreateChatRoomRequest,
  UpdateChatRoomRequest,
  ChatMessage,
  CreateChatMessageRequest,
  UpdateChatMessageRequest,
} from './types';

export function useChatRoomList(
  client: ZenithClient,
  params?: QueryParams,
  options?: Omit<UseQueryOptions<PageResponse<ChatRoom>>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: ['chatRooms', 'list', params],
    queryFn: () => client.chatRooms.list(params),
    ...options,
  });
}

export function useChatRoom(
  client: ZenithClient,
  id: string | number,
  options?: Omit<UseQueryOptions<ChatRoom>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: ['chatRooms', 'detail', id],
    queryFn: () => client.chatRooms.getById(id),
    enabled: !!id,
    ...options,
  });
}

export function useCreateChatRoom(
  client: ZenithClient,
  options?: UseMutationOptions<ChatRoom, Error, CreateChatRoomRequest>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateChatRoomRequest) => client.chatRooms.create(data),
    onSuccess: (data, variables, context) => {
      queryClient.invalidateQueries({ queryKey: ['chatRooms'] });
      options?.onSuccess?.(data, variables, context);
    },
    ...options,
  });
}

export function useUpdateChatRoom(
  client: ZenithClient,
  options?: UseMutationOptions<ChatRoom, Error, { id: string | number; data: UpdateChatRoomRequest }>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }) => client.chatRooms.update(id, data),
    onSuccess: (data, variables, context) => {
      queryClient.invalidateQueries({ queryKey: ['chatRooms'] });
      options?.onSuccess?.(data, variables, context);
    },
    ...options,
  });
}

export function useDeleteChatRoom(
  client: ZenithClient,
  options?: UseMutationOptions<void, Error, string | number>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string | number) => client.chatRooms.delete(id),
    onSuccess: (data, variables, context) => {
      queryClient.invalidateQueries({ queryKey: ['chatRooms'] });
      options?.onSuccess?.(data, variables, context);
    },
    ...options,
  });
}

export function useChatMessageList(
  client: ZenithClient,
  params?: QueryParams,
  options?: Omit<UseQueryOptions<PageResponse<ChatMessage>>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: ['chatMessages', 'list', params],
    queryFn: () => client.chatMessages.list(params),
    ...options,
  });
}

export function useChatMessage(
  client: ZenithClient,
  id: string | number,
  options?: Omit<UseQueryOptions<ChatMessage>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: ['chatMessages', 'detail', id],
    queryFn: () => client.chatMessages.getById(id),
    enabled: !!id,
    ...options,
  });
}

export function useCreateChatMessage(
  client: ZenithClient,
  options?: UseMutationOptions<ChatMessage, Error, CreateChatMessageRequest>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateChatMessageRequest) => client.chatMessages.create(data),
    onSuccess: (data, variables, context) => {
      queryClient.invalidateQueries({ queryKey: ['chatMessages'] });
      options?.onSuccess?.(data, variables, context);
    },
    ...options,
  });
}

export function useUpdateChatMessage(
  client: ZenithClient,
  options?: UseMutationOptions<ChatMessage, Error, { id: string | number; data: UpdateChatMessageRequest }>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }) => client.chatMessages.update(id, data),
    onSuccess: (data, variables, context) => {
      queryClient.invalidateQueries({ queryKey: ['chatMessages'] });
      options?.onSuccess?.(data, variables, context);
    },
    ...options,
  });
}

export function useDeleteChatMessage(
  client: ZenithClient,
  options?: UseMutationOptions<void, Error, string | number>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string | number) => client.chatMessages.delete(id),
    onSuccess: (data, variables, context) => {
      queryClient.invalidateQueries({ queryKey: ['chatMessages'] });
      options?.onSuccess?.(data, variables, context);
    },
    ...options,
  });
}

