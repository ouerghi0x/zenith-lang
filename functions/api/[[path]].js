/**
 * Cloudflare Pages Edge Function: /api/*
 * 1. Enforces Paid PRO License / Subscription for PRO endpoints.
 * 2. Proxies compiler & workflow requests to the hosted Zenith Java Container.
 */
export async function onRequest(context) {
  const { request, env } = context;
  const url = new URL(request.url);

  // Handle CORS preflight at the Cloudflare Edge
  if (request.method === "OPTIONS") {
    return new Response(null, {
      status: 204,
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
        "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Zenith-License",
      },
    });
  }

  // Check if user is trying to toggle or access PRO Edition without a valid license key
  const licenseKey = request.headers.get("X-Zenith-License") || url.searchParams.get("license");
  const isProValid = await verifyProLicense(licenseKey, env);

  // Intercept /api/edition at the Cloudflare Edge so unpaid users cannot toggle PRO for free
  if (url.pathname === "/api/edition" && request.method === "POST") {
    if (!isProValid) {
      return new Response(
        JSON.stringify({
          isPro: false,
          edition: "COMMUNITY",
          editionName: "Zenith Community Edition",
          error: "Valid Zenith PRO License Key required to unlock Workflows & AST Auto-Wire.",
        }),
        {
          status: 403,
          headers: { "Content-Type": "application/json" },
        }
      );
    }
  }

  // Forward request to the Java Compiler Container
  const backendBase = env.BACKEND_API_URL || "http://localhost:3000";
  const targetUrl = `${backendBase.replace(/\/$/, "")}${url.pathname}${url.search}`;

  const proxyHeaders = new Headers(request.headers);
  if (isProValid) {
    proxyHeaders.set("X-Zenith-Verified-Pro", "true");
  }

  try {
    const backendResp = await fetch(targetUrl, {
      method: request.method,
      headers: proxyHeaders,
      body: ["GET", "HEAD"].includes(request.method) ? null : await request.arrayBuffer(),
    });

    const respHeaders = new Headers(backendResp.headers);
    respHeaders.set("Access-Control-Allow-Origin", "*");

    return new Response(backendResp.body, {
      status: backendResp.status,
      headers: respHeaders,
    });
  } catch (err) {
    return new Response(
      JSON.stringify({
        success: false,
        error: "Zenith Compiler Backend unreachable: " + err.message,
      }),
      {
        status: 502,
        headers: { "Content-Type": "application/json" },
      }
    );
  }
}

/**
 * Verifies a paid customer's license key against Cloudflare KV or HMAC secret.
 */
async function verifyProLicense(licenseKey, env) {
  // If an admin master key is configured in Cloudflare environment variables
  if (env.ZENITH_MASTER_LICENSE_KEY && licenseKey === env.ZENITH_MASTER_LICENSE_KEY) {
    return true;
  }

  // If a Cloudflare KV namespace 'ZENITH_LICENSES' is bound, verify the key is active
  if (env.ZENITH_LICENSES && licenseKey) {
    const record = await env.ZENITH_LICENSES.get(licenseKey, { type: "json" });
    if (record && record.active === true) {
      return true;
    }
  }

  return false;
}

