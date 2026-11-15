/**
 * 公开演示站的一键体验账号，构建时由 VITE_DEMO_* 注入；未配置则不显示任何体验入口。
 * 演示快照所有账号的密码统一为 123456（见 tools/database/README.md），不是需要保密的凭据。
 */
export type DemoRole = "guest" | "host";

export interface DemoAccount {
  role: DemoRole;
  username: string;
  password: string;
  label: string;
  icon: string;
  type: "success" | "warning";
}

interface DemoEnv {
  VITE_DEMO_GUEST_USERNAME?: string;
  VITE_DEMO_HOST_USERNAME?: string;
  VITE_DEMO_PASSWORD?: string;
}

const DEFAULT_DEMO_PASSWORD = "123456";

export const resolveDemoAccounts = (env: DemoEnv): DemoAccount[] => {
  const password = env.VITE_DEMO_PASSWORD?.trim() || DEFAULT_DEMO_PASSWORD;
  const accounts: DemoAccount[] = [];
  const guest = env.VITE_DEMO_GUEST_USERNAME?.trim();
  const host = env.VITE_DEMO_HOST_USERNAME?.trim();
  if (guest) {
    accounts.push({
      role: "guest",
      username: guest,
      password,
      label: "以房客体验",
      icon: "👤",
      type: "success",
    });
  }
  if (host) {
    accounts.push({
      role: "host",
      username: host,
      password,
      label: "以房东体验",
      icon: "🏠",
      type: "warning",
    });
  }
  return accounts;
};

export const demoAccounts = resolveDemoAccounts(import.meta.env);

export const isDemoMode = demoAccounts.length > 0;

export const findDemoAccount = (role: unknown): DemoAccount | undefined =>
  demoAccounts.find((account) => account.role === role);
