/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_ADMIN_BASE_URL?: string;
  readonly VITE_SHOW_TEST_ACCOUNTS?: string;
  readonly VITE_DEMO_GUEST_USERNAME?: string;
  readonly VITE_DEMO_HOST_USERNAME?: string;
  readonly VITE_DEMO_PASSWORD?: string;
  readonly VITE_AMAP_API_KEY?: string;
  readonly VITE_AMAP_WEB_SERVICE_KEY?: string;
  readonly VITE_AMAP_SECURITY_JS_CODE?: string;
  readonly VITE_AMAP_SERVICE_HOST?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

declare module "*.vue" {
  import type { DefineComponent } from "vue";
  const component: DefineComponent<{}, {}, any>;
  export default component;
}
