package dev.zenith.generator;

import dev.zenith.ast.DtoDef;
import dev.zenith.ast.EnumDef;
import dev.zenith.ast.FieldDef;
import dev.zenith.ast.ModelDef;
import dev.zenith.ast.ZenithFile;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates an end-to-end TypeScript Client SDK with:
 * - Full TypeScript interfaces (Models, Create Requests, Update Requests,
 * PageResponse, Auth)
 * - Pure HTTP Client API class using fetch (no external runtime dependencies
 * required)
 * - Ready-to-use React Query hooks (useQuery, useMutation) for instant frontend
 * integration
 */
public class TypeScriptSdkGenerator {

    public static List<JavaSourceFile> generate(ZenithFile file) {
        List<JavaSourceFile> list = new ArrayList<>();
        list.add(generateTypes(file));
        list.add(generateHttpClient(file));
        list.add(generateReactQueryHooks(file));
        list.add(generatePackageJson(file));
        return list;
    }

    private static JavaSourceFile generateTypes(ZenithFile file) {
        StringBuilder sb = new StringBuilder();
        sb.append("/**\n");
        sb.append(" * ").append(file.getConfig().getApp()).append(" TypeScript SDK - Types\n");
        sb.append(" * Generated automatically by Zenith DSL v1.0.0\n");
        sb.append(" */\n\n");

        // Page Response
        sb.append("export interface PageResponse<T> {\n");
        sb.append("  content: T[];\n");
        sb.append("  page: number;\n");
        sb.append("  size: number;\n");
        sb.append("  totalElements: number;\n");
        sb.append("  totalPages: number;\n");
        sb.append("  first: boolean;\n");
        sb.append("  last: boolean;\n");
        sb.append("  empty: boolean;\n");
        sb.append("}\n\n");

        sb.append("export interface QueryParams {\n");
        sb.append("  page?: number;\n");
        sb.append("  size?: number;\n");
        sb.append("  sort?: string;\n");
        sb.append("  direction?: 'asc' | 'desc';\n");
        sb.append("  search?: string;\n");
        sb.append("  [key: string]: any;\n");
        sb.append("}\n\n");

        // Auth types if enabled
        if (file.hasAuth()) {
            sb.append("// ================= AUTH =================\n");
            sb.append("export interface LoginRequest {\n");
            sb.append("  username: string;\n");
            sb.append("  password: string;\n");
            sb.append("}\n\n");

            sb.append("export interface AuthResponse {\n");
            sb.append("  token: string;\n");
            sb.append("  type: string;\n");
            sb.append("  username: string;\n");
            sb.append("  roles: string[];\n");
            sb.append("}\n\n");
        }

        // Enums
        for (EnumDef e : file.getEnums()) {
            sb.append("export type ").append(e.getName()).append(" = ");
            for (int i = 0; i < e.getValues().size(); i++) {
                if (i > 0)
                    sb.append(" | ");
                sb.append("'").append(e.getValues().get(i)).append("'");
            }
            sb.append(";\n\n");
        }

        // Models
        for (ModelDef model : file.getModels()) {
            String mName = model.getName();

            // 1. Response Model
            sb.append("export interface ").append(mName).append(" {\n");
            for (FieldDef f : model.getFields()) {
                if (f.hasAnnotation("password"))
                    continue; // stripped from response
                sb.append("  ").append(f.getName());
                if (f.isOptional() || f.hasAnnotation("auto") || f.hasAnnotation("autoNow")) {
                    sb.append("?");
                }
                sb.append(": ").append(mapTsType(f.getType())).append(";\n");
            }
            sb.append("}\n\n");

            if (model.getCrudConfig() != null) {
                // 2. Create Request
                sb.append("export interface Create").append(mName).append("Request {\n");
                for (FieldDef f : model.getFields()) {
                    if (f.hasAnnotation("auto") || f.hasAnnotation("autoNow") || f.hasAnnotation("autoUpdate"))
                        continue;
                    sb.append("  ").append(f.getName());
                    if (f.isOptional() || f.getDefaultValue() != null) {
                        sb.append("?");
                    }
                    sb.append(": ").append(mapTsType(f.getType())).append(";\n");
                }
                sb.append("}\n\n");

                // 3. Update Request
                sb.append("export interface Update").append(mName).append("Request {\n");
                for (FieldDef f : model.getFields()) {
                    if (f.hasAnnotation("auto") || f.hasAnnotation("autoNow") || f.hasAnnotation("autoUpdate"))
                        continue;
                    sb.append("  ").append(f.getName()).append("?: ").append(mapTsType(f.getType())).append(";\n");
                }
                sb.append("}\n\n");
            }
        }
        // dto
        for (DtoDef dto : file.getDtos()) {
            sb.append("export interface ").append(dto.getName()).append(" {\n");
            for (FieldDef f : dto.getFields()) {
                sb.append("  ").append(f.getName());
                if (f.isOptional() || f.getDefaultValue() != null) {
                    sb.append("?");
                }
                sb.append(": ").append(mapTsType(f.getType())).append(";\n");
            }
            sb.append("}\n\n");
        }
        return new JavaSourceFile("sdk/typescript/src/types.ts", sb.toString());
    }

    private static JavaSourceFile generateHttpClient(ZenithFile file) {
        StringBuilder sb = new StringBuilder();
        sb.append("/**\n");
        sb.append(" * ").append(file.getConfig().getApp()).append(" HTTP API Client\n");
        sb.append(" * Generated automatically by Zenith DSL v1.0.0\n");
        sb.append(" */\n\n");

        sb.append("import {\n");
        sb.append("  PageResponse,\n");
        sb.append("  QueryParams,\n");
        if (file.hasAuth()) {
            sb.append("  LoginRequest,\n");
            sb.append("  AuthResponse,\n");
        }
        for (ModelDef model : file.getModels()) {
            sb.append("  ").append(model.getName()).append(",\n");
            if (model.getCrudConfig() != null) {
                sb.append("  Create").append(model.getName()).append("Request,\n");
                sb.append("  Update").append(model.getName()).append("Request,\n");
            }
        }
        sb.append("} from './types';\n\n");

        sb.append("export interface ZenithClientOptions {\n");
        sb.append("  baseUrl?: string;\n");
        sb.append("  getToken?: () => string | null | Promise<string | null>;\n");
        sb.append("  headers?: Record<string, string>;\n");
        sb.append("}\n\n");

        sb.append("export class ZenithClient {\n");
        sb.append("  private baseUrl: string;\n");
        sb.append("  private getToken?: () => string | null | Promise<string | null>;\n");
        sb.append("  private customHeaders: Record<string, string>;\n\n");

        sb.append("  constructor(options: ZenithClientOptions = {}) {\n");
        sb.append("    this.baseUrl = (options.baseUrl || 'http://localhost:").append(file.getConfig().getPort())
                .append("').replace(/\\/+$/, '');\n");
        sb.append("    this.getToken = options.getToken;\n");
        sb.append("    this.customHeaders = options.headers || {};\n");
        sb.append("  }\n\n");

        sb.append("  private async request<T>(path: string, options: RequestInit = {}): Promise<T> {\n");
        sb.append("    const headers: Record<string, string> = {\n");
        sb.append("      'Content-Type': 'application/json',\n");
        sb.append("      'Accept': 'application/json',\n");
        sb.append("      ...this.customHeaders,\n");
        sb.append("    };\n\n");
        sb.append("    if (this.getToken) {\n");
        sb.append("      const token = await this.getToken();\n");
        sb.append("      if (token) {\n");
        sb.append("        headers['Authorization'] = `Bearer ${token}`;\n");
        sb.append("      }\n");
        sb.append("    }\n\n");
        sb.append("    const response = await fetch(`${this.baseUrl}${path}`, {\n");
        sb.append("      ...options,\n");
        sb.append("      headers: { ...headers, ...(options.headers as any) },\n");
        sb.append("    });\n\n");
        sb.append("    if (!response.ok) {\n");
        sb.append("      let errorBody = await response.text();\n");
        sb.append("      try { errorBody = JSON.parse(errorBody); } catch (_) {}\n");
        sb.append(
                "      throw new Error((errorBody as any)?.message || `HTTP ${response.status}: ${response.statusText}`);\n");
        sb.append("    }\n\n");
        sb.append("    if (response.status === 204) {\n");
        sb.append("      return undefined as any;\n");
        sb.append("    }\n");
        sb.append("    return response.json();\n");
        sb.append("  }\n\n");

        // Auth service
        if (file.hasAuth()) {
            String authBase = file.getConfig().getBasePath() + "/auth";
            sb.append("  public readonly auth = {\n");
            sb.append("    login: (credentials: LoginRequest): Promise<AuthResponse> => {\n");
            sb.append("      return this.request<AuthResponse>('").append(authBase).append("/login', {\n");
            sb.append("        method: 'POST',\n");
            sb.append("        body: JSON.stringify(credentials),\n");
            sb.append("      });\n");
            sb.append("    },\n");
            sb.append("    register: (data: any): Promise<AuthResponse> => {\n");
            sb.append("      return this.request<AuthResponse>('").append(authBase).append("/register', {\n");
            sb.append("        method: 'POST',\n");
            sb.append("        body: JSON.stringify(data),\n");
            sb.append("      });\n");
            sb.append("    },\n");
            sb.append("  };\n\n");
        }

        // Model services
        for (ModelDef model : file.getModels()) {
            if (!model.hasEndpoints())
                continue;
            String mName = model.getName();
            String propName = Character.toLowerCase(mName.charAt(0)) + mName.substring(1) + "s";
            String basePath = file.getConfig().getBasePath() + "/"
                    + TypeHelper.toSnakeCase(TypeHelper.pluralize(mName));

            sb.append("  public readonly ").append(propName).append(" = {\n");

            if (model.getCrudConfig() != null) {
                // list
                sb.append("    list: (params?: QueryParams): Promise<PageResponse<").append(mName).append(">> => {\n");
                sb.append(
                        "      const query = params ? '?' + new URLSearchParams(Object.entries(params).filter(([_, v]) => v != null).map(([k, v]) => [k, String(v)])).toString() : '';\n");
                sb.append("      return this.request<PageResponse<").append(mName).append(">>(`").append(basePath)
                        .append("${query}`);\n");
                sb.append("    },\n\n");

                // getById
                sb.append("    getById: (id: string | number): Promise<").append(mName).append("> => {\n");
                sb.append("      return this.request<").append(mName).append(">(`").append(basePath)
                        .append("/${id}`);\n");
                sb.append("    },\n\n");

                // create
                sb.append("    create: (data: Create").append(mName).append("Request): Promise<").append(mName)
                        .append("> => {\n");
                sb.append("      return this.request<").append(mName).append(">('").append(basePath).append("', {\n");
                sb.append("        method: 'POST',\n");
                sb.append("        body: JSON.stringify(data),\n");
                sb.append("      });\n");
                sb.append("    },\n\n");

                // update
                sb.append("    update: (id: string | number, data: Update").append(mName).append("Request): Promise<")
                        .append(mName).append("> => {\n");
                sb.append("      return this.request<").append(mName).append(">(`").append(basePath)
                        .append("/${id}`, {\n");
                sb.append("        method: 'PUT',\n");
                sb.append("        body: JSON.stringify(data),\n");
                sb.append("      });\n");
                sb.append("    },\n\n");

                // delete
                sb.append("    delete: (id: string | number): Promise<void> => {\n");
                sb.append("      return this.request<void>(`").append(basePath).append("/${id}`, {\n");
                sb.append("        method: 'DELETE',\n");
                sb.append("      });\n");
                sb.append("    },\n");
            }

            // custom endpoints
            for (var c : model.getCustomEndpoints()) {
                String methodName = toCamelCaseMethod(c.getMethod(), c.getPath());
                String httpMethod = c.getMethod().toUpperCase();
                String pathFormat = basePath + c.getPath().replaceAll("\\{([^}]+)\\}", "\\${$1}");
                boolean hasId = c.getPath().contains("{id}");
                String bodyParam = c.hasBody() ? ("body: " + c.getBodyType()) : "payload?: any";
                String bodyArg = c.hasBody() ? "body" : "payload";

                sb.append("\n    ").append(methodName).append(": (");
                if (hasId)
                    sb.append("id: string | number, ");
                sb.append(bodyParam).append("): Promise<any> => {\n");
                sb.append("      return this.request<any>(`").append(pathFormat).append("`, {\n");
                sb.append("        method: '").append(httpMethod).append("',\n");
                if ("POST".equalsIgnoreCase(httpMethod) || "PUT".equalsIgnoreCase(httpMethod)
                        || "PATCH".equalsIgnoreCase(httpMethod)) {
                    sb.append("        body: ").append(bodyArg).append(" ? JSON.stringify(").append(bodyArg).append(") : undefined,\n");
                }
                sb.append("      });\n");
                sb.append("    },\n");
            }

            sb.append("  };\n\n");
        }

        sb.append("}\n\n");
        sb.append("export const createClient = (options?: ZenithClientOptions) => new ZenithClient(options);\n");

        return new JavaSourceFile("sdk/typescript/src/client.ts", sb.toString());
    }

    private static JavaSourceFile generateReactQueryHooks(ZenithFile file) {
        StringBuilder sb = new StringBuilder();
        sb.append("/**\n");
        sb.append(" * ").append(file.getConfig().getApp()).append(" React Query Hooks\n");
        sb.append(" * Generated automatically by Zenith DSL v1.0.0\n");
        sb.append(" * Supports @tanstack/react-query v4 / v5\n");
        sb.append(" */\n\n");

        sb.append(
                "import { useQuery, useMutation, useQueryClient, UseQueryOptions, UseMutationOptions } from '@tanstack/react-query';\n");
        sb.append("import { ZenithClient } from './client';\n");
        sb.append("import {\n");
        sb.append("  PageResponse,\n");
        sb.append("  QueryParams,\n");
        for (ModelDef model : file.getModels()) {
            sb.append("  ").append(model.getName()).append(",\n");
            if (model.getCrudConfig() != null) {
                sb.append("  Create").append(model.getName()).append("Request,\n");
                sb.append("  Update").append(model.getName()).append("Request,\n");
            }
        }
        sb.append("} from './types';\n\n");

        for (ModelDef model : file.getModels()) {
            if (!model.hasEndpoints())
                continue;
            String mName = model.getName();
            String propName = Character.toLowerCase(mName.charAt(0)) + mName.substring(1) + "s";
            String queryKey = "'" + propName + "'";

            if (model.getCrudConfig() != null) {
                // useList hook
                sb.append("export function use").append(mName).append("List(\n");
                sb.append("  client: ZenithClient,\n");
                sb.append("  params?: QueryParams,\n");
                sb.append("  options?: Omit<UseQueryOptions<PageResponse<").append(mName)
                        .append(">>, 'queryKey' | 'queryFn'>\n");
                sb.append(") {\n");
                sb.append("  return useQuery({\n");
                sb.append("    queryKey: [").append(queryKey).append(", 'list', params],\n");
                sb.append("    queryFn: () => client.").append(propName).append(".list(params),\n");
                sb.append("    ...options,\n");
                sb.append("  });\n");
                sb.append("}\n\n");

                // useById hook
                sb.append("export function use").append(mName).append("(\n");
                sb.append("  client: ZenithClient,\n");
                sb.append("  id: string | number,\n");
                sb.append("  options?: Omit<UseQueryOptions<").append(mName).append(">, 'queryKey' | 'queryFn'>\n");
                sb.append(") {\n");
                sb.append("  return useQuery({\n");
                sb.append("    queryKey: [").append(queryKey).append(", 'detail', id],\n");
                sb.append("    queryFn: () => client.").append(propName).append(".getById(id),\n");
                sb.append("    enabled: !!id,\n");
                sb.append("    ...options,\n");
                sb.append("  });\n");
                sb.append("}\n\n");

                // useCreate hook
                sb.append("export function useCreate").append(mName).append("(\n");
                sb.append("  client: ZenithClient,\n");
                sb.append("  options?: UseMutationOptions<").append(mName).append(", Error, Create").append(mName)
                        .append("Request>\n");
                sb.append(") {\n");
                sb.append("  const queryClient = useQueryClient();\n");
                sb.append("  return useMutation({\n");
                sb.append("    mutationFn: (data: Create").append(mName).append("Request) => client.").append(propName)
                        .append(".create(data),\n");
                sb.append("    onSuccess: (data, variables, context) => {\n");
                sb.append("      queryClient.invalidateQueries({ queryKey: [").append(queryKey).append("] });\n");
                sb.append("      options?.onSuccess?.(data, variables, context);\n");
                sb.append("    },\n");
                sb.append("    ...options,\n");
                sb.append("  });\n");
                sb.append("}\n\n");

                // useUpdate hook
                sb.append("export function useUpdate").append(mName).append("(\n");
                sb.append("  client: ZenithClient,\n");
                sb.append("  options?: UseMutationOptions<").append(mName)
                        .append(", Error, { id: string | number; data: Update").append(mName).append("Request }>\n");
                sb.append(") {\n");
                sb.append("  const queryClient = useQueryClient();\n");
                sb.append("  return useMutation({\n");
                sb.append("    mutationFn: ({ id, data }) => client.").append(propName).append(".update(id, data),\n");
                sb.append("    onSuccess: (data, variables, context) => {\n");
                sb.append("      queryClient.invalidateQueries({ queryKey: [").append(queryKey).append("] });\n");
                sb.append("      options?.onSuccess?.(data, variables, context);\n");
                sb.append("    },\n");
                sb.append("    ...options,\n");
                sb.append("  });\n");
                sb.append("}\n\n");

                // useDelete hook
                sb.append("export function useDelete").append(mName).append("(\n");
                sb.append("  client: ZenithClient,\n");
                sb.append("  options?: UseMutationOptions<void, Error, string | number>\n");
                sb.append(") {\n");
                sb.append("  const queryClient = useQueryClient();\n");
                sb.append("  return useMutation({\n");
                sb.append("    mutationFn: (id: string | number) => client.").append(propName).append(".delete(id),\n");
                sb.append("    onSuccess: (data, variables, context) => {\n");
                sb.append("      queryClient.invalidateQueries({ queryKey: [").append(queryKey).append("] });\n");
                sb.append("      options?.onSuccess?.(data, variables, context);\n");
                sb.append("    },\n");
                sb.append("    ...options,\n");
                sb.append("  });\n");
                sb.append("}\n\n");
            }
        }

        return new JavaSourceFile("sdk/typescript/src/react-query.ts", sb.toString());
    }

    private static JavaSourceFile generatePackageJson(ZenithFile file) {
        String appName = file.getConfig().getApp().toLowerCase().replaceAll("[^a-z0-9-]", "-");
        String content = "{\n" +
                "  \"name\": \"@" + appName + "/sdk\",\n" +
                "  \"version\": \"1.0.0\",\n" +
                "  \"description\": \"Full-stack TypeScript client SDK generated by Zenith DSL\",\n" +
                "  \"main\": \"src/index.ts\",\n" +
                "  \"types\": \"src/index.ts\",\n" +
                "  \"scripts\": {\n" +
                "    \"build\": \"tsc\"\n" +
                "  },\n" +
                "  \"peerDependencies\": {\n" +
                "    \"@tanstack/react-query\": \"^4.0.0 || ^5.0.0\"\n" +
                "  },\n" +
                "  \"peerDependenciesMeta\": {\n" +
                "    \"@tanstack/react-query\": {\n" +
                "      \"optional\": true\n" +
                "    }\n" +
                "  },\n" +
                "  \"devDependencies\": {\n" +
                "    \"typescript\": \"^5.0.0\"\n" +
                "  }\n" +
                "}\n";
        return new JavaSourceFile("sdk/typescript/package.json", content);
    }

    private static String mapTsType(dev.zenith.ast.TypeRef type) {
        if (type == null)
            return "any";
        if (type.isList()) {
            return mapTsType(type.getGenericType()) + "[]";
        }
        return mapTsType(type.getName());
    }

    private static String mapTsType(String zenithType) {
        if (zenithType == null)
            return "any";
        String base = zenithType.contains("(") ? zenithType.substring(0, zenithType.indexOf('(')).trim()
                : zenithType.trim();

        if (base.startsWith("List<") && base.endsWith(">")) {
            String inner = base.substring(5, base.length() - 1);
            return mapTsType(inner) + "[]";
        }

        switch (base) {
            case "String":
            case "Text":
            case "UUID":
            case "DateTime":
            case "Date":
                return "string";
            case "Int":
            case "Long":
            case "Double":
            case "Float":
            case "Decimal":
                return "number";
            case "Boolean":
                return "boolean";
            default:
                // Enum or referenced Model
                return base;
        }
    }

    private static String toCamelCaseMethod(String method, String path) {
        String clean = path.replaceAll("[{}]", "").replaceAll("^/+", "").replaceAll("/+", "_");
        String[] parts = clean.split("_");
        StringBuilder sb = new StringBuilder();
        sb.append(method.toLowerCase());
        for (String p : parts) {
            if (p.isEmpty())
                continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
