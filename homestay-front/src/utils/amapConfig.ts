// 不再提供硬编码 Fallback Key，必须从环境变量配置
// 请在 .env 中设置 VITE_AMAP_API_KEY 和 VITE_AMAP_WEB_SERVICE_KEY

const normalizeEnv = (value?: string) => value?.trim() || "";

const apiKeyFromEnv = normalizeEnv(import.meta.env.VITE_AMAP_API_KEY);
const webServiceKeyFromEnv = normalizeEnv(import.meta.env.VITE_AMAP_WEB_SERVICE_KEY);
const securityJsCodeFromEnv = normalizeEnv(import.meta.env.VITE_AMAP_SECURITY_JS_CODE);
const serviceHostFromEnv = normalizeEnv(import.meta.env.VITE_AMAP_SERVICE_HOST);

let hasWarnedForMissingSecurityConfig = false;

if (!apiKeyFromEnv) {
  console.error("[AMap] VITE_AMAP_API_KEY is not set. Please configure it in your .env file.");
}
if (!webServiceKeyFromEnv) {
  console.error(
    "[AMap] VITE_AMAP_WEB_SERVICE_KEY is not set. Please configure it in your .env file."
  );
}

export const AMAP_CONFIG = {
  apiKey: apiKeyFromEnv,
  webServiceKey: webServiceKeyFromEnv,
  securityJsCode: securityJsCodeFromEnv,
  serviceHost: serviceHostFromEnv,
  version: "2.0" as const,
  plugins: ["AMap.Geocoder", "AMap.AutoComplete", "AMap.PlaceSearch"],
  isUsingFallbackApiKey: !apiKeyFromEnv,
  isUsingFallbackWebServiceKey: !webServiceKeyFromEnv,
};

export const applyAmapSecurityConfig = () => {
  const nextConfig: NonNullable<Window["_AMapSecurityConfig"]> = {};

  if (AMAP_CONFIG.securityJsCode) {
    nextConfig.securityJsCode = AMAP_CONFIG.securityJsCode;
  }

  if (AMAP_CONFIG.serviceHost) {
    nextConfig.serviceHost = AMAP_CONFIG.serviceHost;
  }

  if (!nextConfig.securityJsCode && !nextConfig.serviceHost) {
    if (!hasWarnedForMissingSecurityConfig) {
      console.warn(
        "[AMap] Missing VITE_AMAP_SECURITY_JS_CODE/VITE_AMAP_SERVICE_HOST. New JSAPI keys may fail authentication."
      );
      hasWarnedForMissingSecurityConfig = true;
    }
    return;
  }

  window._AMapSecurityConfig = {
    ...window._AMapSecurityConfig,
    ...nextConfig,
  };
};
